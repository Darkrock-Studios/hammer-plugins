"""Builds resources/names/australian.txt: the most common given names by sex, from 1944 to 2017 and for each
decade of birth since, each with its count; see resources/names/NOTICE.txt. Run it after wikidata.py, whose
surnames and lists for earlier decades it leaves. South Australia alone publishes every name given in a
year, to 2017; the 2020s are the 100 most common of each year in New South Wales, Victoria, and South
Australia. Downloads into the working directory, so a second run asks for nothing:

    South Australia    https://data.sa.gov.au, dataset popular-baby-names, found by asking its portal:
                       baby-names-1944-2013.zip, a file a year and sex for 2014 to 2017, and the top 100
                       of each year and sex since 2020
    New South Wales    https://data.nsw.gov.au, dataset popular-baby-names-from-1952, found by asking its
                       portal: a file of each year's top 100
    Victoria           https://discover.data.vic.gov.au, dataset popular-baby-names, found by asking its
                       portal: a workbook of each year's top 100

    python3 australian.py <working directory> <resources/names/australian.txt>
"""
import csv
import io
import json
import os
import re
import sys
import time
import unicodedata
import urllib.error
import urllib.request
import zipfile
from collections import Counter

import lists

from xlsx import sheets

GIVEN = 1000
# A given name this many times as common for the other sex is left out.
OTHER_SEX = 20
# A name given once in a decade is left out: many are slips of the pen, and the 1940s, with fewer than
# 1,000 names given more often, would end in an alphabetical run of them.
FEWEST = 2
DECADES = range(1940, 2030, 10)
# The years of the top 100s pooled for the 2020s.
RECENT = range(2020, 2030)
AGENT = "hammer-plugins real-names/tools/australian.py"
# Names that stand in for one, and "MC", the start of one cut short.
PLACEHOLDERS = {"UNNAMED", "UNNMAED", "BABY", "MC"}

work, out = sys.argv[1], sys.argv[2]
os.makedirs(work, exist_ok=True)


def download(name, url):
    """The path of the file `name` in the working directory, downloaded from `url` when it is not there."""
    path = os.path.join(work, name)
    if not os.path.exists(path):
        request = urllib.request.Request(url, headers={"User-Agent": AGENT})
        for attempt in range(4):
            try:
                with urllib.request.urlopen(request, timeout=120) as reply:
                    data = reply.read()
                break
            except OSError as error:
                # Too many requests, or a server's error; other refusals don't go away by asking again.
                if attempt == 3 or isinstance(error, urllib.error.HTTPError) and error.code < 500 and error.code != 429:
                    raise
                time.sleep(10 * (attempt + 1))
        with open(path, "wb") as f:
            f.write(data)
        time.sleep(1)
    return path


def portal(name, base, dataset):
    """The resources of a dataset on a CKAN portal whose API is under `base`, its answer kept under `name`."""
    path = download(name, f"{base}/api/3/action/package_show?id={dataset}")
    with open(path, encoding="utf-8") as f:
        return json.load(f)["result"]["resources"]


def table(path):
    """A CSV or workbook's rows, a list of its cells' text."""
    if path.lower().endswith(".xlsx"):
        return [row for _, rows in sheets(path) for row in rows]
    with open(path, "rb") as f:
        data = f.read()
    try:
        text = data.decode("utf-8-sig")
    except UnicodeDecodeError:
        # South Australia's file of girls' names of 2016 is in Windows-1252.
        text = data.decode("cp1252")
    return list(csv.reader(text.splitlines()))


def ranked(rows):
    """(name, count) for rows of a name and its count, in capitals, the names that are not one left out."""
    for row in rows:
        if len(row) >= 2 and row[1].strip().isdigit():
            name = row[0].strip().upper()
            if name.replace("-", "") not in PLACEHOLDERS and re.fullmatch(r"(?:[^\W\d_]{2,}|[^\W\d_]'[^\W\d_]{2,})(?:['-][^\W\d_]+)*", name):
                yield name, int(row[1])


def folded(name):
    """`name` without the marks on its letters."""
    return "".join(c for c in unicodedata.normalize("NFD", name) if not unicodedata.combining(c))


def cased(name):
    """`name`, in capitals, with a capital after a hyphen, in a name such as D'Arcy or O'Shea, and in one
    such as McKenzie."""
    name = re.sub(r"(^|-|(?<=^[do])')([^\W\d_])", lambda m: m.group(1) + m.group(2).upper(), name.lower())
    return re.sub(r"^Mc(.)", lambda m: "Mc" + m.group(1).upper(), name)


def south_australia():
    """(sex, year, name, count, complete) for South Australia's births, complete for a year whose every
    name is there, as they are to 2017; since, only the top 100."""
    files = {}
    for resource in portal("sa.json", "https://data.sa.gov.au/data", "popular-baby-names"):
        label = resource["name"]
        if "1944-2013" in label:
            path = download("sa-1944-2013.zip", resource["url"])
            with zipfile.ZipFile(path) as z:
                for file in z.namelist():
                    match = re.search(r"(female|male)_cy(\d{4})", file)
                    if match:
                        rows = list(csv.reader(io.TextIOWrapper(z.open(file), encoding="ascii")))
                        for name, count in ranked(rows):
                            yield match.group(1), int(match.group(2)), name, count, True
            continue
        year = re.search(r"\b(20\d\d)\b", label)
        sex = "female" if "female" in label.lower() else "male" if "male" in label.lower() else None
        if not year or not sex or resource["format"].upper() not in ("CSV", "XLSX"):
            continue
        year = int(year.group(1))
        # 2016 has its first files and their amended ones.
        if (year < 2018 or year in RECENT) and ((year, sex) not in files or "amended" in label.lower()):
            files[(year, sex)] = resource
    for (year, sex), resource in sorted(files.items()):
        extension = resource["format"].lower()
        path = download(f"sa-{year}-{sex}.{extension}", resource["url"])
        for name, count in ranked(table(path)):
            yield sex, year, name, count, year < 2018


def new_south_wales():
    """(sex, year, name, count) for the top 100 of each year and sex in New South Wales since 1952."""
    for resource in portal("nsw.json", "https://data.nsw.gov.au/data", "a677cbe2-91e1-4e45-b771-08830d3d9e41"):
        if resource["format"].upper() == "CSV":
            with open(download("nsw.csv", resource["url"]), encoding="utf-8-sig") as f:
                for row in csv.DictReader(f):
                    for name, count in ranked([[row["Name"], row["Number"]]]):
                        yield row["Gender"].lower(), int(row["Year"]), name, count


def victoria():
    """(sex, year, name, count) for the top 100 of each year and sex in Victoria since 2008."""
    for resource in portal("vic.json", "https://discover.data.vic.gov.au", "popular-baby-names"):
        year = re.search(r"\b(20\d\d)\b", resource["name"])
        if not year or resource["format"].upper() != "XLSX":
            continue
        year = int(year.group(1))
        rows = table(download(f"vic-{year}.xlsx", resource["url"]))
        # Columns of a name and its count, the boys' and the girls' side by side, under a header naming
        # each name's sex, or under a row above it that does.
        at = next(i for i, row in enumerate(rows) if any(cell.strip().lower() == "count" for cell in row))
        columns = []
        for i, cell in enumerate(rows[at]):
            if "name" in cell.lower():
                above = next((label for label in reversed(rows[at - 1][: i + 1]) if label.strip()), "") if at else ""
                label = (cell if "male" in cell.lower() else above).lower()
                columns.append((i, "female" if "female" in label else "male"))
        for i, sex in columns:
            for name, count in ranked(row[i:i + 2] for row in rows[at + 1:]):
                yield sex, year, name, count


# Keyed by (sex, decade), with decade None for all years, from South Australia's years with every name,
# and the 2020s from the three states' top 100s alone. Each name is under its spelling without marks,
# which a file sometimes adds, as Victoria's of 2024 writes "Nóra"; how many have each spelling decides
# which the lists have.
given, spellings = {}, {}
top = [(*row, False) for row in (*new_south_wales(), *victoria())]
for sex, year, name, count, complete in (*south_australia(), *top):
    if complete:
        keys = ((sex, year // 10 * 10), (sex, None))
    elif year in RECENT:
        keys = ((sex, 2020),)
    else:
        continue
    for key in keys:
        given.setdefault(key, Counter())[folded(name)] += count
    spellings.setdefault(folded(name), Counter())[name] += count

for sex, kind in (("female", "feminine"), ("male", "masculine")):
    for decade in (None, *DECADES):
        counts = given.get((sex, decade), Counter())
        other = given.get(("male" if sex == "female" else "female", decade), Counter())
        counts = [(cased(spellings[name].most_common(1)[0][0]), count) for name, count in counts.items()
                  if count >= FEWEST and other[name] <= OTHER_SEX * count]
        # A decade without a list of its own is left without one from wikidata.py too.
        lists.write(out, f"{kind}-{decade}" if decade else kind, counts, GIVEN)
