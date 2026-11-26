"""Builds the lists by decade of birth since the 1980s in resources/names/austrian.txt: the most common first
given names by sex among babies born in Austria, each with its count; see resources/names/NOTICE.txt. Run it
after wikidata.py, whose other lists it leaves. Downloads, into the working directory, so a second run asks
for nothing:

    OGDEXT_VORNAMEN_1.csv    https://data.statistik.gv.at/data/OGDEXT_VORNAMEN_1.csv, Statistik Austria's
                             first names of babies by year, sex, and district, 1984 on, accents taken off
    Vornamen_original.ods    https://www.statistik.at/fileadmin/pages/426/Vornamen_1984_bis_2025_original_Schreibweise.ods,
                             every first name given since 1984 as it was written, for the accents

    python3 austrian.py <working directory> <resources/names/austrian.txt>
"""
import csv
import os
import re
import sys
import time
import unicodedata
import urllib.request
import zipfile
import xml.etree.ElementTree as ET
from collections import Counter

import lists

GIVEN = 1000
# A given name this many times as common for the other sex is left out.
OTHER_SEX = 20
AGENT = "hammer-plugins real-names/tools/austrian.py"
NAMES = "https://data.statistik.gv.at/data/OGDEXT_VORNAMEN_1.csv"
SPELLINGS = "https://www.statistik.at/fileadmin/pages/426/Vornamen_1984_bis_2025_original_Schreibweise.ods"
TABLE = "{urn:oasis:names:tc:opendocument:xmlns:table:1.0}"
# Letters Statistik Austria writes as another without its mark, beside those it takes accents off.
PLAIN = str.maketrans("ıłŁđĐÐðøØƫ", "ilLdDDdoOt")

work, out = sys.argv[1], sys.argv[2]
os.makedirs(work, exist_ok=True)


def download(name, url):
    """The path of the file `name` in the working directory, downloaded from `url` when it is not there."""
    path = os.path.join(work, name)
    if not os.path.exists(path):
        request = urllib.request.Request(url, headers={"User-Agent": AGENT})
        for attempt in range(4):
            try:
                with urllib.request.urlopen(request, timeout=300) as reply:
                    data = reply.read()
                break
            except OSError:
                if attempt == 3:
                    raise
                time.sleep(10 * (attempt + 1))
        with open(path, "wb") as f:
            f.write(data)
        time.sleep(1)
    return path


def plain(name):
    """`name` as Statistik Austria's open data writes it: with its umlauts, without its other marks."""
    letters = []
    for c in unicodedata.normalize("NFD", name.translate(PLAIN)):
        if not unicodedata.combining(c) or c == "̈" and letters and letters[-1] in "aouAOU":
            letters.append(c)
    return unicodedata.normalize("NFC", "".join(letters))


def originals():
    """(sex, name, count) for each first name in the spreadsheet's lists of all boys' and all girls' names,
    as it was written, and how many were given it since 1984."""
    sex = None
    with zipfile.ZipFile(download("Vornamen_original.ods", SPELLINGS)) as z:
        for event, element in ET.iterparse(z.open("content.xml"), events=("start", "end")):
            if event == "start":
                if element.tag == TABLE + "table":
                    sex = None
                continue
            if element.tag != TABLE + "table-row":
                continue
            cells = ["".join(cell.itertext()).strip() for cell in element]
            element.clear()
            if cells and cells[0].startswith("Liste aller"):
                sex = "1" if "Bubennamen" in cells[0] else "2" if "Mädchennamen" in cells[0] else None
            elif sex and len(cells) > 1 and re.fullmatch(r"[\d\s]+", cells[1]):
                yield sex, unicodedata.normalize("NFC", cells[0]), int(re.sub(r"\s", "", cells[1]))


# Each name of the open data, by sex, has the spelling most of those given it had.
spellings = {}
for sex, name, count in originals():
    spellings.setdefault((sex, plain(name)), Counter())[name] += count

# Keyed by (sex, decade). The district "GEH" gathers each year's names given fewer than 4 times.
given = {}
with open(download("OGDEXT_VORNAMEN_1.csv", NAMES), encoding="utf-8-sig", newline="") as f:
    for row in csv.DictReader(f, delimiter=";"):
        sex, name = row["C-GESCHLECHT-0"], row["F-VORNAME_NORMALISIERT"]
        name = spellings[(sex, name)].most_common(1)[0][0] if (sex, name) in spellings else name
        given.setdefault((sex, int(row["C-JAHR-0"]) // 10 * 10), Counter())[name] += int(row["F-ANZAHL_LGEB"])
for (sex, decade), counts in sorted(given.items()):
    kind = {"1": "masculine", "2": "feminine"}[sex]
    other = given.get(("2" if sex == "1" else "1", decade), Counter())
    counts = {name: count for name, count in counts.items() if other[name] <= OTHER_SEX * count}
    lists.write(out, f"{kind}-{decade}", counts.items(), GIVEN)
