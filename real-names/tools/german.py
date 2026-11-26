"""Builds the lists by decade of birth since the 2010s in resources/names/german.txt: the most common first
given names by sex among babies of a few large cities, each with its count; see resources/names/NOTICE.txt.
Run it after wikidata.py, whose other lists it leaves. Germany publishes no statistics of names for the
whole country; these are the cities' open data, each year's file downloaded into the working directory,
so a second run asks for nothing:

    Berlin    https://github.com/berlin/haeufige-vornamen-berlin, data/<year>/all_names.json, 2017 to 2023
    Cologne   https://www.offenedaten-koeln.de, Gesamt_Vornamen_2019-2022_0.csv and Vornamenstatistik_2023.csv
    Leipzig   https://opendata.leipzig.de, each year's Vornamensstatistik, found by asking its portal
    Munich    https://opendata.muenchen.de, dataset vornamen-von-neugeborenen, found by asking its portal

    python3 german.py <working directory> <resources/names/german.txt>
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
# A given name this many times as common for the other sex is left out.
OTHER_SEX = 20
DECADES = (2010, 2020)
AGENT = "hammer-plugins real-names/tools/german.py"
# The files of each year's names Leipzig shares under this licence; its 2024 file has none.
LEIPZIG_LICENCE = "http://dcat-ap.de/def/licenses/dl-by-de/2.0"

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
            except OSError:
                if attempt == 3:
                    raise
                time.sleep(10 * (attempt + 1))
        with open(path, "wb") as f:
            f.write(data)
        time.sleep(1)
    return path


def text(path):
    """A file's text; Leipzig's of 2020 is in Windows-1252."""
    with open(path, "rb") as f:
        data = f.read()
    try:
        return data.decode("utf-8-sig")
    except UnicodeDecodeError:
        return data.decode("cp1252")


def rows(path):
    """A CSV file's rows, by its header's names, with spaces around the names and values taken off."""
    lines = text(path).splitlines()
    reader = csv.reader(lines, delimiter=";" if ";" in lines[0] else ",")
    header = [key.strip().lower() for key in next(reader)]
    for row in reader:
        yield dict(zip(header, (value.strip() for value in row)))


def portal(name, host, query):
    """The resources of the datasets a CKAN portal finds for `query`, its answer kept under `name`."""
    path = download(name, f"https://{host}/api/3/action/package_search?q={query}&rows=100")
    with open(path, encoding="utf-8") as f:
        return [resource for dataset in json.load(f)["result"]["results"] for resource in dataset["resources"]]


def berlin():
    """(sex, year, name, count) for Berlin's first given names: those given at least twice in the city, by
    district, and in "rare_names" those given once in each of several districts. Before 2017 the counts
    are of a name in any place in a child's names, so are left out."""
    for year in range(2017, 2024):
        path = download(f"berlin-{year}.json", "https://raw.githubusercontent.com/berlin/haeufige-vornamen-berlin"
                        f"/main/data/{year}/all_names.json")
        with open(path, encoding="utf-8") as f:
            answer = json.load(f)
        for names in [*answer["common_names"].values(), answer["rare_names"]]:
            for name, sexes in names.items():
                for sex, places in sexes.items():
                    if "1" in places:
                        yield sex, year, name, places["1"]


def cologne():
    """(sex, year, name, count) for Cologne's first given names. Its file of 2010 to 2018 counts a name in
    any place in a child's names, so is left out."""
    base = "https://www.offenedaten-koeln.de/sites/default/files/distribution/"
    for name, file, year in (("cologne-2019-2022.csv", "Gesamt_Vornamen_2019-2022_0.csv", None),
                             ("cologne-2023.csv", "Vornamenstatistik_2023.csv", 2023)):
        for row in rows(download(name, base + file)):
            if row["position"] == "1":
                yield row["geschlecht"], year or int(row["jahr"]), row["vorname"], int(row["anzahl"])


def leipzig():
    """(sex, year, name, count) for Leipzig's first given names. Until 2020 a year's file has only the first
    names, in two tables side by side, the girls' and the boys'."""
    for resource in portal("leipzig.json", "opendata.leipzig.de", "vornamen"):
        year = re.search(r"20\d\d", resource["name"])
        if resource.get("license") != LEIPZIG_LICENCE or resource["format"].upper() != "CSV" or not year:
            continue
        year = int(year.group())
        path = download(f"leipzig-{year}.csv", resource["url"])
        if year > 2020:
            for row in rows(path):
                if row["position"] == "1":
                    yield row["geschlecht"], year, row["vorname"], int(row["anzahl"])
            continue
        lines = text(path).splitlines()
        for row in csv.reader(lines[1:]):
            for _, count, name, sex in (row[0:4], row[5:9]):
                if count.isdigit():
                    yield sex.strip(), year, name, int(count)


def munich():
    """(sex, year, name, count) for the first given names of Munich's babies under a year old at the end of
    each year. A name 4 or fewer have (5 or fewer since 2025) has no count, and is left out."""
    for resource in portal("munich.json", "opendata.muenchen.de", "vornamen"):
        year = re.fullmatch(r"Vornamen (20\d\d)", resource["name"].strip())
        if year:
            year = int(year.group(1))
            for row in rows(download(f"munich-{year}.csv", resource["url"])):
                if row["anzahl"].isdigit():
                    yield row["geschlecht"], year, row["vorname"], int(row["anzahl"])


def written(name):
    """`name` as the lists have it, or None for one that is not a name: a single letter, a note, a mark."""
    name = unicodedata.normalize("NFC", name.strip())
    if not re.fullmatch(r"[^\W\d_]{2,}(?:[ '-][^\W\d_]+)*", name):
        return None
    if name.isupper() or name.islower():
        name = re.sub(r"(^|[ '-])(.)", lambda m: m.group(1) + m.group(2).upper(), name.lower())
    return name


# Keyed by (sex, decade), each name under its spelling in small letters; how many have each spelling
# decides which the lists have.
given, spellings = {}, {}
for source in (berlin, cologne, leipzig, munich):
    for sex, year, name, count in source():
        name = written(name)
        if name and sex in ("w", "m") and year // 10 * 10 in DECADES:
            given.setdefault((sex, year // 10 * 10), Counter())[name.casefold()] += count
            spellings.setdefault(name.casefold(), Counter())[name] += count
for (sex, decade), counts in sorted(given.items()):
    kind = {"w": "feminine", "m": "masculine"}[sex]
    other = given.get(("m" if sex == "w" else "w", decade), Counter())
    counts = {spellings[name].most_common(1)[0][0]: count for name, count in counts.items()
              if other[name] <= OTHER_SEX * count}
    lists.write(out, f"{kind}-{decade}", counts.items(), GIVEN)
