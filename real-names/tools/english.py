"""Builds resources/names/english/'s lists of the most common given names by sex for each decade of
birth since the 1990s, each with its count; see resources/names/NOTICE.txt. Run it after wikidata.py,
whose other lists it leaves. Needs, in the working directory, the Office for National Statistics'
Baby names in England and Wales: from 1996,
https://www.ons.gov.uk/peoplepopulationandcommunity/birthsdeathsandmarriages/livebirths/datasets/babynamesinenglandandwalesfrom1996:

    babynames1996to2025.xlsx

    python3 english.py <working directory> <resources/names/english directory>
"""
import os
import re
import sys
from collections import Counter

from xlsx import sheets

GIVEN = 1000
# A given name this many times as common for the other sex is left out.
OTHER_SEX = 20
KINDS = {"Table_1": "feminine", "Table_2": "masculine"}

work, out = sys.argv[1], sys.argv[2]
os.makedirs(out, exist_ok=True)

# Keyed by (kind, decade). A table's rows are a name and its rank and count in each year, the columns
# headed such as "2025 Count"; a count under 3 is "[x]".
given = {}
for sheet, rows in sheets(os.path.join(work, "babynames1996to2025.xlsx")):
    if sheet not in KINDS:
        continue
    years = {}
    for row in rows:
        if row and row[0] == "Name":
            years = {i: int(cell[:4]) for i, cell in enumerate(row) if re.fullmatch(r"\d{4} Count", cell)}
        elif years and len(row[0]) > 1 and row[0][0].isalpha():
            for i, year in years.items():
                if i < len(row) and row[i].isdigit():
                    given.setdefault((KINDS[sheet], year // 10 * 10), Counter())[row[0]] += int(row[i])
for (kind, decade), counts in sorted(given.items()):
    other = given[("masculine" if kind == "feminine" else "feminine", decade)]
    ranked = sorted(((name, count) for name, count in counts.items() if other[name] <= OTHER_SEX * count), key=lambda item: (-item[1], item[0]))[:GIVEN]
    with open(os.path.join(out, f"{kind}-{decade}.txt"), "w", encoding="utf-8") as f:
        f.writelines(f"{name}\t{count}\n" for name, count in ranked)
    print(f"{kind}-{decade}", len(ranked))
