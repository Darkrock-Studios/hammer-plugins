"""Builds resources/names/norwegian/: the most common given names by sex, over all years and for each
decade of birth, each with how many of 100,000 born it was given to, a year at a time, and the most
common surnames, each with its count; see resources/names/NOTICE.txt.
Asks Statistics Norway's StatBank, https://data.ssb.no, for tables 10467 (born persons by name and
year) and 12891 (last names used by 200 persons or more), and keeps each answer in the working
directory, so a second run asks for nothing.

    python3 norwegian.py <working directory> <resources/names/norwegian directory>
"""
import json
import os
import sys
import urllib.request
from collections import Counter

GIVEN = 1000
SURNAMES = 5000
# A given name this many times as common for the other sex is left out.
OTHER_SEX = 20

work, out = sys.argv[1], sys.argv[2]
os.makedirs(work, exist_ok=True)
os.makedirs(out, exist_ok=True)


def table(number, contents):
    """One of a table's contents, as (name's code, name, year, value) for each cell with a value."""
    path = os.path.join(work, f"ssb-{number}.json")
    if not os.path.exists(path):
        query = {"query": [{"code": "ContentsCode", "selection": {"filter": "item", "values": [contents]}}],
                 "response": {"format": "json-stat2"}}
        request = urllib.request.Request(
            f"https://data.ssb.no/api/v0/en/table/{number}", json.dumps(query).encode(),
            {"Content-Type": "application/json", "User-Agent": "hammer-plugins real-names/tools/norwegian.py"})
        with urllib.request.urlopen(request, timeout=300) as reply, open(path, "wb") as f:
            f.write(reply.read())
    with open(path, encoding="utf-8") as f:
        answer = json.load(f)
    # The cells run over the dimensions in order, the last fastest: the name, the contents, the year.
    names, _, years = (answer["dimension"][code]["category"] for code in answer["id"])
    labels = [names["label"][code] for code in sorted(names["index"], key=names["index"].get)]
    codes = sorted(names["index"], key=names["index"].get)
    years = sorted(years["index"], key=years["index"].get)
    for i, value in enumerate(answer["value"]):
        if value:
            yield codes[i // len(years)], labels[i // len(years)], int(years[i % len(years)]), value


def write(path, counts, most):
    ranked = sorted(counts.items(), key=lambda item: (-item[1], item[0]))[:most]
    with open(path, "w", encoding="utf-8") as f:
        f.writelines(f"{name}\t{count}\n" for name, count in ranked)
    print(os.path.basename(path), len(ranked))


# Keyed by (sex, decade), with decade None for all years. A name's code starts with 1 for a girls'
# name and 2 for a boys'. The table has counts only since 1946, and the per cent of those born since
# 1880, to three places.
given = {}
for code, name, year, percent in table(10467, "PersonerProsent"):
    for key in ((code[0], year // 10 * 10), (code[0], None)):
        given.setdefault(key, Counter())[name] += round(percent * 1000)
for (sex, decade), counts in sorted(given.items(), key=lambda item: (item[0][0], item[0][1] or 0)):
    kind = {"1": "feminine", "2": "masculine"}[sex]
    other = given.get(("2" if sex == "1" else "1", decade), Counter())
    counts = {name: count for name, count in counts.items() if other[name] <= OTHER_SEX * count}
    write(os.path.join(out, f"{kind}-{decade}.txt" if decade else f"{kind}.txt"), counts, GIVEN)

# Each surname's count in the latest year it has one.
surnames = {}
for _, name, year, count in table(12891, "Personer"):
    surnames[name] = count
write(os.path.join(out, "surnames.txt"), surnames, SURNAMES)
