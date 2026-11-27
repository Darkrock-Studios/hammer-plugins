"""Builds resources/names/new_zealander.txt: the most common given names by sex, from 1900 to 2025 and for
each decade of birth since, each with its count; see resources/names/NOTICE.txt. Run it after wikidata.py,
whose surnames and lists for earlier decades it leaves. Needs, in the working directory, from the
Department of Internal Affairs' Baby name popularity over time,
https://catalogue.data.govt.nz/dataset/baby-name-popularity-over-time:

    baby-names-1900-to-2025.csv    every name given 10 or more times in a year, by year and sex

    python3 new_zealander.py <working directory> <resources/names/new_zealander.txt>
"""
import csv
import io
import os
import re
import sys
from collections import Counter

import lists

GIVEN = 1000
# A given name this many times as common for the other sex is left out.
OTHER_SEX = 20
DECADES = range(1900, 2030, 10)

# The file's few letters outside ASCII come in more than one encoding: é as 0x82 (code page 437) in
# some rows and 0xe9 (Latin-1) in others, ö as 0xf6, and ī and ō as 0x8d and 0x8f. Its 2025 rows write
# a macron's letter as "?"; these are the names as its earlier years and DIA's workbook of top names by
# year write them.
BYTES = {0x82: "é", 0x8d: "ī", 0x8f: "ō"}
QUESTIONED = {"?io": "Āio", "?ria": "Āria", "M?hina": "Māhina", "M?ia": "Māia", "N?kau": "Nīkau"}
# Babies registered as "Baby", from 2006 to 2008.
PLACEHOLDERS = {"Baby"}
# The file writes Zoë as Zoé up to 2024, where the workbook has Zoë; both have Zoé in 2025.
ZOE_UNTIL = 2024

work, out = sys.argv[1], sys.argv[2]

with open(os.path.join(work, "baby-names-1900-to-2025.csv"), "rb") as f:
    text = "".join(BYTES.get(b, chr(b)) for b in f.read())

# Keyed by (sex, decade), with decade None for all years, each name under its spelling in lower case;
# spellings differing only in capitals (Jo-Anne and Jo-anne, Mackenzie and MacKenzie) are counted
# together under the commoner.
given, spellings = {}, {}
for row in csv.DictReader(io.StringIO(text)):
    year, name = int(row["Year"]), QUESTIONED.get(row["Name"], row["Name"])
    if name in PLACEHOLDERS:
        continue
    if name == "Zoé" and year <= ZOE_UNTIL:
        name = "Zoë"
    if not re.fullmatch(r"[^\W\d_]+(?:[ -][^\W\d_]+)*", name):
        raise ValueError(f"{year} {row['Name']!r}")
    for key in ((row["Sex"], year // 10 * 10), (row["Sex"], None)):
        given.setdefault(key, Counter())[name.lower()] += int(row["Count"])
    spellings.setdefault(name.lower(), Counter())[name] += int(row["Count"])

for sex, kind in (("F", "feminine"), ("M", "masculine")):
    for decade in (None, *DECADES):
        counts = given[(sex, decade)]
        other = given[("M" if sex == "F" else "F", decade)]
        counts = [(spellings[name].most_common(1)[0][0], count) for name, count in counts.items()
                  if other[name] <= OTHER_SEX * count]
        lists.write(out, f"{kind}-{decade}" if decade else kind, counts, GIVEN)
