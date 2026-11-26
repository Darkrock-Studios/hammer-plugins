"""Builds resources/names/polish.txt: the most common given names and surnames of each sex, and the most
common given names of each sex for each decade of birth since 2000, each with its count; see
resources/names/NOTICE.txt. Run it after wikidata.py, whose lists for earlier decades it leaves.
Downloads, into the working directory unless they are there, each from
https://api.dane.gov.pl/resources/<resource>/file, the PESEL register's lists from datasets 1501 (first
given names, as of 20 January 2026) and 568 (surnames, as of 22 January 2025), both counting the dead
too, and the first names given to children born in each year, from dataset 219:

    imiona-zenskie.xlsx      1159639
    imiona-meskie.xlsx       1159637
    nazwiska-zenskie.xlsx    63883
    nazwiska-meskie.xlsx     63886
    urodzeni-2000-2019.csv   21457     both sexes, a year at a time
    urodzeni-2020-<sex>.xlsx 28021, 28020 (zenskie, meskie)
    urodzeni-2021-<sex>.xlsx 36394, 36393
    urodzeni-2022-<sex>.xlsx 44824, 44825
    urodzeni-2023-<sex>.xlsx 54100, 54099
    urodzeni-2024-<sex>.xlsx 63899, 63900
    urodzeni-2025-<sex>.xlsx 1159538, 1159536

    python3 polish.py <working directory> <resources/names/polish.txt>
"""
import csv
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
# The resource of each year's first names given to girls and to boys, from 2020.
BORN = {2020: (28021, 28020), 2021: (36394, 36393), 2022: (44824, 44825), 2023: (54100, 54099),
        2024: (63899, 63900), 2025: (1159538, 1159536)}

work, out = sys.argv[1], sys.argv[2]
os.makedirs(work, exist_ok=True)


def fetch(file, resource):
    """The path of `file` in the working directory, downloaded from dane.gov.pl's `resource` unless it is there."""
    path = os.path.join(work, file)
    if not os.path.exists(path):
        request = urllib.request.Request(f"https://api.dane.gov.pl/resources/{resource}/file",
                                         headers={"User-Agent": "hammer-plugins real-names/tools/polish.py"})
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


def cased(name):
    """`name`, in capitals, with a capital only after a space, a hyphen, or an apostrophe; a Turkish dotted
    capital I, which lower() would leave a dot above, is a plain I."""
    return re.sub(r"(^|[ '-])(.)", lambda m: m.group(1) + m.group(2).upper(), name.replace("İ", "I").lower())


def given(name):
    """A given `name` as cased() writes it, but with a trailing apostrophe that stands for an accent written as that accent."""
    name = cased(name)
    return re.sub(r"[eo]'$", lambda m: {"e'": "é", "o'": "ò"}[m.group()], name)


def counted(path, form=cased):
    """The (name, count) of each row of a workbook whose rows start with a name and end with its count."""
    for _, rows in sheets(path):
        # "BRAK DANYCH" counts the people the register has no name for.
        yield from ((form(row[0]), int(row[-1])) for row in rows
                    if len(row) >= 2 and row[-1].isdigit() and row[0][:1].isalpha() and row[0] != "BRAK DANYCH")


def build(source, resource, file, most, form=cased):
    """Writes a list from one of the PESEL register's workbooks."""
    lists.write(out, file, list(counted(fetch(f"{source}.xlsx", resource), form)), most)


build("imiona-zenskie", 1159639, "feminine", GIVEN, given)
build("imiona-meskie", 1159637, "masculine", GIVEN, given)
build("nazwiska-zenskie", 63883, "surnames-feminine", SURNAMES)
build("nazwiska-meskie", 63886, "surnames-masculine", SURNAMES)

# Keyed by (kind, decade).
born = {}
with open(fetch("urodzeni-2000-2019.csv", 21457), encoding="utf-8-sig") as f:
    for row in csv.reader(f):
        if row[0].isdigit():
            year, name, count, sex = row
            kind = {"K": "feminine", "M": "masculine"}[sex]
            born.setdefault((kind, int(year) // 10 * 10), Counter())[given(name)] += int(count)
for year, resources in BORN.items():
    for kind, sex, resource in zip(("feminine", "masculine"), ("zenskie", "meskie"), resources):
        for name, count in counted(fetch(f"urodzeni-{year}-{sex}.xlsx", resource), given):
            born.setdefault((kind, year // 10 * 10), Counter())[name] += count
for (kind, decade), counts in sorted(born.items()):
    other = born[("masculine" if kind == "feminine" else "feminine", decade)]
    counts = {name: count for name, count in counts.items() if other[name] <= OTHER_SEX * count}
    lists.write(out, f"{kind}-{decade}", counts.items(), GIVEN)
