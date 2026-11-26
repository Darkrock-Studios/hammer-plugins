"""Builds resources/names/finnish.txt: the most common first given names by sex and the most common
surnames, each with its count, of the living Finnish citizens in the Population Information System;
see resources/names/NOTICE.txt. Run it after wikidata.py, whose lists by decade of birth it leaves.
Asks avoindata.suomi.fi's CKAN API for the Digital and Population Data Services Agency's dataset of
names,

    https://avoindata.suomi.fi/data/api/3/action/package_show?id=57282ad6-3ab1-48fb-983a-8aba5ff8d29a

and downloads the two workbooks it lists, named for the day they were drawn from the register:

    etunimitilasto-<date>-dvv.xlsx    given names 5 or more have, by sex, as any, first, or other given name
    sukunimitilasto-<date>-dvv.xlsx   surnames 20 or more have

keeping the answer and both in the working directory, so a second run asks for nothing.

    python3 finnish.py <working directory> <resources/names/finnish.txt>
"""
import json
import os
import re
import sys
import time
import urllib.error
import urllib.request
from collections import Counter

import lists
from xlsx import sheets

GIVEN = 1000
SURNAMES = 5000
# A given name this many times as common for the other sex is left out.
OTHER_SEX = 20
DATASET = "57282ad6-3ab1-48fb-983a-8aba5ff8d29a"
AGENT = "hammer-plugins real-names/tools/finnish.py"
NOT_NAMES = {"etunimetön", "Md", "Mohd", "Mst"}

work, out = sys.argv[1], sys.argv[2]
os.makedirs(work, exist_ok=True)


def fetch(url, path):
    """Downloads `url` into `path`."""
    request = urllib.request.Request(url, headers={"User-Agent": AGENT})
    for attempt in range(4):
        try:
            with urllib.request.urlopen(request, timeout=300) as reply:
                data = reply.read()
            break
        except urllib.error.HTTPError as error:
            # The site's firewall refuses some User-Agents outright; asking again does not help.
            if error.code == 403:
                sys.exit(f"avoindata.suomi.fi refused {url}; download it by hand to {path}")
            if attempt == 3:
                raise
            time.sleep(30 * (attempt + 1))
        except OSError:
            if attempt == 3:
                raise
            time.sleep(30 * (attempt + 1))
    with open(path, "wb") as f:
        f.write(data)
    time.sleep(2)


def workbook(prefix):
    """The path of the latest of the dataset's workbooks whose name starts with `prefix`, downloaded unless it is there."""
    package = os.path.join(work, "dvv-package.json")
    if not os.path.exists(package):
        fetch(f"https://avoindata.suomi.fi/data/api/3/action/package_show?id={DATASET}", package)
    with open(package, encoding="utf-8") as f:
        urls = [resource["url"] for resource in json.load(f)["result"]["resources"]]
    url = max(url for url in urls if re.fullmatch(prefix + r"-[\d-]+-dvv\.xlsx", url.rsplit("/", 1)[-1]))
    path = os.path.join(work, url.rsplit("/", 1)[-1])
    if not os.path.exists(path):
        fetch(url, path)
    return path


def counted(path, sheet):
    """The (name, count) of each row of a workbook's sheet, leaving out what is no name: initials and
    abbreviations such as "K.C.", "MD", and "Md" (for Mohammed), and "etunimetön", having no given name."""
    for name, rows in sheets(path):
        if name == sheet:
            for row in rows:
                if len(row) >= 2 and row[1].isdecimal() and len(row[0]) > 1 and row[0] not in NOT_NAMES and "." not in row[0] \
                        and not any(len(word) > 1 and word.isupper() for word in re.split(r"[ '-]", row[0])):
                    yield row[0], int(row[1])


def surnames(path):
    """The surnames of `path`, a surname that differs from another only by its capitals, such as "Ala-Aho"
    beside "Ala-aho", counted as the more common."""
    counts = Counter(dict(counted(path, "Nimet")))
    forms = {}
    for name, _ in counts.most_common():
        forms.setdefault(name.casefold(), name)
    merged = Counter()
    for name, count in counts.items():
        merged[forms[name.casefold()]] += count
    return merged


# The register's first given names: "ens" is a sex's first given names, "kaikki" all its given names,
# and "muut" those after the first.
given = {kind: Counter(dict(counted(workbook("etunimitilasto"), sheet)))
         for kind, sheet in (("feminine", "Naiset ens"), ("masculine", "Miehet ens"))}
for kind, counts in given.items():
    other = given["masculine" if kind == "feminine" else "feminine"]
    lists.write(out, kind, [(name, count) for name, count in counts.items() if other[name] <= OTHER_SEX * count], GIVEN)

lists.write(out, "surnames", surnames(workbook("sukunimitilasto")).items(), SURNAMES)
