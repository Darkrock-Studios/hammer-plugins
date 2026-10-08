"""Builds resources/names/<culture>/ for a culture without published statistics, from the people
Wikidata has born since 1880: the most common given names by sex, over all years and for each decade
of birth with enough people, and the most common surnames, each with its count; see resources/names/NOTICE.txt.
Asks https://query.wikidata.org, and keeps each answer in the working directory, so a second run
asks only for what is missing.

    python3 wikidata.py <culture> <working directory> <resources/names/<culture> directory>
"""
import json
import os
import re
import sys
import time
import unicodedata
import urllib.parse
import urllib.request
from collections import Counter

GIVEN = 1000
SURNAMES = 5000
# A name fewer people than this have is left out, as likely a mistake or a foreigner's.
FEWEST = 3
# A decade with fewer people than this gets no list of its own.
FEWEST_PEOPLE = 1000
# A given name this many times as common for the other sex is left out.
OTHER_SEX = 20
DECADES = range(1880, 2030, 10)

# For each culture: "country", whose citizens its people are; "within", the part of it they were born
# in, for a culture that is one of a country's; "born", countries whose citizens count when born in
# what is now the country, such as the Soviet Union for Russia; "macrons", whether to keep long
# vowels' macrons; and "feminine", the endings that make a man's surname a woman's, for a surname list
# for each sex, with "sure", the woman's endings that are no man's.
CULTURES = {
    "brazilian": {"country": "Q155"},
    "chinese": {"country": "Q148", "born": ["Q13426199", "Q8733"]},
    "dutch": {"country": "Q55", "born": ["Q29999"]},
    "egyptian": {"country": "Q79"},
    "english": {"country": "Q145", "within": "Q21"},
    "filipino": {"country": "Q928"},
    "german": {"country": "Q183", "born": ["Q43287", "Q41304", "Q7318", "Q16957", "Q713750"]},
    "greek": {"country": "Q41", "feminine": [
        ("poulos", "poulou"), ("idis", "idou"), ("adis", "adou"), ("akis", "aki"), ("os", "ou"), ("is", "i"), ("as", "a"),
    ]},
    "indian": {"country": "Q668", "born": ["Q129286"]},
    "iranian": {"country": "Q794"},
    "irish": {"country": "Q27", "born": ["Q174193", "Q145"]},
    "israeli": {"country": "Q801"},
    "italian": {"country": "Q38", "born": ["Q172579"]},
    "japanese": {"country": "Q17", "born": ["Q188712"], "macrons": False},
    "kenyan": {"country": "Q114"},
    "korean": {"country": "Q884"},
    "mexican": {"country": "Q96"},
    "nigerian": {"country": "Q1033"},
    "norwegian": {"country": "Q20"},
    "pakistani": {"country": "Q843", "born": ["Q129286"]},
    "polish": {"country": "Q36", "born": ["Q211274", "Q207272"], "feminine": [
        ("ski", "ska"), ("cki", "cka"), ("dzki", "dzka"),
    ], "sure": ("ska", "cka", "dzka")},
    "russian": {"country": "Q159", "born": ["Q15180", "Q34266"], "feminine": [
        ("sky", "skaya"), ("skiy", "skaya"), ("ov", "ova"), ("ev", "eva"), ("in", "ina"), ("tsyn", "tsyna"),
        ("oy", "aya"), ("ny", "naya"),
    ], "sure": ("ova", "eva", "skaya")},
    "scottish": {"country": "Q145", "within": "Q22"},
    "spanish": {"country": "Q29"},
    "swedish": {"country": "Q34"},
    "turkish": {"country": "Q43", "born": ["Q12560"]},
    "ukrainian": {"country": "Q212", "born": ["Q15180", "Q133356"], "feminine": [
        ("skyi", "ska"), ("sky", "ska"), ("ov", "ova"), ("ev", "eva"), ("in", "ina"), ("yn", "yna"), ("nyi", "na"),
    ], "sure": ("ova", "eva", "ska")},
    "vietnamese": {"country": "Q881"},
}
SEXES = {"feminine": "Q6581072", "masculine": "Q6581097"}

culture, work, out = sys.argv[1], sys.argv[2], sys.argv[3]
options = CULTURES[culture]
os.makedirs(work, exist_ok=True)
os.makedirs(out, exist_ok=True)


def people():
    """The patterns for ?p being one of the culture's people, each with a name for its answers' files."""
    home = options["country"]
    within = f" ?p wdt:P19/wdt:P131* wd:{options['within']}." if "within" in options else ""
    patterns = {home: f"?p wdt:P27 wd:{home}.{within}"}
    for country in options.get("born", []):
        patterns[country] = f"?p wdt:P27 wd:{country}; wdt:P19/wdt:P17 wd:{home}. FILTER NOT EXISTS {{ ?p wdt:P27 wd:{home}. }}"
    return patterns


def ask(name, query):
    """The rows of a query's answer, kept in the working directory under `name`."""
    path = os.path.join(work, f"{culture}-{name}.json")
    if not os.path.exists(path):
        url = "https://query.wikidata.org/sparql?" + urllib.parse.urlencode({"query": query, "format": "json"})
        request = urllib.request.Request(url, headers={"User-Agent": "hammer-plugins real-names/tools/wikidata.py"})
        for attempt in range(4):
            try:
                with urllib.request.urlopen(request, timeout=120) as reply:
                    answer = reply.read()
                break
            except OSError:
                # Too many requests, or a query out of time on a busy server.
                if attempt == 3:
                    raise
                time.sleep(30 * (attempt + 1))
        with open(path, "wb") as f:
            f.write(answer)
        time.sleep(2)
    with open(path, encoding="utf-8") as f:
        return [{key: value["value"] for key, value in row.items()} for row in json.load(f)["results"]["bindings"]]


def written(label):
    """A name's English label as the lists have it, or None for one that is not a name in Latin letters."""
    label = unicodedata.normalize("NFC", label.strip())
    if not options.get("macrons", True):
        label = re.sub(r"[āēīōū]", lambda m: "aeiou"["āēīōū".index(m.group())], label)
        label = re.sub(r"[ĀĒĪŌŪ]", lambda m: "AEIOU"["ĀĒĪŌŪ".index(m.group())], label)
    if len(label) < 2 or not label[0].isupper() or not re.fullmatch(r"[^\W\d_]+(?:[ '-][^\W\d_]+)*", label):
        return None
    return label if all("LATIN" in unicodedata.name(c, "") for c in label if c.isalpha()) else None


def write(file, counts, most):
    ranked = sorted(((name, count) for name, count in counts.items() if count >= FEWEST), key=lambda item: (-item[1], item[0]))[:most]
    if sum(count for _, count in ranked) < FEWEST_PEOPLE:
        return
    with open(os.path.join(out, f"{file}.txt"), "w", encoding="utf-8") as f:
        f.writelines(f"{name}\t{count}\n" for name, count in ranked)
    print(file, len(ranked))


# Keyed by (sex, decade), with decade None for all of them. A person's first given name counts: one
# without a place in the series of their names, or first in it. A query for each decade, as one for
# all of a country's people runs out of time.
given = {(sex, decade): Counter() for sex in SEXES for decade in [None, *DECADES]}
surnames = {sex: Counter() for sex in SEXES}
for sex, sex_item in SEXES.items():
    for decade in DECADES:
        for country, pattern in people().items():
            born = f"""{pattern} ?p wdt:P21 wd:{sex_item}; wdt:P569 ?born.
                FILTER(?born >= "{decade}-01-01T00:00:00Z"^^xsd:dateTime && ?born < "{decade + 10}-01-01T00:00:00Z"^^xsd:dateTime)"""
            rows = ask(f"given-{sex}-{decade}-{country}", f"""
                SELECT ?label (COUNT(DISTINCT ?p) AS ?count) WHERE {{
                  {born}
                  ?p p:P735 ?statement. ?statement ps:P735 ?name.
                  FILTER NOT EXISTS {{ ?statement pq:P1545 ?place. FILTER(?place != "1") }}
                  ?name rdfs:label ?label. FILTER(LANG(?label) = "en")
                }} GROUP BY ?label""")
            for row in rows:
                name = written(row["label"])
                if name:
                    given[(sex, decade)][name] += int(row["count"])
                    given[(sex, None)][name] += int(row["count"])
            rows = ask(f"surnames-{sex}-{decade}-{country}", f"""
                SELECT ?label (COUNT(DISTINCT ?p) AS ?count) WHERE {{
                  {born}
                  ?p wdt:P734 ?name. ?name rdfs:label ?label. FILTER(LANG(?label) = "en")
                }} GROUP BY ?label""")
            for row in rows:
                name = written(row["label"])
                if name:
                    surnames[sex][name] += int(row["count"])

for (sex, decade), counts in given.items():
    other = given[("masculine" if sex == "feminine" else "feminine", decade)]
    counts = {name: count for name, count in counts.items() if other[name] <= OTHER_SEX * count}
    write(f"{sex}-{decade}" if decade else sex, counts, GIVEN)

endings = options.get("feminine")
if endings:
    # Both sexes' surnames counted together in the man's form, as Wikidata has few women, and some
    # under the man's form.
    men = Counter(surnames["masculine"])
    for name, count in surnames["feminine"].items():
        # The man's form men have, or the first that fits; a woman's surname that only looks like
        # one, as "Sereda" does, has no man's form of its own ending.
        forms = [name[: -len(new)] + old for old, new in endings if name.endswith(new)]
        sure = forms and name.endswith(options.get("sure", ()))
        men[next((form for form in forms if form in surnames["masculine"]), forms[0] if sure else name)] += count
    women = Counter()
    for name, count in men.items():
        women[next((name[: -len(old)] + new for old, new in endings if name.endswith(old)), name)] += count
    write("surnames-masculine", men, SURNAMES)
    write("surnames-feminine", women, SURNAMES)
else:
    write("surnames", surnames["feminine"] + surnames["masculine"], SURNAMES)
