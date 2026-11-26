"""Builds resources/names/spanish.txt: the most common given names by sex, over all years and for each
decade of birth since the 1920s, and the most common surnames, each with its count; see
resources/names/NOTICE.txt. From INE's annual census of 1 January 2025. Run it after wikidata.py, whose
lists for earlier decades it leaves. Downloads to the working directory, and keeps there each answer
of INE's tools and of https://query.wikidata.org, so a second run asks for nothing:

    nombres_por_edad_media.xlsx   https://www.ine.es/daco/daco42/nombyapel/nombres_por_edad_media.xlsx,
                                  the given names of each sex that 20 people or more have
    nombres-<sex>-<decade>.html   https://www.ine.es/tnombres/formGeneralresult.do?vista=3, the 5,000
                                  most common given names of a sex born in a decade
    apellidos.html                https://www.ine.es/apellidos/formGeneralresult.do?vista=1, the 5,000
                                  most common surnames
    wikidata-<kind>-<hash>.json   https://query.wikidata.org/sparql, how many of Spain's citizens
                                  have each spelling with accents of INE's given names and surnames

    python3 spanish.py <working directory> <resources/names/spanish.txt>
"""
import hashlib
import html
import json
import os
import re
import sys
import time
import unicodedata
import urllib.parse
import urllib.request
from collections import Counter

import lists
from xlsx import sheets

GIVEN = 1000
SURNAMES = 5000
# A given name this many times as common for the other sex is left out.
OTHER_SEX = 20
# INE's codes for the sexes, and the decades of birth its tool has; 1920 is everyone born before 1930,
# who are the 1920s' in all but a few.
SEXES = {"1": "masculine", "6": "feminine"}
DECADES = range(1920, 2030, 10)
USER_AGENT = "hammer-plugins real-names/tools/spanish.py"

# INE writes names in capitals without accents, and compound given names without their particles.
# These are the compound names usually written with one; others, such as María Dolores or Juan Cruz,
# are usually written without. A name with a word that takes a particle after María, such as "ANA
# CARMEN", could be either, and is left out.
PARTICLES = [
    "María del Carmen", "María de los Ángeles", "María del Pilar", "María del Mar", "María del Rosario",
    "María del Rocío", "María del Valle", "María del Prado", "María del Camino", "María del Pino",
    "María de la O", "María de los Llanos", "María del Henar", "María de la Salud", "María de las Angustias",
    "María de los Desamparados", "María de la Sierra", "María de la Cabeza", "María del Castillo",
    "María de la Encina", "María del Águila", "María de Tíscar", "María de la Montaña",
    "María de las Ermitas", "María de las Hermitas", "Ana María del Carmen", "Ana María del Pilar",
    "Juan de Dios", "Juan de Mata", "Francisco de Asís", "Francisca de Asís", "Francisco de Paula",
    "Francisca de Paula", "Francisco de Borja", "Teresa de Jesús", "Teresita de Jesús", "Mariana de Jesús",
    "María Teresa de Jesús", "Dulce Nombre de María",
]
# Compound names whose particle is not clear, left out too.
UNSURE = {"MARIA SEÑOR", "MARIA SAMPEDRO"}
# Words of given names and of surnames written otherwise than Wikidata's spellings say. Wikidata has
# more Catalans of note than Spain has, so its spelling of a name both write is often the Catalan one;
# it has no spelling of some names, and gives LLUCIA, a woman's name, a man's spelling.
GIVEN_FORMS = [
    "Nuria", "Eulalia", "Llúcia", "Dácil", "Luján", "Lidón", "Leónides", "Náyade", "Manasés", "Tanausú",
    "Abián", "Ruymán", "Brígido", "Mérida", "Felicísima", "Lázara", "Onésima", "Teódula", "Espíritu",
    "Mónico", "Riánsares", "Águila", "Tíscar",
]
SURNAME_FORMS = [
    "Galián", "Menchón", "Calzón", "Ausín", "Sendín", "Mompeán", "Cotán", "Belchí", "Chuliá", "Barrajón",
    "Atiénzar", "Sanchidrián", "Montávez", "Peribáñez", "Santibáñez", "Mengíbar", "Buisán", "Tévar",
    "Tíscar", "Cañíbano", "Águila",
]
# Words of a surname written in small letters, and those that are when after "de".
SMALL = {"DE", "DEL", "DA", "DAS", "DO", "DOS", "Y"}
AFTER_DE = {"LA", "LAS", "LOS"}
# The marks a letter may have in the names of Spain's people, Catalan and Portuguese ones too.
MARKS = {"A": "ÁÀÃÂ", "E": "ÉÈÊ", "I": "ÍÏ", "O": "ÓÒÕÔ", "U": "ÚÜ"}
# Wikidata's classes of given names and of surnames, and the property linking a person to theirs.
KINDS = {
    "given": ("wd:Q202444 wd:Q12308941 wd:Q11879590 wd:Q3409032", "P735"),
    "surname": ("wd:Q101352", "P734"),
}
LANGUAGES = ("mul", "es", "en", "ca", "gl", "eu")

work, out = sys.argv[1], sys.argv[2]
os.makedirs(work, exist_ok=True)


def download(file, url, form=None, check=None):
    """The answer from `url`, kept in the working directory as `file`; with `form`, POSTed. An answer
    without `check` in it is an error, and is asked for again."""
    path = os.path.join(work, file)
    if not os.path.exists(path):
        data = urllib.parse.urlencode(form).encode() if form is not None else None
        request = urllib.request.Request(url, data, {"User-Agent": USER_AGENT})
        for attempt in range(5):
            try:
                with urllib.request.urlopen(request, timeout=300) as reply:
                    body = reply.read()
                if check is None or check in body:
                    break
            except OSError as error:
                print(file, error, file=sys.stderr)
            time.sleep(10 * 2 ** attempt)
        else:
            raise SystemExit(f"{url} failed")
        with open(path, "wb") as f:
            f.write(body)
        time.sleep(3)
    return path


def table(file, url, form, *columns):
    """Each row of one of INE's tools' tables, as its name and its counts in `columns`."""
    with open(download(file, url, form, b"tableCellGr"), encoding="utf-8") as f:
        page = f.read()
    for row in re.findall(r"<tr>(.*?)</tr>", page, re.S):
        name = re.search(r'headers="nombre [^"]*"[^>]*>\s*(.*?)\s*</td>', row, re.S)
        if name:
            counts = [re.search(rf'headers="{column} [^"]*">(?:&nbsp;)?\s*(?:<!--.*?-->)?\s*([\d.]*)\s*</td>', row, re.S)
                      for column in columns]
            yield html.unescape(name.group(1)), [int(c.group(1).replace(".", "") or 0) if c else 0 for c in counts]


def fold(name):
    """`name` as INE writes it: in capitals, without marks on its letters but for Ñ and Ç."""
    marked = unicodedata.normalize("NFD", name.upper().replace("Ñ", "\0").replace("Ç", "\1"))
    return "".join(c for c in marked if not unicodedata.combining(c)).replace("\0", "Ñ").replace("\1", "Ç")


def spellings(word):
    """The ways `word`, in INE's capitals, may be written: as it is or with a mark on one letter."""
    yield word.capitalize()
    for i, letter in enumerate(word):
        for marked in MARKS.get(letter, ""):
            yield (word[:i] + marked + word[i + 1:]).capitalize()


def written(kind, words):
    """How each of `words`, in INE's capitals, is written as a `kind` of name: the spelling of it that
    Wikidata has a name of with the most of Spain's citizens having it, else the word capitalized."""
    classes, property = KINDS[kind]
    counts = {}
    words = sorted(words)
    for start in range(0, len(words), 60):
        # Many names have their label only in "mul", for all languages, or in English.
        labels = " ".join(f"{json.dumps(spelling)}@{language}" for word in words[start:start + 60]
                          for spelling in spellings(word) for language in LANGUAGES)
        query = f"""SELECT ?spelling (COUNT(DISTINCT ?p) AS ?count) WHERE {{
              VALUES ?label {{ {labels} }} VALUES ?class {{ {classes} }}
              ?item rdfs:label ?label; wdt:P31 ?class.
              OPTIONAL {{ ?p wdt:{property} ?item; wdt:P27 wd:Q29. }}
              BIND(STR(?label) AS ?spelling)
            }} GROUP BY ?spelling"""
        file = f"wikidata-{kind}-{hashlib.sha1(query.encode()).hexdigest()[:12]}.json"
        with open(download(file, "https://query.wikidata.org/sparql", {"query": query, "format": "json"}, b'"bindings"'), encoding="utf-8") as f:
            for row in json.load(f)["results"]["bindings"]:
                counts[row["spelling"]["value"]] = int(row["count"]["value"])
    # Of spellings Wikidata has but no one in Spain has, the one without marks if it is one; for a word
    # Wikidata has no spelling of, "-ción" is the only way to write "-CION".
    return {word: max(spellings(word), key=lambda spelling: (counts.get(spelling, -1), spelling == word.capitalize()))
            if any(spelling in counts for spelling in spellings(word)) else re.sub("cion$", "ción", word.capitalize())
            for word in words}


given = {}
for sex, kind in SEXES.items():
    for decade in DECADES:
        rows = table(f"nombres-{sex}-{decade}.html", "https://www.ine.es/tnombres/formGeneralresult.do?vista=3",
                     {"cmb3": "00", "cmb4": decade, "cmb6": sex, "cmb5": ""}, "total")
        given[(kind, decade)] = Counter({name: count for name, (count,) in rows})
path = download("nombres_por_edad_media.xlsx", "https://www.ine.es/daco/daco42/nombyapel/nombres_por_edad_media.xlsx")
for sheet, rows in sheets(path):
    kind = {"Hombres": "masculine", "Mujeres": "feminine"}[sheet]
    given[(kind, None)] = Counter({row[1]: int(row[2]) for row in rows if len(row) >= 3 and row[0].isdigit()})

particles = {" ".join(word for word in fold(form).split() if word not in SMALL | AFTER_DE): form for form in PARTICLES}
bound = {name.split()[-1] for name, form in particles.items() if form.startswith("María de")}
# Each list's names as INE writes them, keyed by the list.
chosen = {}
for (kind, decade), counts in sorted(given.items(), key=lambda item: (item[0][0], item[0][1] or 0)):
    other = given[("masculine" if kind == "feminine" else "feminine", decade)]
    counts = Counter({name: count for name, count in counts.items() if other[name] <= OTHER_SEX * count
                      and (name in particles or not bound & set(name.split()[1:])) and name not in UNSURE})
    chosen[f"{kind}-{decade}" if decade else kind] = counts.most_common(GIVEN)

# A surname is counted as many times as people have it, first or second, as the format draws both
# from the same list.
surnames = Counter()
for name, (first, second) in table("apellidos.html", "https://www.ine.es/apellidos/formGeneralresult.do?vista=1",
                                   {"cmb3": "00", "cmb6": ""}, "total1", "total2"):
    surnames[name] = first + second
surnames = surnames.most_common(SURNAMES)

forms = {"given": written("given", {word for names in chosen.values() for name, _ in names for word in name.split()}),
         "surname": written("surname", {word for name, _ in surnames for word in name.split()} - SMALL - AFTER_DE)}
forms["given"].update({fold(form): form for form in GIVEN_FORMS})
forms["surname"].update({fold(form): form for form in SURNAME_FORMS})

for file, names in chosen.items():
    lists.write(out, file, [(particles.get(name) or " ".join(forms["given"][word] for word in name.split()), count)
                            for name, count in names], GIVEN)


def surname(name):
    """How a surname INE has is written: "de la Fuente", "La Rosa"."""
    words = name.split()
    return " ".join(word.lower() if word in SMALL or word in AFTER_DE and i and words[i - 1] == "DE"
                    else forms["surname"].get(word, word.capitalize()) for i, word in enumerate(words))


lists.write(out, "surnames", [(surname(name), count) for name, count in surnames], SURNAMES)
