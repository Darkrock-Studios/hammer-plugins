"""Builds resources/names/irish/: the most common given names by sex, since 1964 and for each decade of
birth since, and the most common surnames, each with its count; see resources/names/NOTICE.txt. Run
it after wikidata.py, whose lists for earlier decades it leaves. Needs, in the working directory,
the Central Statistics Office's tables as CSV, each from
https://ws.cso.ie/public/api.restful/PxStat.Data.Cube_API.ReadDataset/<table>/CSV/1.0/en:

    VSA50.csv                boys' names with 3 or more occurrences, by year
    VSA60.csv                girls' names with 3 or more occurrences, by year
    VSA110.csv               surnames of babies with 10 or more occurrences, by year

    python3 irish.py <working directory> <resources/names/irish directory>
"""
import csv
import os
import sys
from collections import Counter

GIVEN = 1000
SURNAMES = 5000
# A given name this many times as common for the other sex is left out.
OTHER_SEX = 20

work, out = sys.argv[1], sys.argv[2]
os.makedirs(out, exist_ok=True)


def table(name):
    """A table's counts, as (name, year, count); it has each name's rank in each year too."""
    with open(os.path.join(work, f"{name}.csv"), encoding="utf-8-sig") as f:
        for statistic, label, _, year, _, value, _, count in csv.reader(f):
            if count and not label.endswith("Rank") and statistic != "STATISTIC":
                yield value, int(year), int(count)


def write(path, counts, most):
    ranked = sorted(counts.items(), key=lambda item: (-item[1], item[0]))[:most]
    with open(path, "w", encoding="utf-8") as f:
        f.writelines(f"{name}\t{count}\n" for name, count in ranked)
    print(os.path.basename(path), len(ranked))


# Keyed by (kind, decade), with decade None for all years.
given = {}
for kind, name in (("masculine", "VSA50"), ("feminine", "VSA60")):
    for value, year, count in table(name):
        for key in ((kind, year // 10 * 10), (kind, None)):
            given.setdefault(key, Counter())[value] += count
for (kind, decade), counts in sorted(given.items(), key=lambda item: (item[0][0], item[0][1] or 0)):
    other = given[("masculine" if kind == "feminine" else "feminine", decade)]
    counts = {name: count for name, count in counts.items() if other[name] <= OTHER_SEX * count}
    write(os.path.join(out, f"{kind}-{decade}.txt" if decade else f"{kind}.txt"), counts, GIVEN)

surnames = Counter()
for value, _, count in table("VSA110"):
    surnames[value] += count
write(os.path.join(out, "surnames.txt"), surnames, SURNAMES)
