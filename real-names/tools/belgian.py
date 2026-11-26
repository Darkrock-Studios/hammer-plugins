"""Builds resources/names/belgian.txt: the most common given names by sex, of everyone living in Belgium
and for each decade of birth since the 1990s, and the most common surnames, each with its count; see
resources/names/NOTICE.txt. Run it after wikidata.py, whose lists for earlier decades it leaves.
Downloads to the working directory Statbel's workbooks, from
https://statbel.fgov.be/sites/default/files/files/documents/bevolking/5.10%20Namen%20en%20voornamen/:

    Firstnames_Girls_1995-.xlsx         5.10.%203%20Voornamen%20meisjes%20en%20jongens/Firstnames_Girls_1995-.xlsx,
                                        the first names given to 5 or more girls born in each year since 1995
    Firstnames_Boys_1995-.xlsx          5.10.%203%20Voornamen%20meisjes%20en%20jongens/Firstnames_Boys_1995-.xlsx,
                                        the same for boys
    First_names_Population_2026.xlsx    5.10.2%20Voornamen%20van%20vrouwen%20en%20mannen/First%20names_Population_2026.xlsx,
                                        the first names 5 or more women or men living on 1 January 2026 have
    Family_names_2026.xlsx              5.10.1%20Familienamen/Family_names_2026.xlsx, the 10,000 most
                                        common surnames of those living on 1 January 2026

    python3 belgian.py <working directory> <resources/names/belgian.txt>
"""
import os
import re
import sys
import time
import urllib.request
from collections import Counter

import lists
from xlsx import sheets

GIVEN = 1000
SURNAMES = 5000
# A given name this many times as common for the other sex is left out.
OTHER_SEX = 20
# The newborns' years start in 1995, so the 1990s are its last five.
NEWBORNS = range(1995, 2026)
BASE = "https://statbel.fgov.be/sites/default/files/files/documents/bevolking/5.10%20Namen%20en%20voornamen/"
FILES = {
    "Firstnames_Girls_1995-.xlsx": "5.10.%203%20Voornamen%20meisjes%20en%20jongens/Firstnames_Girls_1995-.xlsx",
    "Firstnames_Boys_1995-.xlsx": "5.10.%203%20Voornamen%20meisjes%20en%20jongens/Firstnames_Boys_1995-.xlsx",
    "First_names_Population_2026.xlsx": "5.10.2%20Voornamen%20van%20vrouwen%20en%20mannen/First%20names_Population_2026.xlsx",
    "Family_names_2026.xlsx": "5.10.1%20Familienamen/Family_names_2026.xlsx",
}
# Statbel leaves out the letters Latin-1 lacks, such as Polish ł and Turkish ş, and then capitalizes
# what is left, but for a surname. These are such names common enough for the lists, as they are
# written; the rest are too rare for them, or, as Micha for Michał, a name of its own too.
FORMS = {"Lyas": "İlyas", "Nguyn": "Nguyễn", "ahin": "Şahin"}

work, out = sys.argv[1], sys.argv[2]
os.makedirs(work, exist_ok=True)


def download(file):
    """The path of one of Statbel's workbooks, downloaded to the working directory if it is not there."""
    path = os.path.join(work, file)
    if not os.path.exists(path):
        request = urllib.request.Request(BASE + FILES[file], headers={"User-Agent": "hammer-plugins real-names/tools/belgian.py"})
        for attempt in range(4):
            time.sleep(2)
            try:
                with urllib.request.urlopen(request, timeout=300) as reply:
                    body = reply.read()
                if body.startswith(b"PK"):
                    break
            except OSError as error:
                print(file, error, file=sys.stderr)
            if attempt == 3:
                raise SystemExit(f"{BASE + FILES[file]} failed")
            time.sleep(30 * (attempt + 1))
        with open(path, "wb") as f:
            f.write(body)
    return path


def belgium(rows):
    """A sheet's names and counts for all of Belgium, as (name, count): its first three columns, rank,
    name, and count, after a row naming them "Belgium". The regions, and in the workbook of everyone
    living Belgium's age groups too, have columns of their own after them."""
    found = False
    for row in rows:
        if not found:
            found = row[:1] == ["Belgium"]
        elif len(row) > 2 and row[2].isdecimal() and row[1].strip():
            yield row[1].strip(), int(row[2])


def written(name, surname=False):
    """`name` as it is written, or None for one that is a code or has lost letters."""
    name = FORMS.get(name, name)
    # "MD" and "MHD" stand for Mohammed.
    if re.search(r"[^\W\d_a-zß-ÿ]{2}", name):
        return None
    if name[0].isupper() or surname and re.match(r"(d'|de |van |von |le |la |di |da |del |della )", name):
        return name
    return None


def counted(counts, spellings):
    """`counts` with each name in the spelling `spellings` has for it, those that differ only in case
    counted together."""
    together = Counter()
    for name, count in counts.items():
        together[spellings[name.casefold()]] += count
    return together


def spelled(*counts):
    """The most common spelling of each name in any of `counts`, by the name in small letters."""
    total = Counter()
    for each in counts:
        total.update(each)
    spellings = {}
    for name, _ in total.most_common()[::-1]:
        spellings[name.casefold()] = name
    return spellings


def given(counts, kind):
    """Writes the list `kind` of `counts`, a Counter of names by sex, leaving out a name far more common for the other."""
    sex = "feminine" if kind.startswith("feminine") else "masculine"
    other = counts["masculine" if sex == "feminine" else "feminine"]
    lists.write(out, kind, [(name, count) for name, count in counts[sex].items() if other[name] <= OTHER_SEX * count], GIVEN)


everyone = {"feminine": Counter(), "masculine": Counter()}
for sheet, rows in sheets(download("First_names_Population_2026.xlsx")):
    for name, count in belgium(rows):
        if name := written(name):
            everyone[{"Women": "feminine", "Men": "masculine"}[sheet]][name] += count

# Keyed by decade, each a Counter by sex.
decades = {}
for kind, file in (("feminine", "Firstnames_Girls_1995-.xlsx"), ("masculine", "Firstnames_Boys_1995-.xlsx")):
    for sheet, rows in sheets(download(file)):
        # A sheet for each year, and one of all years.
        if sheet.isdecimal() and int(sheet) in NEWBORNS:
            decade = decades.setdefault(int(sheet) // 10 * 10, {"feminine": Counter(), "masculine": Counter()})
            for name, count in belgium(rows):
                if name := written(name):
                    decade[kind][name] += count

spellings = spelled(*everyone.values(), *(counts for decade in decades.values() for counts in decade.values()))
for kind in everyone:
    everyone[kind] = counted(everyone[kind], spellings)
for kind in ("feminine", "masculine"):
    given(everyone, kind)
for decade, counts in sorted(decades.items()):
    for kind in counts:
        counts[kind] = counted(counts[kind], spellings)
    for kind in ("feminine", "masculine"):
        given(counts, f"{kind}-{decade}")

surnames = Counter()
for _, rows in sheets(download("Family_names_2026.xlsx")):
    for name, count in belgium(rows):
        if name := written(name, True):
            surnames[name] += count
lists.write(out, "surnames", counted(surnames, spelled(surnames)).items(), SURNAMES)
