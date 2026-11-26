"""Builds resources/names/israeli.txt and resources/names/levantine.txt from Israel's Central Bureau of
Statistics (CBS): the most common given names by sex, over all years and for each decade of birth since
the 1940s, and the most common surnames, each with its count; see resources/names/NOTICE.txt. Israeli
is the Jewish sector, Levantine the Muslim, Christian-Arab and Druze sectors pooled. Run it after
wikidata.py for Israeli, whose lists for earlier decades it leaves.

CBS gives the names in Hebrew letters; israeli-names.tsv, beside this script, has the English spelling
of each (sector, kind, Hebrew name, spelling), "-" for one left out. A name without a spelling is
left out; the script says how many people that leaves out of each list.

Downloads into the working directory, and keeps, these files from https://www.cbs.gov.il:

    11_25_391t1.xlsx     release 391/2025: current first given names in the population register,
                         April 2025, of those born in each year from 1949 to 2024, by sector and sex
    11_19_037t3.xls      release 037/2019, Last Names in Israel 2017: the Jewish sector's surnames
    11_19_037t4.xls      ... the Muslims'
    11_19_037t5.xls      ... the Christians'
    11_19_037t6.xls      ... the Druze

    python3 israeli.py <working directory> <resources/names/israeli.txt> <resources/names/levantine.txt>
"""
import os
import sys
import time
import urllib.request
from collections import Counter

import lists
import xls
import xlsx

GIVEN = 1000
SURNAMES = 5000
# A given name this many times as common for the other sex is left out.
OTHER_SEX = 20

GIVEN_URL = "https://www.cbs.gov.il/he/mediarelease/DocLib/2025/391/11_25_391t1.xlsx"
SURNAMES_URL = "https://www.cbs.gov.il/he/mediarelease/doclib/2019/037/11_19_037{}.xls"
# Each given-name sheet's sector and sex.
SHEETS = {
    "בנות יהודיות": ("jewish", "feminine"), "בנים יהודים": ("jewish", "masculine"),
    "בנות מוסלמיות": ("muslim", "feminine"), "בנים מוסלמים": ("muslim", "masculine"),
    "בנות נוצריות-ערביות": ("christian", "feminine"), "בנים נוצרים-ערבים": ("christian", "masculine"),
    "בנות דרוזיות": ("druze", "feminine"), "בנים דרוזים": ("druze", "masculine"),
}
SURNAME_TABLES = {"t3": "jewish", "t4": "muslim", "t5": "christian", "t6": "druze"}

work, israeli, levantine = sys.argv[1], sys.argv[2], sys.argv[3]
os.makedirs(work, exist_ok=True)


def fetch(url):
    """The path of the file at `url` in the working directory, downloaded if it is not there yet."""
    path = os.path.join(work, url.rsplit("/", 1)[1])
    if not os.path.exists(path):
        # cbs.gov.il resets the connection unless the request looks like a browser's.
        request = urllib.request.Request(url, headers={
            "User-Agent": "Mozilla/5.0 (X11; Linux x86_64; rv:128.0) Gecko/20100101 Firefox/128.0 "
                          "(hammer-plugins real-names/tools/israeli.py)"})
        for attempt in range(4):
            time.sleep(2)
            try:
                with urllib.request.urlopen(request, timeout=300) as reply:
                    data = reply.read()
                # A missing file comes back as a small page rather than an error.
                if len(data) < 10000:
                    raise ValueError(f"{url} gave no file")
                break
            except (OSError, ValueError):
                if attempt == 3:
                    raise
                time.sleep(30 * (attempt + 1))
        with open(path, "wb") as f:
            f.write(data)
    return path


spellings = {}
with open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "israeli-names.tsv"), encoding="utf-8") as f:
    for line in f:
        if line.strip() and not line.startswith("#"):
            sector, kind, hebrew, latin = line.rstrip("\n").split("\t")
            spellings[(sector, kind, hebrew)] = None if latin == "-" else latin


def spelling(sector, kind, hebrew):
    """`hebrew`'s English spelling in `sector`, or None for a name left out. The Arab sectors share
    their spellings unless one has its own."""
    found = spellings.get((sector, kind, hebrew))
    if found is None and (sector, kind, hebrew) not in spellings and sector != "jewish":
        found = spellings.get(("arab", kind, hebrew))
    return found


def number(cell):
    """A cell's count; ".." is one of 1 to 4, too few to give."""
    cell = cell.replace(",", "")
    return int(float(cell)) if cell.replace(".", "", 1).isdigit() else 0


def culture(sector):
    return israeli if sector == "jewish" else levantine


# Keyed by (file, sex, decade), with decade None for all years; and the people left out of each.
given, left_out = {}, Counter()
for name, rows in xlsx.sheets(fetch(GIVEN_URL)):
    sector, sex = SHEETS[name]
    header = next(row for row in rows if row and row[0] == "prati1")
    years = [int(year) for year in header[2:]]
    for row in rows[rows.index(header) + 1:]:
        if not row or not row[0]:
            continue
        latin = spelling(sector, sex, row[0].strip())
        # The total counts the years of 1 to 4 too.
        counts = [((culture(sector), sex, None), number(row[1]))]
        counts += [((culture(sector), sex, year // 10 * 10), number(cell)) for year, cell in zip(years, row[2:])]
        for key, count in counts:
            if not count:
                continue
            if latin:
                given.setdefault(key, Counter())[latin] += count
            else:
                left_out[key] += count
for (file, sex, decade), counts in sorted(given.items(), key=lambda item: (item[0][0], item[0][1], item[0][2] or 0)):
    other = given.get((file, "masculine" if sex == "feminine" else "feminine", decade), Counter())
    counts = {name: count for name, count in counts.items() if other[name] <= OTHER_SEX * count}
    kind = f"{sex}-{decade}" if decade else sex
    print(os.path.basename(file), kind, "left out", left_out[(file, sex, decade)], "of",
          left_out[(file, sex, decade)] + sum(given[(file, sex, decade)].values()), "people")
    lists.write(file, kind, counts.items(), GIVEN)

surnames = {israeli: Counter(), levantine: Counter()}
for table, sector in SURNAME_TABLES.items():
    for _, rows in xls.sheets(fetch(SURNAMES_URL.format(table))):
        # The Muslims' table lists some surnames twice, in a second block whose counts add to the first's.
        for row in rows[2:]:
            if row and row[0].strip():
                latin = spelling(sector, "surname", row[0].strip())
                if latin:
                    surnames[culture(sector)][latin] += number(row[1])
                else:
                    left_out[(culture(sector), "surname")] += number(row[1])
for file, counts in surnames.items():
    print(os.path.basename(file), "surnames left out", left_out[(file, "surname")], "of",
          left_out[(file, "surname")] + sum(counts.values()), "people")
    lists.write(file, "surnames", counts.items(), SURNAMES)
