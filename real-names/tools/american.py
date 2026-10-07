"""Builds resources/names/american/: the most common given names by sex, over all years and for each
decade of birth, and the most common surnames, each with its count; see resources/names/NOTICE.txt.
Needs, in the working directory:

    yob<year>.txt            https://www.ssa.gov/oact/babynames/names.zip, unzipped
    Names_2010Census.csv     https://www2.census.gov/topics/genealogy/2010surnames/names.zip, unzipped

    python3 american.py <working directory> <resources/names/american directory>
"""
import csv
import glob
import os
import re
import sys
from collections import Counter

GIVEN = 1000
SURNAMES = 5000
# A given name this many times as common for the other sex is left out: mostly records of the wrong sex.
OTHER_SEX = 20

# The census writes surnames in capitals without spaces or punctuation. These are the ones among the
# most common that are usually written otherwise; "Mc" names are cased by rule.
SURNAME_FORMS = [
    "MacDonald", "MacKenzie", "MacKay", "MacLeod", "MacLean",
    "O'Brien", "O'Connor", "O'Neill", "O'Neal", "O'Donnell", "O'Connell", "O'Neil", "O'Hara", "O'Malley",
    "O'Keefe", "O'Rourke", "O'Reilly", "O'Sullivan", "O'Toole", "O'Shea", "O'Grady",
    "De Leon", "De La Cruz", "De Jesus", "De La Rosa", "De La Torre", "De Los Santos", "De La Garza",
    "De Guzman", "De Luna", "De Anda", "Del Valle", "Del Rio", "Del Rosario", "Del Toro",
    "DeWitt", "DeLong", "DeLuca", "DeMarco", "DeVries", "DeVore", "DeSantis", "DeHart", "DeLoach",
    "DeSimone", "DeRosa", "DeAngelo", "DeVito", "DeBoer", "DeAngelis", "DeYoung", "DeStefano",
    "DuBois", "DuBose", "Da Silva", "D'Amico", "D'Angelo", "D'Agostino",
    "LaRue", "LaFleur", "LaPointe", "LaCroix", "LaRose", "LaPorte", "LaPlante",
    "LeBlanc", "LeClair", "LeMay", "LeMaster",
    "Van Dyke", "Van Horn", "Van Meter", "Van Winkle",
    "St. John", "St. Clair", "St. Pierre", "Jean-Baptiste",
]
FORMS = {re.sub(r"[^A-Z]", "", form.upper()): form for form in SURNAME_FORMS}


def cased(name, forms={}):
    """`name`, in any case, capitalized, or as `forms` has it."""
    upper = name.upper()
    if upper in forms:
        return forms[upper]
    if upper.startswith("MC") and len(upper) > 3:
        return "Mc" + upper[2] + upper[3:].lower()
    return upper[0] + upper[1:].lower()


def write(path, counts, most):
    ranked = sorted(counts.items(), key=lambda item: (-item[1], item[0]))[:most]
    with open(path, "w", encoding="utf-8") as f:
        f.writelines(f"{name}\t{count}\n" for name, count in ranked)
    print(os.path.basename(path), len(ranked))


work, out = sys.argv[1], sys.argv[2]
os.makedirs(out, exist_ok=True)

# Keyed by (sex, decade), with decade None for all years.
given = {}
for path in sorted(glob.glob(os.path.join(work, "yob*.txt"))):
    decade = int(re.search(r"yob(\d{4})", path).group(1)) // 10 * 10
    with open(path, encoding="utf-8") as f:
        for name, sex, count in csv.reader(f):
            for key in ((sex, decade), (sex, None)):
                given.setdefault(key, Counter())[cased(name)] += int(count)
for (sex, decade), counts in sorted(given.items(), key=lambda item: (item[0][0], item[0][1] or 0)):
    kind = {"F": "feminine", "M": "masculine"}[sex]
    other = given[("M" if sex == "F" else "F", decade)]
    counts = {name: count for name, count in counts.items() if other[name] <= OTHER_SEX * count}
    write(os.path.join(out, f"{kind}-{decade}.txt" if decade else f"{kind}.txt"), counts, GIVEN)

surnames = Counter()
with open(os.path.join(work, "Names_2010Census.csv"), encoding="utf-8") as f:
    for row in csv.DictReader(f):
        if row["name"].isalpha():
            surnames[cased(row["name"], FORMS)] += int(row["count"])
write(os.path.join(out, "surnames.txt"), surnames, SURNAMES)
