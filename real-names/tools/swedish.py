"""Builds resources/names/swedish.txt: the most common given names by sex, over all years and for each
decade of birth since the 1940s, and the most common surnames, each with its count; see
resources/names/NOTICE.txt. Run it after wikidata.py, whose lists for earlier decades it leaves.
Asks Statistics Sweden's API, https://api.scb.se/OV0104/v1/doris/en/ssd/BE/BE0001, for these tables,
and keeps each answer in the working directory, so a second run asks for nothing:

    BE0001G/BE0001TNamn10    first names normally used with 10 or more bearers, of all living, by year
    BE0001G/BE0001ENamn10    surnames with 10 or more bearers, of all living, by year
    BE0001G/BE0001T100       the 100 most common first names normally used of each sex, of those
                             living at the end of 2021, by year of birth, spellings counted together
    BE0001D/BE0001Nyfodda    first names normally used given to 2 or more newborns, by year

    python3 swedish.py <working directory> <resources/names/swedish.txt>
"""
import json
import os
import re
import sys
import time
import urllib.request
from collections import Counter

import lists

GIVEN = 1000
SURNAMES = 5000
# A given name this many times as common for the other sex is left out.
OTHER_SEX = 20
# The latest year of the tables of all living.
LATEST = "2020"
# The years of birth whose lists come from BE0001T100's survivors, and from BE0001Nyfodda's newborns.
# Before the 1940s the 100 names, the most common among all living, are not the decade's own: they
# leave out Bertil, Gösta, Birgit, and Gunnel, and have Maja and Alva.
SURVIVORS = range(1940, 2000)
NEWBORNS = range(2000, 2023)
# Particles of surnames, written in small letters.
PARTICLES = {"af", "av", "da", "de", "del", "den", "der", "di", "du", "la", "le", "van", "von"}

work, out = sys.argv[1], sys.argv[2]
os.makedirs(work, exist_ok=True)


def table(path, variables, years=None):
    """A table's cells with a count, as (each variable's code, count): every value of each of
    `variables` and of the year, or with `years`, only those years', asked for 5 at a time to stay under
    the API's 150,000 cells an answer."""
    years = [str(year) for year in years] if years else [None]
    for start in range(0, len(years), 5):
        some = years[start:start + 5]
        name = path.split("/")[-1] + ("" if some == [None] else f"-{some[0]}-{some[-1]}")
        file = os.path.join(work, f"scb-{name}.json")
        if not os.path.exists(file):
            query = [{"code": code, "selection": {"filter": "all", "values": ["*"]}} for code in variables + ["Tid"]]
            if some != [None]:
                query[-1]["selection"] = {"filter": "item", "values": some}
            fetch(path, {"query": query, "response": {"format": "json"}}, file)
        with open(file, encoding="utf-8") as f:
            for row in json.load(f)["data"]:
                # ".." is a count too small to give.
                if row["values"][0].isdigit() and int(row["values"][0]):
                    yield tuple(row["key"]), int(row["values"][0])


def fetch(path, query, file):
    """Asks for the table at `path` with `query`, and keeps the answer in `file`."""
    request = urllib.request.Request(
        f"https://api.scb.se/OV0104/v1/doris/en/ssd/BE/BE0001/{path}", json.dumps(query).encode(),
        {"Content-Type": "application/json", "User-Agent": "hammer-plugins real-names/tools/swedish.py"})
    for attempt in range(4):
        time.sleep(2)
        try:
            with urllib.request.urlopen(request, timeout=300) as reply:
                answer = reply.read().decode("utf-8-sig")
            json.loads(answer)
            break
        except (OSError, ValueError):
            if attempt == 3:
                raise
            time.sleep(30 * (attempt + 1))
    with open(file, "w", encoding="utf-8") as f:
        f.write(answer)


def written(name):
    """`name` as it is written, or None for one cut short. SCB pads names with spaces, cuts them at 20
    characters with a full stop, writes an apostrophe as an acute accent, and writes some names in capitals."""
    name = name.strip().replace("\u00b4", "'")
    if name.endswith("."):
        return None

    def word(m):
        if not m.group().isupper():
            return m.group()
        # A particle before another word, as in "af Klint"; "Le" alone is a surname.
        return m.group().lower() if m.group(1) and m.group().lower() in PARTICLES else m.group().capitalize()
    return re.sub(r"[^\s-]{2,}(?=(\s?))", word, name)


def given(counts, kind):
    """Writes the list `kind` of `counts`, a Counter of names by sex, leaving out a name far more common for the other."""
    sex = "feminine" if kind.startswith("feminine") else "masculine"
    other = counts["masculine" if sex == "feminine" else "feminine"]
    lists.write(out, kind, [(name, count) for name, count in counts[sex].items() if other[name] <= OTHER_SEX * count], GIVEN)


# A name's code is the name and K for a woman's or M for a man's.
everyone = {"feminine": Counter(), "masculine": Counter()}
for (code, year), count in table("BE0001G/BE0001TNamn10", ["Tilltalsnamn"], [LATEST]):
    code = code.strip()
    if name := written(code[:-1]):
        everyone["feminine" if code.endswith("K") else "masculine"][name] += count
for kind in ("feminine", "masculine"):
    given(everyone, kind)

# Keyed by decade, each a Counter by sex.
decades = {}
# A name's code starts with 1 for a man's and 2 for a woman's. A name counts its other spellings too,
# such as Karl with Carl, and is in capitals where SCB names a group by one of them, such as CHRISTINA
# with Kristina.
for (code, year), count in table("BE0001G/BE0001T100", ["Tilltalsnamn"]):
    if int(year) in SURVIVORS:
        decade = decades.setdefault(int(year) // 10 * 10, {"feminine": Counter(), "masculine": Counter()})
        decade["feminine" if code[0] == "2" else "masculine"][written(code[1:])] += count
# Girls are 2 and boys 1.
for (name, sex, year), count in table("BE0001D/BE0001Nyfodda", ["Tilltalsnamn", "Kon"], NEWBORNS):
    if name := written(name):
        decade = decades.setdefault(int(year) // 10 * 10, {"feminine": Counter(), "masculine": Counter()})
        decade["feminine" if sex == "2" else "masculine"][name] += count
for decade, counts in sorted(decades.items()):
    for kind in ("feminine", "masculine"):
        given(counts, f"{kind}-{decade}")

surnames = Counter()
for (name, year), count in table("BE0001G/BE0001ENamn10", ["Efternamn"], [LATEST]):
    if name := written(name):
        surnames[name] += count
lists.write(out, "surnames", surnames.items(), SURNAMES)
