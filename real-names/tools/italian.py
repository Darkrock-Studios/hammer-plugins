"""Builds resources/names/italian.txt's lists of the most common given names by sex for each decade of
birth since the 2000s, each with its count; see resources/names/NOTICE.txt. Run it after wikidata.py,
whose other lists it leaves, and whose accented names it follows.
Asks ISTAT's Contanomi, https://www.istat.it/dati/calcolatori/contanomi/, for the names of births
registered in Italy, a year and a sex at a time, and for single names' counts in each year, and keeps
each answer in the working directory, so a second run asks for nothing.

    python3 italian.py <working directory> <resources/names/italian.txt>
"""
import json
import os
import re
import sys
import time
import unicodedata
import urllib.error
import urllib.parse
import urllib.request
from collections import Counter

import lists

GIVEN = 1000
# A given name this many times as common for the other sex is left out.
OTHER_SEX = 20
# The 2020s count the years the service gives whole: it stops 2020's and 2021's lists at a few
# hundred names, and 2025's counts grow each week until the year is complete.
YEARS = [*range(2000, 2020), 2022, 2023, 2024]
SEXES = {"f": "feminine", "m": "masculine"}
SERVICE = "https://www.istat.it/wp-content/themes/EGPbs5-child/contanomi/nati/index2022.php"
ALL = 100000

# The service writes an accent as an apostrophe after its vowel, and before 2011 mostly leaves it
# out. These take an acute accent; the others a grave, as Italian writes them.
ACUTE = [
    "José", "René", "Renée", "André", "Chloé", "Khloé", "Hervé", "Desirée", "Amélie", "Zoé", "Josué",
    "Inés", "Matías", "Andrés", "Nicolás",
]
# What the service has for a name it was not given.
PLACEHOLDERS = {"DATO MANCANTE", "NON INDICATO", "SCONOSCIUTO"}

work, out = sys.argv[1], sys.argv[2]
os.makedirs(work, exist_ok=True)


def ask(wait, **query):
    """The service's answer to `query`, waiting up to `wait` seconds for it, or None when it answers
    with nothing."""
    request = urllib.request.Request(
        f"{SERVICE}?{urllib.parse.urlencode(query)}",
        headers={"User-Agent": "hammer-plugins real-names/tools/italian.py"})
    for attempt in range(5):
        try:
            with urllib.request.urlopen(request, timeout=wait) as reply:
                answer = reply.read().decode("utf-8")
            break
        except (OSError, urllib.error.HTTPError, ValueError):
            if attempt == 4:
                raise
            time.sleep(10 * 2 ** attempt)
    # Asked often, the service takes 25 seconds or more to answer.
    time.sleep(5)
    # JSONP, as "callback(...);", the callback's name fixed whatever the query asks.
    answer = answer[answer.index("(") + 1:answer.rindex(")")]
    return json.loads(answer) if answer else None


def year(number, sex):
    """A year's names of a sex, most common first, as (name, count), and whether that is all of them.

    The service answers with nothing at all when its answer would hold one of a few rows it cannot
    encode, so this keeps the most it can answer: all of them, or those before the first such row."""
    path = os.path.join(work, f"year-{number}-{sex}.json")
    if not os.path.exists(path):
        def rows(limit):
            answer = ask(120, type="list", limit=limit, year=number, gender=sex)
            return None if answer is None else [(row["name"], row["count"]) for row in answer["0"]]

        kept, full = rows(ALL), True
        if kept is None:
            # The longest answer below the first row that fails: double, then halve the gap.
            good, bad, full = 0, None, False
            kept = []
            while bad is None or bad - good > 1:
                limit = good * 2 or 100 if bad is None else (good + bad) // 2
                if limit >= ALL:
                    bad = ALL
                    continue
                answer = rows(limit)
                if answer is None:
                    bad = limit
                elif len(answer) < limit:
                    raise ValueError(f"{number} {sex}: {limit} asked, {len(answer)} answered")
                else:
                    good, kept = limit, answer
        with open(path, "w", encoding="utf-8") as f:
            json.dump({"full": full, "rows": kept}, f, ensure_ascii=False)
    with open(path, encoding="utf-8") as f:
        answer = json.load(f)
    return answer["rows"], answer["full"]


series_path = {sex: os.path.join(work, f"names-{sex}.json") for sex in SEXES}
series = {}
for sex, path in series_path.items():
    series[sex] = {}
    if os.path.exists(path):
        with open(path, encoding="utf-8") as f:
            series[sex] = json.load(f)


def save():
    """Keeps the series asked for so far in the working directory."""
    for sex, path in series_path.items():
        with open(path, "w", encoding="utf-8") as f:
            json.dump(series[sex], f, ensure_ascii=False)


def name_series(name, sex):
    """A name's count for a sex in each year, by year."""
    answer = ask(60, type="name", name=name, gender=sex)
    series[sex][name] = {str(row["year"]): row["count"] for key, rows in answer.items() if key != "years"
                         for row in rows if row["count"]}


# Keyed by (sex, year): the counts the year's list has, and for a list that stops short of all its
# names, the count of its last, which no name left out of it is over.
listed, short = {}, {}
for number in YEARS:
    for sex in SEXES:
        rows, full = year(number, sex)
        listed[(sex, number)] = dict(rows)
        if not full:
            short[(sex, number)] = rows[-1][1] if rows else ALL


def counts(name, sex, years):
    """A name's count over `years`: its series' where it has one, or else the least the lists allow,
    and a guess at the most: in a year a list leaves it out, as common as in the most common of the
    two years either side that a list has it, so far as that list allows."""
    if name in series[sex]:
        total = sum(series[sex][name].get(str(number), 0) for number in years)
        return total, total
    low = high = sum(listed[(sex, number)].get(name, 0) for number in years)
    for number in years:
        if (sex, number) in short and name not in listed[(sex, number)]:
            near = [listed[(sex, other)].get(name, 0)
                    for other in range(number - 2, number + 3) if other in YEARS]
            high += min(short[(sex, number)], max(near))
    return low, high


# A name counts with its whole series where some year's list stops short of it, asked for each name
# that could be among a decade's GIVEN most common, the likeliest first. Asking for every name a short
# list could leave out would take tens of thousands of requests. The service cannot find a name with
# an apostrophe.
decades = sorted({number // 10 * 10 for number in YEARS})
while True:
    wanted = {}
    for sex in SEXES:
        for decade in decades:
            years = [number for number in YEARS if number // 10 * 10 == decade]
            names = {name for number in years for name in listed[(sex, number)]}
            names = {name: counts(name, sex, years) for name in names}
            least = sorted((low for low, _ in names.values()), reverse=True)[GIVEN - 1]
            for name, (low, high) in names.items():
                if high > low and high >= least and "'" not in name:
                    wanted[(name, sex)] = max(wanted.get((name, sex), 0), high)
    if not wanted:
        break
    print("asking for", len(wanted), "names", file=sys.stderr)
    for name, sex in sorted(wanted, key=wanted.get, reverse=True)[:20]:
        name_series(name, sex)
    save()


def fold(name):
    """`name` without the marks on its letters."""
    return "".join(c for c in unicodedata.normalize("NFD", name) if not unicodedata.combining(c))


def marks(name):
    """The places in `name` of its letters with marks."""
    return {i for i, c in enumerate(name) if c != fold(c)}


# The spellings with marks of the words of the names the culture's other lists have, and ACUTE, by
# their letters.
accented = {}
for key, lines in lists.read(out).items():
    if key != "surnames" and not re.fullmatch(r"\w+-20[0-9]0", key):
        for line in reversed(lines):
            for word in line.split("\t")[0].split(" "):
                if marks(word):
                    accented.setdefault(fold(word), {})[frozenset(marks(word))] = word
for word in ACUTE:
    accented.setdefault(fold(word), {})[frozenset(marks(word))] = word

GRAVE = dict(zip("aeiou", "àèìòù"))


def written(name):
    """A name, in capitals with an apostrophe after a vowel for its accent, as Italian writes it; None
    for a placeholder, or for one with other characters, as a few mistyped registrations have."""
    if not re.fullmatch(r"[A-Z' -]+", name) or name in PLACEHOLDERS:
        return None
    name = re.sub(r"([aeiou])'", lambda m: GRAVE[m.group(1)], " ".join(name.lower().split()))
    name = re.sub(r"(^|[ '-])(.)", lambda m: m.group(1) + m.group(2).upper(), name)
    return " ".join(accented.get(fold(word), {}).get(frozenset(marks(word)), word)
                    for word in name.split(" "))


# Each year's counts, a name's whole series where there is one. The service keeps most accents as a
# mark on the letter, which a list shows as an apostrophe after it and a series counts with the
# unmarked name; an accent at a name's end, it keeps as an apostrophe, which a series leaves out.
yearly = {}
for (sex, number), names in listed.items():
    marked = {}
    for name in names:
        if "'" in name.rstrip("'"):
            marked.setdefault(name.replace("'", ""), []).append(name)
    year_counts = dict(names)
    for name, years in series[sex].items():
        if str(number) in years:
            for other in marked.get(name, []):
                del year_counts[other]
            year_counts[name] = years[str(number)]
    yearly[(sex, number)] = year_counts
for key, names in yearly.items():
    yearly[key] = Counter()
    for name, count in names.items():
        if written(name):
            yearly[key][written(name)] += count

# Words of the same letters are one, written as the most common of them is since the service has
# written accents.
spellings = Counter()
for (sex, number), names in yearly.items():
    for name, count in names.items():
        for word in name.split(" "):
            spellings[word] += count if number >= 2011 else 0
forms = {}
for word, _ in sorted(spellings.items(), key=lambda item: (item[1], item[0])):
    forms[fold(word)] = word

given = {}
for (sex, number), names in yearly.items():
    for name, count in names.items():
        name = " ".join(forms[fold(word)] for word in name.split(" "))
        given.setdefault((sex, number // 10 * 10), Counter())[name] += count
for (sex, decade), names in sorted(given.items()):
    other = given[("m" if sex == "f" else "f", decade)]
    names = {name: count for name, count in names.items() if other[name] <= OTHER_SEX * count}
    lists.write(out, f"{SEXES[sex]}-{decade}", names.items(), GIVEN)
