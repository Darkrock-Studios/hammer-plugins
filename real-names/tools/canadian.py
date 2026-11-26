"""Builds resources/names/canadian.txt and resources/names/quebecois.txt: the most common given names by
sex, over all years and for each decade of birth, each with its count; see resources/names/NOTICE.txt.
Run it right after wikidata.py for both cultures, whose surnames and lists for earlier decades it leaves.
The sources write names in capitals without accents; it takes Quebec's spellings from wikidata.py's
Québécois lists, France's lists, and the accents Ontario, British Columbia, and Alberta keep.

Canadian: all years and the decades since the 1990s from Statistics Canada's births since 1991; the
1980s from the four largest provinces' together; the 1920s to the 1970s from Ontario's and British
Columbia's. Québécois: all years and the decades since the 1980s from Retraite Québec's births since 1980.

Downloads into the working directory, once:

    17100147-eng.zip                 https://www150.statcan.gc.ca/n1/tbl/csv/17100147-eng.zip
    grande_listef_csv.csv            Retraite Québec's girls' names, from https://www.donneesquebec.ca
    grande_listeg_csv.csv            Retraite Québec's boys' names
    baby_names_-_female_.csv         Ontario's girls' names, from https://data.ontario.ca
    baby_names_-_male.csv            Ontario's boys' names
    bc-popular-girls-names.csv       British Columbia's girls' names, from https://www2.gov.bc.ca
    bc-popular-boys-names.csv        British Columbia's boys' names
    baby-names-frequency_1980_2020.xlsx  Alberta's names, from https://open.alberta.ca

    python3 canadian.py <working directory> <resources/names/canadian.txt> <resources/names/quebecois.txt>
"""
import csv
import io
import os
import re
import sys
import time
import unicodedata
import urllib.request
import zipfile
from collections import Counter, defaultdict

import lists
import xlsx

GIVEN = 1000
# A given name this many times as common for the other sex is left out.
OTHER_SEX = 20
# Retraite Québec writes "<5" for a count of 1 to 4.
FEWER_THAN_5 = 2

SOURCES = {
    "17100147-eng.zip": "https://www150.statcan.gc.ca/n1/tbl/csv/17100147-eng.zip",
    "grande_listef_csv.csv": "https://www.donneesquebec.ca/recherche/dataset/13db2583-427a-4e5f-b679-8532d3df571f"
                             "/resource/bf77b504-54b9-4db8-be53-b92156175c12/download/grande_listef_csv.csv",
    "grande_listeg_csv.csv": "https://www.donneesquebec.ca/recherche/dataset/93d640ec-d059-4768-b7ed-388604b278aa"
                             "/resource/039539f5-af55-4d8f-9010-ca718e45c2a5/download/grande_listeg_csv.csv",
    "baby_names_-_female_.csv": "https://data.ontario.ca/dataset/4d339626-98f9-49fe-aede-d64f03fa914f"
                                "/resource/acc72e92-3100-4a04-8f5f-4fad9cd77cc5/download/baby_names_-_female_.csv",
    "baby_names_-_male.csv": "https://data.ontario.ca/dataset/eb4c585c-6ada-4de7-8ff1-e876fb1a6b0b"
                             "/resource/5d8b8ece-fa01-43c5-955b-4b642b28c559/download/baby_names_-_male.csv",
    "bc-popular-girls-names.csv": "https://www2.gov.bc.ca/assets/download/3DF4D54C10364F7F979B182E624C10E5",
    "bc-popular-boys-names.csv": "https://www2.gov.bc.ca/assets/gov/birth-adoption-death-marriage-and-divorce"
                                 "/statistics-reports/bc-popular-boys-names.csv",
    "baby-names-frequency_1980_2020.xlsx": "https://open.alberta.ca/dataset/11245675-b047-49fc-8bd1-cc2ce8314a6d"
                                           "/resource/e8aac308-c754-484c-b446-0c57ed0e8d37/download/baby-names-frequency_1980_2020.xlsx",
}

# Entries for a child not yet named, or for its sex.
PLACEHOLDERS = {"BABY", "BEBE", "UNNAMED", "UN-NAMED", "INFANT", "TWIN", "MALE", "FEMALE", "BOY", "GIRL", "FILLE",
                "GARCON", "INCONNU", "INCONNUE", "NONAME", "NO-NAME", "NO", "NONE"}
# Ontario's abbreviations of names, mostly of those born before the 1930s.
ABBREVIATIONS = {"WM": "WILLIAM", "JOS": "JOSEPH", "CHAS": "CHARLES", "THOS": "THOMAS", "ROBT": "ROBERT",
                 "GEO": "GEORGE", "JAS": "JAMES", "JNO": "JOHN"}

# Quebec's spellings of common names the reference lists have without their accents or not at all: France
# writes no accent on a capital, as in "Emile".
QUEBEC_FORMS = [
    "Émile", "Émilie", "Émy", "Édouard", "Élodie", "Éloïse", "Élise", "Élie", "Éliane", "Éléonore", "Élyse",
    "Émeric", "Émilien", "Étienne", "Ève", "Évelyne", "Éveline", "Édith", "Éric", "Éloi", "Élisabeth", "Émie",
    "Marylène", "Marlène", "Josué", "Joséphine", "Déborah", "Laëtitia", "Adélaïde",
]


def fetch(name):
    """The path of the download `name`, fetched into the working directory if it is not there yet."""
    path = os.path.join(work, name)
    if not os.path.exists(path):
        request = urllib.request.Request(SOURCES[name], headers={"User-Agent": "hammer-plugins real-names/tools/canadian.py"})
        for attempt in range(4):
            time.sleep(2)
            try:
                with urllib.request.urlopen(request, timeout=600) as reply:
                    answer = reply.read()
                break
            except OSError:
                if attempt == 3:
                    raise
                time.sleep(30 * (attempt + 1))
        with open(path + ".part", "wb") as f:
            f.write(answer)
        os.replace(path + ".part", path)
    return path


def fold(name):
    """`name` as the sources' key for it: in capitals without accents, its words joined by hyphens, or None
    for what is no name: a placeholder, an initial, or anything but letters, hyphens, and apostrophes. A
    "Jr" after it is left off, and an abbreviation is the name it stands for."""
    name = "".join(c for c in unicodedata.normalize("NFD", name.strip().upper()) if not unicodedata.combining(c))
    name = re.sub(r"[ -]+", "-", re.sub(r"[ -]JR\.?$", "", name))
    name = ABBREVIATIONS.get(name, name)
    if name in PLACEHOLDERS or not re.fullmatch(r"[A-Z]{2,}(?:['-][A-Z]+)*|[A-Z]'[A-Z]{2,}", name):
        return None
    return name


def cased(name):
    """`name`, in any case, with a capital only at its start and after a space, a hyphen, or an apostrophe,
    and in a "Mc" name after the "Mc"."""
    name = re.sub(r"(^|[ '-])(.)", lambda m: m.group(1) + m.group(2).upper(), name.lower())
    return re.sub(r"(^|-)Mc(?=\w\w)(.)", lambda m: m.group(1) + "Mc" + m.group(2).upper(), name)


def unmangled(name):
    """`name`, its UTF-8 read as Windows-1252 where Ontario's file has it so, put right."""
    if "Ã" not in name:
        return name
    try:
        return bytes(ord(c) if ord(c) < 256 else c.encode("cp1252")[0] for c in name).decode("utf-8")
    except (UnicodeError, ValueError):
        return name


def add(counts, sex, year, name, count, written=None):
    """Adds `count` people of `sex` born in `year` named `name` to `counts`, keyed by (sex, year, key), and
    with `written`, to its count of the name's spelling, keyed by key."""
    key = fold(name)
    if key and count:
        counts[(sex, year, key)] += count
        if written is not None:
            written[key][cased(re.sub(r"\s+", " ", name.strip()))] += count


def statcan():
    """Statistics Canada's counts since 1991."""
    counts = Counter()
    with zipfile.ZipFile(fetch("17100147-eng.zip")) as z, z.open("17100147.csv") as f:
        # Each name has a row for its frequency, its rank, and its share; filtered before parsing, as there are millions.
        rows = csv.reader(line for line in io.TextIOWrapper(f, encoding="utf-8-sig") if '"Frequency"' in line)
        for year, _, _, sex, name, _, _, _, _, _, _, _, value, *_ in rows:
            if value:
                add(counts, "F" if sex == "Female" else "M", int(year), name, int(float(value)))
    return counts


def quebec():
    """Retraite Québec's counts since 1980, with a name it shortens to an initial or a few consonants and a
    name, such as "J SEBASTIEN" or "LS PHILIPPE", counted as the most common name it stands for."""
    counts, short = Counter(), Counter()
    for sex, name in (("F", "grande_listef_csv.csv"), ("M", "grande_listeg_csv.csv")):
        with open(fetch(name), encoding="utf-8-sig") as f:
            rows = csv.reader(f)
            years = [int(column[2:]) for column in next(rows)[1:]]
            for row in rows:
                shortened = re.fullmatch(r"([A-Z][B-DF-HJ-NP-TV-XZ]{0,2})\.?[ -]([A-Z]{2,}(?:[ -][A-Z]+)*)", row[0].strip())
                for year, value in zip(years, row[1:]):
                    count = FEWER_THAN_5 if value == "<5" else int(value) if value.isdigit() else 0
                    if shortened and count:
                        short[(sex, year, shortened.group(1), fold(shortened.group(2)))] += count
                    else:
                        add(counts, sex, year, row[0], count)
    totals = Counter()
    for (sex, _, key), count in counts.items():
        totals[(sex, key)] += count
    whole = {}
    for (sex, year, letters, rest), count in short.items():
        if rest and (sex, letters, rest) not in whole:
            pattern = re.compile("[A-Z]*".join(letters) + "[A-Z]*-" + re.escape(rest))
            fits = [(total, key) for (each, key), total in totals.items() if each == sex and pattern.fullmatch(key)
                    and key.split("-")[0] != letters]
            # A few consonants no name stands for are a name of their own, as "Tsz" is.
            whole[(sex, letters, rest)] = max(fits)[1] if fits else fold(f"{letters}-{rest}") if len(letters) > 1 else None
        if rest and whole[(sex, letters, rest)]:
            counts[(sex, year, whole[(sex, letters, rest)])] += count
    return counts


def ontario(written):
    """Ontario's counts since 1917."""
    counts = Counter()
    for sex, name in (("F", "baby_names_-_female_.csv"), ("M", "baby_names_-_male.csv")):
        with open(fetch(name), encoding="utf-8-sig") as f:
            rows = csv.reader(f)
            next(rows)
            for year, each, count in rows:
                add(counts, sex, int(year), unmangled(each), int(count), written)
    return counts


def british_columbia(written):
    """British Columbia's counts since 1924."""
    counts = Counter()
    for sex, name in (("F", "bc-popular-girls-names.csv"), ("M", "bc-popular-boys-names.csv")):
        with open(fetch(name), encoding="utf-8-sig") as f:
            rows = csv.reader(f)
            # The last column is each name's total.
            years = [int(column) for column in next(rows)[1:-1]]
            for row in rows:
                for year, count in zip(years, row[1:-1]):
                    add(counts, sex, year, row[0], int(count or 0), written)
    return counts


def alberta(written):
    """Alberta's counts from 1980 to 2020."""
    counts = Counter()
    for _, rows in xlsx.sheets(fetch("baby-names-frequency_1980_2020.xlsx")):
        for row in rows:
            if len(row) >= 5 and row[4].isdigit() and row[3] in ("Girl", "Boy"):
                add(counts, "F" if row[3] == "Girl" else "M", int(row[4]), row[1], int(row[2]), written)
    return counts


def spellings(names, share):
    """The spelling of each key among `names`, (name, count) pairs: its most common accented spelling where
    at least `share` as many have that as have it unaccented, and the unaccented otherwise."""
    plain, accented = Counter(), {}
    for name, count in names:
        key = fold(name)
        if key and unicodedata.normalize("NFD", name) == name:
            plain[key] += count
        elif key:
            accented.setdefault(key, Counter())[cased(name)] += count
    forms = {key: cased(key) for key in plain}
    for key, counts in accented.items():
        form, count = counts.most_common(1)[0]
        if count >= share * plain[key]:
            forms[key] = form
    return forms


def reference(path, only=None):
    """The given names of the lists in the culture's file at `path`, or of those of them in `only`, as (name, count)."""
    for name, lines in lists.read(path).items():
        if name.startswith(("feminine", "masculine")) and (only is None or name in only):
            for line in lines:
                each, count = line.split("\t")
                yield each, int(count)


def by_decade(counts, years, decade_of=lambda year: year // 10 * 10):
    """`counts`, keyed by (sex, year, key), summed by (sex, decade) for the years in `years`."""
    decades = {}
    for (sex, year, key), count in counts.items():
        if year in years:
            decades.setdefault((sex, decade_of(year)), Counter())[key] += count
    return decades


def write(out, kind, counts, other, form):
    """Writes the list `kind` of `counts`, by key, leaving out a name far more common in `other`, the other sex's."""
    kept = [(form(key), count) for key, count in counts.items() if other[key] <= OTHER_SEX * count]
    lists.write(out, kind, kept, GIVEN)


def write_all(out, decades, form, everyone=None):
    """Writes each decade's lists of `decades`, keyed by (sex, decade), and with `everyone`, keyed by sex,
    the lists of all years."""
    kinds = {"F": "feminine", "M": "masculine"}
    for (sex, decade), counts in sorted(decades.items()):
        write(out, f"{kinds[sex]}-{decade}", counts, decades.get(("M" if sex == "F" else "F", decade), Counter()), form)
    for sex, counts in sorted((everyone or {}).items()):
        write(out, kinds[sex], counts, everyone["M" if sex == "F" else "F"], form)


work, canadian_out, quebecois_out = sys.argv[1], sys.argv[2], sys.argv[3]
os.makedirs(work, exist_ok=True)
here = os.path.dirname(os.path.abspath(__file__))

written = defaultdict(Counter)
qc, on, bc, ab, sc = quebec(), ontario(written), british_columbia(written), alberta(written), statcan()

# Ontario records a child's first given name, which for many of French Canada's born before the 1990s
# was Marie or Joseph, the name used being the next; British Columbia's share of them stands in for Ontario's.
born = {"on": Counter(), "bc": Counter()}
for province, counts in (("on", on), ("bc", bc)):
    for (sex, year, _), count in counts.items():
        born[province][(sex, year)] += count
for year in range(1920, 1990):
    for sex, name in (("F", "MARIE"), ("M", "JOSEPH")):
        share = bc[(sex, year, name)] / born["bc"][(sex, year)] if born["bc"][(sex, year)] else 0
        on[(sex, year, name)] = min(on[(sex, year, name)], round(share * born["on"][(sex, year)]))

# Quebec's spelling of each key: as QUEBEC_FORMS has it, or else as the Wikidata lists of Quebec's people
# have it, or else accented where the other provinces or France write it so. Those lose accents often,
# so a fifth as many with the accent as without is enough.
quebec_forms = {}
for names in (reference(os.path.join(here, "../resources/names/french.txt")),
              ((name, count) for counts in written.values() for name, count in counts.items())):
    quebec_forms.update({key: form for key, form in spellings(names, 0.2).items() if form != cased(key)})
quebec_forms.update(spellings(reference(quebecois_out), 0.5))
quebec_forms.update({fold(form): form for form in QUEBEC_FORMS})


def quebecois(key):
    """The key `key` as Quebec writes it: a compound none of the references has with accents by its parts."""
    form = quebec_forms.get(key, cased(key))
    if "-" in key and form == cased(key):
        return "-".join(quebecois(part) for part in key.split("-"))
    return form


# Canada's spelling of each key: Quebec's, but for a name of English too, one of the 1,000 most common
# in the United States, the one most of its people in the sources have, Quebec's in Quebec and the other
# provinces' as they write it. Their records of French names have often lost the accents.
english = {name for name, _ in reference(os.path.join(here, "../resources/names/american.txt"), ("feminine", "masculine"))}
votes = defaultdict(Counter)
for (_, _, key), count in qc.items():
    votes[key][quebecois(key)] += count
for key, counts in written.items():
    votes[key].update(counts)


def canadian(key):
    """The key `key` as Canadians write it."""
    if quebecois(key) != cased(key) and cased(key) not in english:
        return quebecois(key)
    return (votes.get(key) or Counter({cased(key): 1})).most_common(1)[0][0]


def everyone(counts):
    """`counts`, keyed by (sex, year, key), summed by sex."""
    return {sex: counts for (sex, _), counts in by_decade(counts, range(1900, 2100), lambda year: 0).items()}


write_all(quebecois_out, by_decade(qc, range(1980, 2030)), quebecois, everyone(qc))

# The 1920s to the 1970s from Ontario and British Columbia, the 1980s from them, Quebec, and Alberta,
# and since from Statistics Canada.
decades = by_decade(sc, range(1990, 2030))
for counts, years in ((on, range(1920, 1990)), (bc, range(1920, 1990)), (qc, range(1980, 1990)), (ab, range(1980, 1990))):
    for key, more in by_decade(counts, years).items():
        decades.setdefault(key, Counter()).update(more)
write_all(canadian_out, decades, canadian, everyone(sc))
