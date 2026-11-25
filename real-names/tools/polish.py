"""Builds resources/names/polish.txt: the most common given names and surnames of each sex, each with its
count; see resources/names/NOTICE.txt. Run it after wikidata.py, whose lists by decade of birth it
leaves. Needs, in the working directory, from the PESEL register's lists at https://dane.gov.pl,
datasets 1501 (first given names, as of 20 January 2026) and 568 (surnames, as of 22 January 2025),
both counting the dead too:

    imiona-zenskie.xlsx      https://api.dane.gov.pl/resources/1159639/file
    imiona-meskie.xlsx       https://api.dane.gov.pl/resources/1159637/file
    nazwiska-zenskie.xlsx    https://api.dane.gov.pl/resources/63883/file
    nazwiska-meskie.xlsx     https://api.dane.gov.pl/resources/63886/file

    python3 polish.py <working directory> <resources/names/polish.txt>
"""
import os
import re
import sys

import lists
from xlsx import sheets

GIVEN = 1000
SURNAMES = 5000

work, out = sys.argv[1], sys.argv[2]


def cased(name):
    """`name`, in capitals, with a capital only after a space, a hyphen, or an apostrophe."""
    return re.sub(r"(^|[ '-])(.)", lambda m: m.group(1) + m.group(2).upper(), name.lower())


def build(source, file, most):
    """Writes a list from a workbook whose rows start with a name and end with its count."""
    counts = []
    for _, rows in sheets(os.path.join(work, f"{source}.xlsx")):
        # "BRAK DANYCH" counts the people the register has no name for.
        counts += [(cased(row[0]), int(row[-1])) for row in rows
                   if len(row) >= 2 and row[-1].isdigit() and row[0][:1].isalpha() and row[0] != "BRAK DANYCH"]
    lists.write(out, file, counts, most)


build("imiona-zenskie", "feminine", GIVEN)
build("imiona-meskie", "masculine", GIVEN)
build("nazwiska-zenskie", "surnames-feminine", SURNAMES)
build("nazwiska-meskie", "surnames-masculine", SURNAMES)
