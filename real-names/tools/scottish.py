"""Builds resources/names/scottish/: the most common given names by sex, since 1974 and for each decade
of birth since, and the most common surnames, each with its count; see resources/names/NOTICE.txt.
Run it after wikidata.py, whose lists for earlier decades it leaves. Needs, in the working directory,
from National Records of Scotland's Babies' First Names and Most Common Surnames publications,
https://www.nrscotland.gov.uk/publications/:

    full-list-1974-2025.csv           from all-names-given-to-babies-between-1974-to-2025.zip
    most-common-surnames-2025.xlsx

    python3 scottish.py <working directory> <resources/names/scottish directory>
"""
import csv
import os
import re
import sys
from collections import Counter

from xlsx import sheets

GIVEN = 1000
SURNAMES = 5000
# A given name this many times as common for the other sex is left out.
OTHER_SEX = 20

work, out = sys.argv[1], sys.argv[2]
os.makedirs(out, exist_ok=True)


def write(path, counts, most):
    ranked = sorted(counts.items(), key=lambda item: (-item[1], item[0]))[:most]
    with open(path, "w", encoding="utf-8") as f:
        f.writelines(f"{name}\t{count}\n" for name, count in ranked)
    print(os.path.basename(path), len(ranked))


# Keyed by (sex, decade), with decade None for all years.
given = {}
with open(os.path.join(work, "full-list-1974-2025.csv"), encoding="utf-8") as f:
    for row in csv.DictReader(f):
        if not row["Name"][0].isalpha():
            continue
        for key in ((row["Sex"], int(row["Year"]) // 10 * 10), (row["Sex"], None)):
            given.setdefault(key, Counter())[row["Name"]] += int(row["Number"])
for (sex, decade), counts in sorted(given.items(), key=lambda item: (item[0][0], item[0][1] or 0)):
    kind = {"Girl": "feminine", "Boy": "masculine"}[sex]
    other = given[("Boy" if sex == "Girl" else "Girl", decade)]
    counts = {name: count for name, count in counts.items() if other[name] <= OTHER_SEX * count}
    write(os.path.join(out, f"{kind}-{decade}.txt" if decade else f"{kind}.txt"), counts, GIVEN)

# The surnames in the registers of births, marriages, and deaths of every fifth year from 1975 to
# 2010 and each year since 2013: the sheet's rows are a year, a surname, its number, and its rank.
surnames = Counter()
for name, rows in sheets(os.path.join(work, "most-common-surnames-2025.xlsx")):
    if name.startswith("Surnames TimeSeries"):
        for row in rows:
            if len(row) >= 3 and row[0].isdigit() and row[2].isdigit() and row[1][0].isalpha():
                # "Mcdonald", "O'neill", and the like are written with one capital.
                surname = re.sub(r"^(Mc|O'|D')(.)", lambda m: m.group(1) + m.group(2).upper(), row[1])
                surnames[surname] += int(row[2])
write(os.path.join(out, "surnames.txt"), surnames, SURNAMES)
