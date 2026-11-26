"""Builds resources/names/slovenian.txt: the most common given names by sex, of all living in Slovenia
and for each decade of birth since the 1930s, and the most common surnames, each with its count; see
resources/names/NOTICE.txt. Run it after wikidata.py, whose lists for earlier decades it leaves.
Asks the Statistical Office of the Republic of Slovenia (SURS) for these tables of its PxWeb API,
https://pxweb.stat.si/SiStatData/api/v1/en/Data/<table>.px, and keeps each answer in the working
directory, so a second run asks for nothing:

    05X1005S, 05X1010S       men's and women's first names with 5 or more bearers, of all living, by year
    05X1015S to 05X1018S     surnames with 5 or more bearers, of all living, by year, A to F, G to L,
                             M to R, and S to Ž
    05X2001S, 05X2002S       first names given to 5 or more baby boys and girls, by year

and asks its "Imena in rojstni dnevi" app, https://www.stat.si/ImenaRojstva/sl/ImenaData/GetNameChartValues,
for each name's bearers by period of birth, a name at a time, the most common first, down to names
FEWEST living people have or until no other name could be among a period's most common: about 5,600
requests, two hours.

    python3 slovenian.py <working directory> <resources/names/slovenian.txt>
"""
import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from collections import Counter

import lists

GIVEN = 1000
SURNAMES = 5000
# A given name this many times as common for the other sex is left out.
OTHER_SEX = 20
# The latest year of the tables of all living.
LATEST = "2026"
# The app's periods of birth of those living at the start of LATEST: up to 1940, 1941 to 1950, and
# so on to 2021 to 2025. The first is mostly those born in the 1930s, and each other runs a year after
# a decade, so is the list of the decade it mostly overlaps. Decades from the 1990s have newborns'
# lists instead, which are of births and not survivors.
PERIODS = [1930, 1940, 1950, 1960, 1970, 1980]
NEWBORNS = range(1990, 2030, 10)
# A name fewer living people have is not asked about: its count in a period would be at most this,
# and the app hides most counts that small.
FEWEST = 10

work, out = sys.argv[1], sys.argv[2]
os.makedirs(work, exist_ok=True)
AGENT = "hammer-plugins real-names/tools/slovenian.py"


def fetch(request, timeout=300, missing=False):
    """The answer to `request`, trying again after a wait; with `missing`, None where the server fails
    with an error twice, as the app does for a name it does not have."""
    for attempt in range(4):
        try:
            with urllib.request.urlopen(request, timeout=timeout) as reply:
                return reply.read()
        except OSError as error:
            if missing and attempt and getattr(error, "code", None) == 500:
                return None
            if attempt == 3:
                raise
            time.sleep(30 * (attempt + 1))


def table(name, years):
    """A table's names with a count in each of `years`, as (name, year, count)."""
    path = os.path.join(work, f"surs-{name}.json")
    if not os.path.exists(path):
        # MERITVE 1 is the number of people; 2 is the name's rank.
        query = {"query": [{"code": "MERITVE", "selection": {"filter": "item", "values": ["1"]}}]
                 + ([{"code": "LETO", "selection": {"filter": "item", "values": years}}] if years else []),
                 "response": {"format": "json-stat2"}}
        answer = fetch(urllib.request.Request(
            f"https://pxweb.stat.si/SiStatData/api/v1/en/Data/{name}.px", json.dumps(query).encode(),
            {"Content-Type": "application/json", "User-Agent": AGENT}))
        json.loads(answer)
        with open(path, "wb") as f:
            f.write(answer)
        time.sleep(2)
    with open(path, encoding="utf-8") as f:
        answer = json.load(f)
    # The cells run over the dimensions in order, the last fastest: the name, the measure, the year.
    names, _, periods = (answer["dimension"][code]["category"] for code in answer["id"])
    names = [names["label"][code] for code in sorted(names["index"], key=names["index"].get)]
    periods = sorted(periods["index"], key=periods["index"].get)
    for i, value in enumerate(answer["value"]):
        # A cell with too few people, "-", is null.
        if value:
            yield names[i // len(periods)], int(periods[i % len(periods)]), value


def written(name):
    """`name` with each word in capitals written with a capital only at its start, or None for a
    name with a word of one letter, such as "B K"."""
    words = name.split(" ")
    if any(len(word) < 2 for word in words):
        return None
    return " ".join("-".join(part.capitalize() if part.isupper() and len(part) > 1 else part
                             for part in word.split("-")) for word in words)


answers = {}


def periods(sex, name):
    """The app's counts of the living with given `name` of `sex`, M or Z, by period of birth, each None
    where it hides the count; kept, a line for each name, in the working directory."""
    path = os.path.join(work, f"imena-{sex}.jsonl")
    if sex not in answers:
        answers[sex] = {}
        if os.path.exists(path):
            with open(path, encoding="utf-8") as f:
                for line in f:
                    known = json.loads(line)
                    answers[sex][known["name"]] = known["values"]
    if name not in answers[sex]:
        time.sleep(0.5)
        url = ("https://www.stat.si/ImenaRojstva/sl/ImenaData/GetNameChartValues?"
               + urllib.parse.urlencode({"name": name, "gender": sex}))
        answer = fetch(urllib.request.Request(url, headers={"User-Agent": AGENT}), 60, missing=True)
        # The app answers null for some names, such as Šarika. A count of 0 with the note "z" is
        # hidden; with no note it is none.
        answer = answer and json.loads(answer)
        values = answer and [None if cell["noteText"] == "z" else cell["value"] for cell in answer["values"]]
        answers[sex][name] = values
        with open(path, "a", encoding="utf-8") as f:
            f.write(json.dumps({"name": name, "values": values}, ensure_ascii=False) + "\n")
    return answers[sex][name]


def given(counts, kind):
    """Writes the list `kind` of `counts`, a Counter of names by sex, leaving out a name far more common for the other."""
    sex = "feminine" if kind.startswith("feminine") else "masculine"
    other = counts["masculine" if sex == "feminine" else "feminine"]
    lists.write(out, kind, [(name, count) for name, count in counts[sex].items() if other[name] <= OTHER_SEX * count], GIVEN)


everyone = {"feminine": Counter(), "masculine": Counter()}
for kind, name in (("masculine", "05X1005S"), ("feminine", "05X1010S")):
    for value, _, count in table(name, [LATEST]):
        if value := written(value):
            everyone[kind][value] += count
for kind in ("feminine", "masculine"):
    given(everyone, kind)

# Keyed by decade, each a Counter by sex.
decades = {decade: {"feminine": Counter(), "masculine": Counter()} for decade in [*PERIODS, *NEWBORNS]}
for kind, sex in (("feminine", "Z"), ("masculine", "M")):
    for name, count in sorted(everyone[kind].items(), key=lambda item: (-item[1], item[0])):
        # A name no more common than every period's GIVEN-th so far can be in no period's list.
        if count < FEWEST or all(sum(1 for each in decades[decade][kind].values() if each >= count) >= GIVEN
                                 for decade in PERIODS):
            break
        for decade, value in zip(PERIODS, periods(sex, name) or []):
            if value:
                decades[decade][kind][name] += value
for kind, name in (("masculine", "05X2001S"), ("feminine", "05X2002S")):
    for value, year, count in table(name, None):
        if (value := written(value)) and year // 10 * 10 in NEWBORNS:
            decades[year // 10 * 10][kind][value] += count
for decade, counts in decades.items():
    for kind in ("feminine", "masculine"):
        given(counts, f"{kind}-{decade}")

# Slovenian surnames are the same for women and men.
surnames = Counter()
for name in ("05X1015S", "05X1016S", "05X1017S", "05X1018S"):
    for value, _, count in table(name, [LATEST]):
        if value := written(value):
            surnames[value] += count
lists.write(out, "surnames", surnames.items(), SURNAMES)
