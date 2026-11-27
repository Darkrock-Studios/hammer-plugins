"""Builds resources/names/brazilian.txt: the most common given names by sex, over all years and for each
decade of birth since the 1930s, and the most common surnames, each with its count; see
resources/names/NOTICE.txt. Run it after wikidata.py, whose lists for earlier decades it leaves and
from whose lists it takes the accents the census leaves off. Asks the names API of IBGE's 2022
census, https://servicodados.ibge.gov.br/api/v3/nomes/2022, for

    localidade/0/ranking/nome?sexo=<f or m>&page=<n>     each sex's given names, 30 a page, all of them
    localidade/0/ranking/nome?decada=<decade>&page=<n>   the given names of those born in a decade
    localidade/0/ranking/sobrenome?page=<n>              the surnames
    nome/<name>                                          a name's count for each sex, for the few missing

and keeps each answer in the working directory, so a second run asks for nothing.

    python3 brazilian.py <working directory> <resources/names/brazilian.txt>
"""
import functools
import json
import os
import re
import sys
import time
import unicodedata
import urllib.request
from collections import Counter

import lists

GIVEN = 1000
SURNAMES = 5000
# A given name this many times as common for the other sex is left out.
OTHER_SEX = 20
# The census gives a decade's names for both sexes together; "1930" is everyone born before 1940.
DECADES = range(1930, 2030, 10)
API = "https://servicodados.ibge.gov.br/api/v3/nomes/2022"

# The census writes names in small letters without accents. These are the spellings of common ones
# that the Wikidata lists lack or have otherwise, as Brazil writes them.
GIVEN_FORMS = [
    "Kauã", "Cauã", "Tauã", "Raí", "Raíssa", "Raísa", "Taíssa", "Taísa", "Thaísa", "Laísa", "Maísa", "Thainá",
    "Géssica", "Gésica", "Jésica", "Cícera", "Ágata", "Ísis", "Maitê", "Nazaré", "Noé", "Tomé", "Gessé",
    "Ataíde", "Alaíde", "Pérola", "Mariângela", "Liduína", "Quézia", "Kézia", "Késsia", "Alcebíades", "Enéias",
    "Élio", "Élcio", "Ênio", "Ézio", "Élvio", "Élsio", "Édio", "Hélcio", "Délcio", "Dárcio", "Dércio", "Tércio",
    "Tárcio", "Tássio", "Tássia", "Kássio", "Kássia", "Acássio", "Adélcio", "Joélcio", "Lírio", "Írio", "Áureo",
    "Alírio", "Duílio", "Artêmio", "Timóteo", "Teófilo", "Eládio", "Evilázio", "Dionízio", "Anízio", "Gildázio",
    "Genézio", "Anézio", "Edézio", "Eliézio", "Eliésio", "Eusébio", "Euzébio", "Aloízio", "Eloísio", "Elízio",
    "Olímpio", "Bráulio", "Gláucio", "Júnio", "Júnia", "Fábia", "Fabíola", "Rúbia", "Lígia", "Mércia", "Danúbia",
    "Sâmia", "Magnólia", "Geórgia", "Noélia", "Rosária", "Eloá", "Heloá", "Elisângela", "Elizângela", "Josafá",
    "Leocádia", "Luísa",
]
SURNAME_FORMS = [
    "Doria", "Soria", "Gracia", "Loes", "Céu", "Graças", "Mercês", "Remédios", "Esperança", "Paraíso", "Belém",
    "Pádua", "Setúbal", "Póvoa", "Távora", "Feijó", "Badaró", "Mororó", "Café", "Salomé", "Barnabé", "Dórea",
    "Jácome", "Sátiro", "Perpétuo", "Ciríaco", "Siríaco", "Brígida", "Crisóstomo", "Melquíades", "Clímaco",
    "Ataídes", "Porciúncula", "Mamédio", "Eliziário", "Nicássio", "Cantuário", "Cantuária", "Cambuí", "Carnaúba",
    "Baraúna", "Braúna", "Mônaco", "América", "Rebouças", "Peçanha", "Façanha", "Lorenço", "Florenço", "Mendoça",
    "Valença", "Florença", "Vilaça", "Villaça", "Picanço", "Colaço", "Melgaço", "Calaça", "Praça", "Camurça",
    "Caraça", "Lança", "Davanço", "Durães", "Manhães", "Carvalhães",
]
# Words the census counts among surnames that are none: answers such as "não sabe", a title, and
# pieces of compound names and particles, as of "Espírito Santo", "Vilas Boas", and "Del Rey".
NOT_SURNAMES = set("""
    nao sabe informado sem irmao dona doa daa dod oda fos espirito santo santa sao sant boas
    del dal dall dalla della dell la los el san van von der den
""".split())
# The surnames of three letters or fewer that are ones: the census's others are most often
# abbreviations or pieces of longer words.
SHORT_SURNAMES = set("sa li wu luz paz vaz gil pio ceu rio mar ito abe ono kim lee lin lau ali dib ott rey job may val sol".split())
# A given name this many times as common as a surname is taken as a surname written in its place.
SURNAME = 100
# A name this many times rarer than one of the same sex a letter away from it, or a surname than such a
# surname, is taken as a slip in writing that one.
MISTAKEN = 100

work, out = sys.argv[1], sys.argv[2]
os.makedirs(work, exist_ok=True)


def ask(name, path):
    """The API's answer for `path`, kept in the working directory under `name`."""
    file = os.path.join(work, f"{name}.json")
    if not os.path.exists(file):
        request = urllib.request.Request(API + path, headers={"User-Agent": "hammer-plugins real-names/tools/brazilian.py"})
        for attempt in range(5):
            try:
                with urllib.request.urlopen(request, timeout=60) as reply:
                    answer = reply.read()
                json.loads(answer)
                break
            except (OSError, ValueError):
                if attempt == 4:
                    raise
                time.sleep(10 * (attempt + 1))
        with open(file, "wb") as f:
            f.write(answer)
        time.sleep(0.3)
    with open(file, encoding="utf-8") as f:
        return json.load(f)


def ranking(kind, field=None, value=None):
    """A ranking's rows, (name, count), most common first, asked for a page at a time as they are read."""
    page = 1
    while True:
        query = f"&{field}={value}" if field else ""
        answer = ask(f"ranking-{kind}-{value}-{page}" if field else f"ranking-{kind}-{page}",
                     f"/localidade/0/ranking/{kind}?page={page}{query}")
        for row in answer["items"]:
            yield row["nome"], row["frequencia"]
        if page >= answer["totalPages"]:
            return
        page += 1


def fold(name):
    """`name` in small letters without accents, as the census writes it."""
    return "".join(c for c in unicodedata.normalize("NFD", name.lower()) if not unicodedata.combining(c))


def brazilian(form):
    """`form` as Brazil writes it, or None for a spelling of another language: an accent before m or n
    is a circumflex, as in "Antônio" for Portugal's "António"."""
    form = re.sub(r"[áéó](?=[mn][aeiou])", lambda m: {"á": "â", "é": "ê", "ó": "ô"}[m.group(0)], form)
    if not re.fullmatch(r"[a-zA-ZáâãéêíóôõúçüÁÂÉÊÍÓÔÚ' -]+", form):
        return None
    # Spanish: "García", "Ramón", "Raúl", "Pérez".
    if re.search(r"(í[ao]|[áéíóú]n|úl|[áéíóú]\w*z)$", form):
        return None
    return form


# Endings with an accent in every name that has them after a consonant, for a name the lists lack:
# "-ao" is "-ão", and a name stressed on the vowel before its last two, as "Patricia" is, has an
# accent there. "-cao" after a vowel or an "n" is "-ção", as in "Conceição".
ENDINGS = """
    ao ão  oes ões  encia ência  encio êncio  ancia ância  ancio âncio  acia ácia  acio ácio  alia ália  alio álio
    ario ário  asia ásia  asio ásio  avia ávia  avio ávio  ania ânia  anio ânio  ecia écia  ecio écio  elia élia
    elio élio  eria éria  erio ério  esia ésia  esio ésio  enia ênia  enio ênio  icia ícia  icio ício  idia ídia
    idio ídio  ilia ília  ilio ílio  ilvia ílvia  ilvio ílvio  inia ínia  inio ínio  isia ísia  isio ísio  ivia ívia
    ivio ívio  oria ória  orio ório  osia ósia  osio ósio  onia ônia  onio ônio  ucia úcia  ucio úcio  ulia úlia
    ulio úlio  irio írio  iria íria  opio ópio  opia ópia  urcio úrcio  urcia úrcia  oncio ôncio  oncia ôncia
    odio ódio  odia ódia  adio ádio  igio ígio  igia ígia  azio ázio  azia ázia  ezio ézio  ezia ézia
    izio ízio  izia ízia  ozio ózio  ozia ózia""".split()
ENDINGS = dict(zip(ENDINGS[::2], ENDINGS[1::2]))


def spellings(names, extra):
    """The accented spelling of each folded name that has one: from `extra`, or the most common accented
    one of `names`, (name, count) pairs, where at least half as many have it as have the unaccented."""
    plain, accented = Counter(), {}
    for name, count in names:
        if unicodedata.normalize("NFD", name) == name:
            plain[fold(name)] += int(count)
        elif brazilian(name):
            accented.setdefault(fold(name), Counter())[brazilian(name)] += int(count)
    forms = {}
    for folded, counts in accented.items():
        form, count = counts.most_common(1)[0]
        if 2 * count >= plain[folded]:
            forms[folded] = form
    forms.update({fold(form): form for form in extra})
    return forms


def written(name, *forms):
    """A name the census writes as `name`, as the first of `forms` has it, or by ENDINGS."""
    for each in forms:
        if name in each:
            return each[name]
    name = re.sub(r"(?<=[aeioun])cao$", "ção", name)
    for ending in sorted(ENDINGS, key=len, reverse=True):
        if name.endswith(ending) and (ending in ("ao", "oes") or name[-len(ending) - 1:-len(ending)] not in "aeiou"):
            name = name[:-len(ending)] + ENDINGS[ending]
            break
    return re.sub(r"(^|[ '-])(.)", lambda m: m.group(1) + m.group(2).upper(), name)


known = lists.read(out)
given_lines = [line.split("\t") for key, lines in known.items() if not key.startswith(("surnames", "format"))
               for line in lines]
surname_lines = [line.split("\t") for key, lines in known.items() if key.startswith("surnames") for line in lines]
given_forms, surname_forms = spellings(given_lines, GIVEN_FORMS), spellings(surname_lines, SURNAME_FORMS)
# The names each sex's lists have, and the surnames, as the census writes them, none of them a slip.
known_given = {sex: {fold(line.split("\t")[0]) for key, lines in known.items() if key.startswith(kind) for line in lines}
               for sex, kind in (("f", "feminine"), ("m", "masculine"))}
known_surnames = {fold(name) for name, _ in surname_lines}

# Each name's count for each sex: the census gives one only where 20 or more have it.
sexes = {sex: dict(ranking("nome", "sexo", sex)) for sex in ("f", "m")}
OTHER = {"f": "m", "m": "f"}
KINDS = {"f": "feminine", "m": "masculine"}


def split(name):
    """The shares of a name's people who are women and who are men."""
    women, men = sexes["f"].get(name, 0), sexes["m"].get(name, 0)
    if not women and not men:
        # Fewer than 20 of each sex have it, but 20 or more in all.
        answer = ask(f"nome-{name}", f"/nome/{name}")
        women, men = answer["mulheres"] or 0, answer["homens"] or 0
    return {"f": women / (women + men), "m": men / (women + men)}


def mistaken(name, counts, known):
    """Whether `name` is likely a slip in writing a name a letter away from it that MISTAKEN times as
    many have, as "Santod" is for "Santos", and not one of the `known`."""
    letters = "abcdefghijklmnopqrstuvwxyz"
    near = {name[:i] + name[i + 1:] for i in range(len(name))}
    near |= {name[:i] + name[i + 1] + name[i] + name[i + 2:] for i in range(len(name) - 1)}
    near |= {name[:i] + c + name[i + j:] for i in range(len(name) + 1) for c in letters for j in (0, 1)}
    return name in counts and name not in known and any(
        counts.get(other, 0) >= MISTAKEN * counts[name] for other in near - {name})


# The census counts every word after the first given name as a surname: a word more have as their
# first given name, as "Maria" and "José", is most often a second one. The ranking is read on to
# surnames fewer than SURNAME times 20 have, the fewest a given name can have, for odd() below.
surnames, read = [], {}
for name, count in ranking("sobrenome"):
    read[name] = count
    if (name in NOT_SURNAMES or len(name) <= 3 and name not in SHORT_SURNAMES or mistaken(name, read, known_surnames)
            or sexes["f"].get(name, 0) + sexes["m"].get(name, 0) >= count):
        continue
    surnames.append((written(name, surname_forms, given_forms), count))
    if len(surnames) >= SURNAMES and count < SURNAME * 20:
        break
lists.write(out, "surnames", surnames, SURNAMES)


@functools.cache
def odd(name, sex):
    """Whether a given name of the sex is likely a slip, or a surname written in its place: one SURNAME
    times as many have as a surname."""
    everyone = sexes["f"].get(name, 0) + sexes["m"].get(name, 0)
    return mistaken(name, sexes[sex], known_given[sex]) or read.get(name, 0) > SURNAME * everyone


def keep(counts, other, sex):
    """`counts`, a sex's, without the names over OTHER_SEX times as common for the other sex, by its
    `other`, or odd."""
    return {name: count for name, count in counts.items()
            if other.get(name, 0) <= OTHER_SEX * count and not odd(name, sex)}


for sex in sexes:
    counts = keep(sexes[sex], sexes[OTHER[sex]], sex)
    lists.write(out, KINDS[sex], [(written(name, given_forms), count) for name, count in counts.items()], GIVEN)

for decade in DECADES:
    # A decade's count of a name is split between the sexes as its count over all years is. The
    # ranking is read a page at a time until no name further down could be among either sex's most common.
    counts = {"f": {}, "m": {}}
    for row, (name, count) in enumerate(ranking("nome", "decada", decade), 1):
        shares = split(name)
        for sex in counts:
            if round(count * shares[sex]):
                counts[sex][name] = round(count * shares[sex])
        if row % 30 == 0:
            most = [sorted(keep(counts[sex], counts[OTHER[sex]], sex).values(), reverse=True)[GIVEN - 1:GIVEN] for sex in counts]
            if all(each and each[0] > count for each in most):
                break
    for sex in counts:
        kept = keep(counts[sex], counts[OTHER[sex]], sex)
        lists.write(out, f"{KINDS[sex]}-{decade}", [(written(name, given_forms), count) for name, count in kept.items()], GIVEN)
