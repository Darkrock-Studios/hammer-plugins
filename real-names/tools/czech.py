"""Builds feminine-2020 and masculine-2020 in resources/names/czech.txt: the 100 or so most common
given names of each sex among births in Czechia in 2025, each with its count; see
resources/names/NOTICE.txt. They are only the most common names. Run it after wikidata.py, whose
other lists it leaves. Downloads, into the working directory unless it is there, the Czech Statistical
Office's workbook of the year's top 100 names, with the counts its workbooks for 2022 to 2024 leave out:

    top-2025.xlsx    https://csu.gov.cz/docs/107508/0a6170f4-bc53-7d35-afe2-3d5fcd0acb47/data_detska_jmena_top_100_cesko_2025.xlsx

    python3 czech.py <working directory> <resources/names/czech.txt>
"""
import os
import sys
import time
import urllib.request
from collections import Counter

import lists
from xlsx import sheets

GIVEN = 1000
# A given name this many times as common for the other sex is left out.
OTHER_SEX = 20
YEARS = {2025: "https://csu.gov.cz/docs/107508/0a6170f4-bc53-7d35-afe2-3d5fcd0acb47/data_detska_jmena_top_100_cesko_2025.xlsx"}
SHEETS = {"Dívky": "feminine", "Chlapci": "masculine"}

work, out = sys.argv[1], sys.argv[2]
os.makedirs(work, exist_ok=True)


def fetch(year):
    """The path of the year's workbook in the working directory, downloaded unless it is there."""
    path = os.path.join(work, f"top-{year}.xlsx")
    if not os.path.exists(path):
        request = urllib.request.Request(YEARS[year], headers={"User-Agent": "hammer-plugins real-names/tools/czech.py"})
        for attempt in range(4):
            try:
                with urllib.request.urlopen(request, timeout=300) as reply:
                    data = reply.read()
                break
            except OSError:
                if attempt == 3:
                    raise
                time.sleep(30 * (attempt + 1))
        with open(path, "wb") as f:
            f.write(data)
        time.sleep(2)
    return path


# Keyed by (kind, decade).
born = {}
for year in YEARS:
    for sheet, rows in sheets(fetch(year)):
        # The rows are a name in capitals, its count, and its rank; NEZJIŠTĚNO counts those with no name known.
        for name, count, *_ in (row for row in rows if len(row) >= 2 and row[1].isdigit() and row[0] != "NEZJIŠTĚNO"):
            born.setdefault((SHEETS[sheet], year // 10 * 10), Counter())[name.title()] += int(count)
for (kind, decade), counts in sorted(born.items()):
    other = born[("masculine" if kind == "feminine" else "feminine", decade)]
    counts = {name: count for name, count in counts.items() if other[name] <= OTHER_SEX * count}
    lists.write(out, f"{kind}-{decade}", counts.items(), GIVEN)
