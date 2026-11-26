"""Builds resources/names/argentine.txt's lists from RENAPER, Argentina's national register of persons:
the most common whole given names by sex among those born from 1922 to 2015, over all those years and
for each decade of birth, the most common single given names by sex among those born from 2020 to 2024,
and the most common surnames of those alive at the end of 2021, each with its count; see
resources/names/NOTICE.txt. Run it after wikidata.py, whose lists for earlier decades it leaves.
For surnames' accents it also reads spanish.txt's and mexican.txt's beside the culture's file.
Downloads to the working directory, and keeps there each answer of https://query.wikidata.org, so a
second run asks for nothing:

    historico-nombres.zip              https://infra.datos.gob.ar/catalog/otros/dataset/2/distribution/2.1/download/historico-nombres.zip,
                                       each whole given name and how many have it, by year of birth
    nombres_propios_provincia_anio_2012_2024.csv
                                       https://datosabiertos.renaper.gob.ar/, each single given name of
                                       those born from 2012 to 2024 and how many have it, by year
    nombres_propios_frecuentes_anio_sexo_provincia_2012_2024.csv
                                       https://datosabiertos.renaper.gob.ar/, the 20 most common of each
                                       sex, by province and year
    apellidos_cantidad_personas_provincia.csv
                                       https://datosabiertos.renaper.gob.ar/, the surnames more than 10
                                       of a province's residents have, by province
    wikidata-<query>.json              https://query.wikidata.org/sparql, the first given names of
                                       Argentina's people of each sex, and the spellings of their given
                                       names and surnames

RENAPER gives no sex for a given name, so this tells it from the name's words: a name is of the sex of
its first word that tells one. A word tells a sex when one sex has it over OTHER_SEX times as often as
the other: as the first given name of Argentina's people in Wikidata, or in RENAPER's lists of each
sex's 20 most common; else, for a word neither has, among the names with it whose sex the other words
tell, until that tells no more. A name none of whose words tells a sex is left out.

    python3 argentine.py <working directory> <resources/names/argentine.txt>
"""
import csv
import io
import json
import os
import re
import sys
import time
import unicodedata
import urllib.parse
import urllib.request
import zipfile
from collections import Counter

import lists

GIVEN = 1000
SURNAMES = 5000
# A word tells a name's sex when one sex has it over this many times as often as the other.
OTHER_SEX = 20
# The fewest people of Wikidata, and of RENAPER's names, a word must have to tell a sex.
FEWEST_KNOWN = 3
FEWEST_NAMES = 20
# A word takes the accent RENAPER writes it with where at least one in this many of its records has it,
# and Wikidata's spelling where this many of Argentina's people have the word.
ACCENTED = 20
FEWEST_SPELLINGS = 5
USER_AGENT = "hammer-plugins real-names/tools/argentine.py"

HISTORY = "https://infra.datos.gob.ar/catalog/otros/dataset/2/distribution/2.1/download/historico-nombres.zip"
RENAPER = "https://datosabiertos.renaper.gob.ar/"
WORDS = "nombres_propios_provincia_anio_2012_2024.csv"
TOP = "nombres_propios_frecuentes_anio_sexo_provincia_2012_2024.csv"
SURNAME_FILE = "apellidos_cantidad_personas_provincia.csv"
# Births from 1922 to 2015 count for their decade; for the 2020s, those from 2020 to 2024 in the list
# of single given names.
DECADES = range(1920, 2020, 10)
RECENT = 2020
SEXES = ("feminine", "masculine")
WIKIDATA_SEXES = {"feminine": "Q6581072", "masculine": "Q6581097"}

# Words of a given name written in small letters, as in "María de los Ángeles" and "Juan de Dios".
PARTICLES = {"DE", "DEL", "LA", "LAS", "LOS", "Y"}
# The list of single given names marks those with one as "NOMBRE UNICO".
NOT_NAMES = {"NOMBRE UNICO"}
# Words of surnames written in small letters: Spanish ones, as in "de la Fuente" and "del Río"; a lone
# "De" is mostly Italian's, as in "De Luca", and keeps its capital.
SMALL = {"DEL", "Y"}
ARTICLES = {"LA", "LAS", "LOS"}
# Surname words that are a piece of one rather than one, for a surname of nothing else.
PIECES = {"DE", "DEL", "LA", "LAS", "LOS", "Y", "SAN", "DI", "DA", "DO", "DOS", "DAS", "D'", "DELL'", "O'", "L'", "DALL'"}

# Words of names written otherwise than Wikidata's spellings and RENAPER's say. Records often leave
# the accent off a capital, and some give English and Basque names one.
GIVEN_FORMS = [
    "Ámbar", "Úrsula", "Ágata", "Eloísa", "Thomas", "Paul", "Eileen", "Zoe", "Maite", "Naomi", "Cloe", "Cian",
    "Maitte", "Haidée", "Alberica", "Aroon",
]
SURNAME_FORMS = [
    "De Jesús", "De Asís", "de los Santos", "De Rosa", "Cortez", "Gordon", "Barnes", "Urban", "Paris", "Landaburu",
    "Espíndola", "Román", "Barón", "Alcalá", "Tomé", "Mas", "Del Vecchio", "De Gregorio",
]

work, out = sys.argv[1], sys.argv[2]
os.makedirs(work, exist_ok=True)


def download(file, url, form=None):
    """The bytes from `url`, kept in the working directory as `file`; with `form`, POSTed."""
    path = os.path.join(work, file)
    if not os.path.exists(path):
        data = urllib.parse.urlencode(form).encode() if form is not None else None
        request = urllib.request.Request(url, data, {"User-Agent": USER_AGENT})
        for attempt in range(5):
            try:
                with urllib.request.urlopen(request, timeout=300) as reply:
                    body = reply.read()
                break
            except OSError as error:
                print(file, error, file=sys.stderr)
                if attempt == 4:
                    raise
                time.sleep(15 * 2 ** attempt)
        with open(path, "wb") as f:
            f.write(body)
        time.sleep(3)
    with open(path, "rb") as f:
        return f.read()


def table(file):
    """The rows of one of RENAPER's tables, each by its columns' names."""
    return csv.DictReader(io.StringIO(download(file, RENAPER + file).decode("utf-8-sig")))


def wikidata(name, query):
    """The rows of a query's answer."""
    body = download(f"wikidata-{name}.json", "https://query.wikidata.org/sparql", {"query": query, "format": "json"})
    return [{key: value["value"] for key, value in row.items()} for row in json.loads(body)["results"]["bindings"]]


def fold(name):
    """`name` in capitals without marks on its letters but Ñ's, as RENAPER's later lists write it."""
    marked = unicodedata.normalize("NFD", unicodedata.normalize("NFC", name).upper().replace("Ñ", "\0"))
    return "".join(c for c in marked if not unicodedata.combining(c)).replace("\0", "Ñ")


def capitalized(word):
    """`word` with a capital first letter and after an apostrophe, as in "O'Connor", and small letters
    elsewhere."""
    return re.sub(r"(^|')(\w)", lambda m: m.group(1) + m.group(2).upper(), word.lower())


def marked(word):
    """`word` capitalized, with an acute accent for a grave one, if it then has one acute accent and
    no other marks but Ñ's and Ü's; else None. Some records write "Josè" or "MarÍa"."""
    grave, acute = chr(0x300), chr(0x301)
    word = unicodedata.normalize("NFC", unicodedata.normalize("NFD", capitalized(word)).replace(grave, acute))
    accents = sum(c in "áéíóúÁÉÍÓÚ" for c in word)
    return word if accents == 1 and re.fullmatch(r"[A-ZÁÉÍÓÚÑ][a-záéíóúñü]+", word) else None


def spellings(rows, whole=False):
    """The spellings of the words of Wikidata's labels in `rows`, or of the labels of more than one word
    with `whole`, by their folded letters, each with how many people have it. Spanish's letters only,
    which leaves out Italian's and Portuguese's accents."""
    people = {}
    for row in rows:
        label = unicodedata.normalize("NFC", row["label"])
        for each in [label] if whole else label.split():
            if whole and not re.search(r"[ ']", each):
                continue
            # A whole surname may begin with a small letter, as "de la Fuente" does; a word may not.
            if (re.fullmatch(r"[A-ZÁÉÍÓÚÜÑ](?:[ ']?[a-záéíóúüñA-ZÁÉÍÓÚÜÑ])+", each)
                    or whole and re.fullmatch(r"[a-z]+(?: [a-z]+)*(?: [A-ZÁÉÍÓÚÜÑ][a-záéíóúüñ]+)+", each)):
                # A label in Spanish and for all languages ("mul") counts its people twice.
                counts = people.setdefault(fold(each), Counter())
                counts[each] = max(counts[each], int(row["count"]))
    return people


def most(counts):
    """The spelling most people have of `counts`, by spelling; of those as many have, the one with an
    accent or a small letter, which sort last."""
    return max(sorted(counts, reverse=True), key=counts.__getitem__)


# Whole given names by (year, folded name), and how often each spelling of each word is written.
born, written_as = Counter(), {}
with zipfile.ZipFile(io.BytesIO(download("historico-nombres.zip", HISTORY))) as archive:
    with archive.open("historico-nombres.csv") as f:
        for row in csv.DictReader(io.TextIOWrapper(f, encoding="utf-8-sig")):
            words = unicodedata.normalize("NFC", row["nombre"]).split()
            key = " ".join(fold(word) for word in words)
            # Names with digits, punctuation or a lone letter are slips.
            if not words or not re.fullmatch(r"[A-ZÑ]{2,}(?: [A-ZÑ]{2,}| Y)*", key):
                continue
            count = int(row["cantidad"])
            born[(int(row["anio"]), key)] += count
            for word in words:
                written_as.setdefault(fold(word), Counter())[word] += count
names = Counter()
for (year, key), count in born.items():
    names[key] += count

# Each word's count for each sex, first given names in Wikidata and RENAPER's most common, to begin with.
known = {}
for sex in SEXES:
    rows = wikidata(f"given-{sex}", f"""SELECT ?label (COUNT(DISTINCT ?p) AS ?count) WHERE {{
          ?p wdt:P27 wd:Q414; wdt:P21 wd:{WIKIDATA_SEXES[sex]}; p:P735 ?statement. ?statement ps:P735 ?name.
          FILTER NOT EXISTS {{ ?statement pq:P1545 ?place. FILTER(?place != "1") }}
          ?name rdfs:label ?label. FILTER(LANG(?label) IN ("es", "mul"))
        }} GROUP BY ?label""")
    for row in rows:
        known.setdefault(fold(row["label"].split()[0]), Counter())[sex] += int(row["count"])
for row in table(TOP):
    known.setdefault(row["nombre"], Counter())["feminine" if row["sexo"] == "F" else "masculine"] += int(row["cantidad"])


def sex_of(counts, fewest):
    """The sex one of `counts` has over OTHER_SEX times as often as the other, if any."""
    women, men = counts["feminine"], counts["masculine"]
    if women + men >= fewest:
        if women > OTHER_SEX * men:
            return "feminine"
        if men > OTHER_SEX * women:
            return "masculine"
    return None


def name_sex(key, words):
    """The sex of the first of a name's words that tells one."""
    return next((words[word] for word in key.split() if word in words and word not in PARTICLES), None)


seeds = {word: sex for word, counts in known.items() if (sex := sex_of(counts, FEWEST_KNOWN))}
words = dict(seeds)
while True:
    counts = {}
    for key, count in names.items():
        sex = name_sex(key, words)
        if sex:
            for word in set(key.split()):
                counts.setdefault(word, Counter())[sex] += count
    learned = dict(seeds)
    learned.update({word: sex for word, each in counts.items()
                    if word not in seeds and word not in PARTICLES and (sex := sex_of(each, FEWEST_NAMES))})
    if learned == words:
        break
    words = learned

# A word's spelling is GIVEN_FORMS', else Wikidata's where it has an accent and enough people, else
# RENAPER's where enough of its records have an accent, else the word capitalized.
given_wikidata = spellings(wikidata("given-spellings", """SELECT ?label (COUNT(DISTINCT ?p) AS ?count) WHERE {
      ?p wdt:P27 wd:Q414; wdt:P735 ?name. ?name rdfs:label ?label. FILTER(LANG(?label) IN ("es", "mul"))
    } GROUP BY ?label"""))
given_forms = {}
for word, each in written_as.items():
    accented = Counter()
    for spelling, count in each.items():
        if marked(spelling):
            accented[marked(spelling)] += count
    if accented and ACCENTED * accented[most(accented)] >= sum(each.values()):
        given_forms[word] = most(accented)
for word, counts in given_wikidata.items():
    if sum(counts.values()) >= FEWEST_SPELLINGS and most(counts) != capitalized(word):
        given_forms[word] = most(counts)
given_forms.update({fold(form): form for form in GIVEN_FORMS})


def given_name(key):
    """How a folded given name is written: "María de los Ángeles"."""
    return " ".join(word.lower() if i and word in PARTICLES else given_forms.get(word, capitalized(word))
                    for i, word in enumerate(key.split()))


def write_given(file, counts):
    by_sex = {sex: Counter() for sex in SEXES}
    for key, count in counts.items():
        sex = name_sex(key, words)
        if sex:
            by_sex[sex][given_name(key)] += count
    for sex in SEXES:
        lists.write(out, f"{sex}-{file}" if file else sex, by_sex[sex].items(), GIVEN)
    return by_sex


left_out = sum(count for key, count in names.items() if not name_sex(key, words))
print(f"{left_out} of {sum(names.values())} people's given names tell no sex", file=sys.stderr)
write_given(None, names)
for decade in DECADES:
    in_decade = Counter()
    for (year, key), count in born.items():
        if decade <= year < decade + 10:
            in_decade[key] += count
    write_given(decade, in_decade)

recent = Counter()
for row in table(WORDS):
    if int(row["anio"]) >= RECENT and row["nombre"] not in NOT_NAMES:
        recent[" ".join(row["nombre"].split())] += int(row["cantidad"])
write_given(RECENT, recent)

# Surnames: RENAPER's counts of each province's residents, the whole country's together, and how often
# each spelling of each word is written.
surnames, surname_written = Counter(), {}
for row in table(SURNAME_FILE):
    name = re.sub(r"['´`’‘]\s*", "'", " ".join(row["apellido"].split()))
    key = fold(name)
    if (re.fullmatch(r"[A-ZÑ']+(?: [A-ZÑ']+)*", key) and len(key.replace("'", "")) >= 2
            and not set(key.split()) <= PIECES and not re.search(r"(^|[ '])[A-ZÑ](?= |$)", key)):
        surnames[key] += int(row["cantidad"])
        for word in name.split():
            surname_written.setdefault(fold(word), Counter())[capitalized(word)] += int(row["cantidad"])

# A surname word's spelling is SURNAME_FORMS', else RENAPER's where it has a Ü, else Wikidata's where
# enough people have it, else the most common with an accent of Wikidata's, then of Spain's and Mexico's
# lists, else RENAPER's. Those lists write a word without an accent wherever they had no spelling of it,
# and Spain's has Catalan's spellings, so only their acute accents count, and not on a last letter.
surname_rows = wikidata("surname-spellings", """SELECT ?label (COUNT(DISTINCT ?p) AS ?count) WHERE {
      ?p wdt:P27 wd:Q414; wdt:P734 ?name. ?name rdfs:label ?label. FILTER(LANG(?label) IN ("es", "mul"))
    } GROUP BY ?label""")
surname_wikidata = spellings(surname_rows)
hispanic = {}
for culture in ("spanish", "mexican"):
    for line in lists.read(os.path.join(os.path.dirname(out), f"{culture}.txt")).get("surnames", []):
        name, count = line.split("\t")
        for word in name.split():
            hispanic.setdefault(fold(word), Counter())[word] += int(count)


def accented(counts, last=True):
    """Those of `counts`, by spelling, with an acute accent and no other marks but Ñ's and Ü's; without
    `last`, not on the last letter, as Catalan's "Falcó" has it but Italian's "Falco" does not."""
    return Counter({spelling: count for spelling, count in counts.items()
                    if marked(spelling) == spelling and (last or spelling[-1] not in "áéíóú")})


surname_forms = {}
for word, each in surname_written.items():
    known_here = surname_wikidata.get(word, Counter())
    if "ü" in most(each):
        surname_forms[word] = most(each)
    elif sum(known_here.values()) >= FEWEST_SPELLINGS:
        surname_forms[word] = most(known_here)
    else:
        surname_forms[word] = most(accented(known_here) or accented(hispanic.get(word, Counter()), last=False) or each)
surname_forms.update({fold(form): form for form in SURNAME_FORMS})
wholes = {key: most(counts) for key, counts in spellings(surname_rows, whole=True).items()}
wholes.update({fold(form): form for form in SURNAME_FORMS if " " in form or "'" in form})


def surname(key):
    """How a folded surname is written: "de la Fuente", "del Río", "De Luca", "D'Angelo"."""
    if key in wholes:
        return wholes[key]
    words = key.split()
    return " ".join(word.lower() if i < len(words) - 1 and (word in SMALL or word == "DE" and words[i + 1] in ARTICLES)
                    or i and word in ARTICLES and words[i - 1] == "DE"
                    else surname_forms.get(word, capitalized(word)) for i, word in enumerate(words))


written = Counter()
for key, count in surnames.items():
    written[surname(key)] += count
lists.write(out, "surnames", written.items(), SURNAMES)
