"""Builds resources/names/swiss.txt: the most common first given names by sex of Switzerland's permanent
resident population at the end of 2025, over all years of birth and for each decade of birth since the
1920s, and the most common surnames, each with its count; see resources/names/NOTICE.txt. Run it after
wikidata.py, whose lists for earlier decades it leaves. Asks opendata.swiss's catalogue,
https://ckan.opendata.swiss/api/3/action/package_show?id=<dataset>, for these datasets of the Federal
Statistical Office, downloads each one's CSV file from https://dam-api.bfs.admin.ch, and keeps each
answer and file in the working directory, so a second run asks for nothing:

    weibliche-vornamen-der-bevolkerung-nach-jahrgang-schweiz-2025    women's first names, by year of birth
    mannliche-vornamen-der-bevolkerung-nach-jahrgang-schweiz-2025    men's first names, by year of birth
    nachnamen-der-standigen-wohnbevolkerung-nach-sprachregion3       surnames, by language region

    python3 swiss.py <working directory> <resources/names/swiss.txt>
"""
import csv
import json
import os
import re
import sys
import time
import unicodedata
import urllib.request
from collections import Counter

import lists

GIVEN = 1000
SURNAMES = 5000
# A given name this many times as common for the other sex is left out.
OTHER_SEX = 20
# A decade of birth gets a list of its own with this many of a sex still living: the 1910s have a few
# dozen, and are left to Wikidata.
DECADE_PEOPLE = 2000
# opendata.swiss refuses Python's own User-Agent, though not this one.
AGENT = "hammer-plugins real-names/tools/swiss.py"
DATASETS = {
    "feminine": "weibliche-vornamen-der-bevolkerung-nach-jahrgang-schweiz-2025",
    "masculine": "mannliche-vornamen-der-bevolkerung-nach-jahrgang-schweiz-2025",
    "surnames": "nachnamen-der-standigen-wohnbevolkerung-nach-sprachregion3",
}
GRAVE = {"a": "à", "e": "è", "i": "ì", "o": "ò", "u": "ù"}
# Words of a compound surname written in small letters, as in "Droz-dit-Busset" and "Matthey-de-l'Endroit".
PARTICLES = {"d", "de", "des", "dit", "du", "l", "la", "le"}

work, out = sys.argv[1], sys.argv[2]
os.makedirs(work, exist_ok=True)


def download(name, url):
    """The path of the file `name` in the working directory, downloaded from `url` when it is not there."""
    path = os.path.join(work, name)
    if not os.path.exists(path):
        request = urllib.request.Request(url, headers={"User-Agent": AGENT})
        for attempt in range(4):
            try:
                with urllib.request.urlopen(request, timeout=600) as reply:
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


def rows(dataset):
    """The rows of a dataset's CSV file, found through the catalogue, by its header's names."""
    path = download(f"package-{dataset}.json", f"https://ckan.opendata.swiss/api/3/action/package_show?id={dataset}")
    with open(path, encoding="utf-8") as f:
        url, = (resource["url"] for resource in json.load(f)["result"]["resources"] if resource["format"] == "CSV")
    with open(download(f"{dataset}.csv", url), encoding="utf-8-sig") as f:
        yield from csv.DictReader(f)


def surname(name):
    """`name` as it is written. Some Italian surnames are written without accents, a final accented vowel
    as the vowel and an apostrophe, such as "Macri'" for "Macrì"; a few in small letters; and some in small
    letters after a hyphen or an apostrophe, such as "Robert-nicoud" and "O'brien"."""
    name = unicodedata.normalize("NFC", name.strip())
    name = re.sub(r"([aeiou])'(?= |$)", lambda m: GRAVE[m.group(1)], name)
    if name.islower():
        name = re.sub(r"(^|[ '-])(.)", lambda m: m.group(1) + m.group(2).upper(), name)
    name = re.sub(r"-([^\W\d_]+)", lambda m: "-" + (m.group(1) if m.group(1) in PARTICLES else m.group(1)[:1].upper() + m.group(1)[1:]), name)
    return re.sub(r"(^|[ -])(O|N|[Dd]|[Dd]ell|[Dd]all)'([a-z])", lambda m: m.group(1) + m.group(2) + "'" + m.group(3).upper(), name)


def given(counts, kind):
    """Writes the list `kind` of `counts`, a Counter of names by sex, leaving out a name far more common for the other."""
    sex = "feminine" if kind.startswith("feminine") else "masculine"
    other = counts["masculine" if sex == "feminine" else "feminine"]
    lists.write(out, kind, [(name, count) for name, count in counts[sex].items() if other[name] <= OTHER_SEX * count], GIVEN)


# Keyed by decade, with None for all years, each a Counter by sex. The files count only a person's first
# given name, and leave out a name fewer than 3 people have.
counts = {}
for sex in ("feminine", "masculine"):
    for row in rows(DATASETS[sex]):
        name, count = unicodedata.normalize("NFC", row["firstname"].strip()), int(row["VALUE"])
        for decade in (None, int(row["YEAROFBIRTH"]) // 10 * 10):
            counts.setdefault(decade, {"feminine": Counter(), "masculine": Counter()})[sex][name] += count
for kind in ("feminine", "masculine"):
    given(counts[None], kind)
for decade, decade_counts in sorted((decade, each) for decade, each in counts.items() if decade):
    for kind in ("feminine", "masculine"):
        if sum(decade_counts[kind].values()) >= DECADE_PEOPLE:
            given(decade_counts, f"{kind}-{decade}")

# Each surname under its spelling folded, which also takes "ß" as "ss"; how many have each spelling
# decides which the list has. The file counts a surname at least 3 people have in a language region,
# in each of the four.
surnames, spellings = Counter(), {}
for row in rows(DATASETS["surnames"]):
    name = surname(row["LASTNAME"])
    surnames[name.casefold()] += int(row["VALUE"])
    spellings.setdefault(name.casefold(), Counter())[name] += int(row["VALUE"])
lists.write(out, "surnames", [(spellings[key].most_common(1)[0][0], count) for key, count in surnames.items()], SURNAMES)
