"""Builds resources/names/<culture>.txt for a culture without published statistics, from the people
Wikidata has born since 1880: the most common given names by sex, over all years and for each decade
of birth with enough people, and the most common surnames, each with its count; see resources/names/NOTICE.txt.
Asks https://query.wikidata.org, and keeps each answer in the working directory, so a second run
asks only for what is missing.

    python3 wikidata.py <culture> <working directory> <resources/names/<culture>.txt>
"""
import hashlib
import json
import os
import re
import sys
import time
import unicodedata
import urllib.parse
import urllib.request
from collections import Counter

import lists

GIVEN = 1000
SURNAMES = 5000
# A name fewer people than this have is left out, as likely a mistake or a foreigner's.
FEWEST = 3
# A decade gets a list of its own with this many people and this many names to pick from, and there
# is a list of all years, or of surnames, with this many names.
DECADE_PEOPLE = 500
DECADE_NAMES = 50
FEWEST_NAMES = 100
# A given name this many times as common for the other sex is left out.
OTHER_SEX = 20
DECADES = range(1880, 2030, 10)

# For each culture: "country", whose citizens its people are, or "countries", several, a person who is
# a citizen of two of them, or of two of "born", counting once; "within", the part of it they were born
# in, for a culture that is one of a country's; "born", countries whose citizens count when born in
# what is now the country, such as the Soviet Union for Russia; "marks", whether to keep the marks on
# letters, which English leaves off Japanese's long vowels, or "scholarly", whether to leave off those
# of a scholar's transliteration of Arabic, as in Muḥammad and Muṣṭafá, which people leave off their own;
# "spelled", whether a name counts as the person's own label spells it, for a culture whose people
# Wikidata links to a name in another spelling, as Muhammad for a Mohamed, with "particles", words
# that only start a name, to which the label's next word belongs; and "feminine", the endings
# that make a man's surname a woman's, for a surname list for each sex, with "sure", the woman's
# endings that are no man's.

# For Arabic names: the particles that are part of a name and never all of it, as Abdel in Abdel Fattah
# and Ben in Ben Ali.
ARABIC = {"spelled": True, "scholarly": True, "particles": {
    "Abd", "Abdel", "Abdul", "Abou", "Abu", "Ait", "Aït", "Al", "Ben", "Bin", "Bou", "El", "Ibn", "Ould",
}}
CULTURES = {
    "australian": {"country": "Q408"},
    "belgian": {"country": "Q31"},
    "brazilian": {"country": "Q155"},
    "dutch": {"country": "Q55", "born": ["Q29999"]},
    # Egypt's earlier states and the Ottoman Empire, of which Egyptians were subjects before 1914.
    "egyptian": {"countries": ["Q79"], "born": ["Q127861", "Q370173", "Q124943", "Q3087763", "Q170468", "Q12560"],
                 **ARABIC},
    # The United Kingdom's people: asking for those born in England runs out of time.
    "english": {"country": "Q145", "born": ["Q174193"]},
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
    "japanese": {"country": "Q17", "born": ["Q188712"], "marks": False},
    "korean": {"country": "Q884"},
    # Morocco, Algeria, and Tunisia, and Tunisia's earlier states; not France, whose citizens born in
    # the Maghreb before independence are mostly its settlers.
    "maghrebi": {"countries": ["Q1028", "Q262", "Q948"], "born": ["Q2017684", "Q4899891", "Q12560"], **ARABIC},
    "mexican": {"country": "Q96"},
    "nigerian": {"country": "Q1033"},
    "norwegian": {"country": "Q20"},
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
}
SEXES = {"feminine": "Q6581072", "masculine": "Q6581097"}

culture, work, out = sys.argv[1], sys.argv[2], sys.argv[3]
options = CULTURES[culture]
os.makedirs(work, exist_ok=True)


def people():
    """The patterns for ?p being one of the culture's people, each with a name for its answers' files."""
    if "countries" in options:
        # One pattern for all the countries and one for all of "born", as a query counts a person once.
        homes = " ".join(f"wd:{country}" for country in options["countries"])
        patterns = {"-".join(options["countries"]): f"VALUES ?home {{ {homes} }} ?p wdt:P27 ?home."}
        if "born" in options:
            earlier = " ".join(f"wd:{country}" for country in options["born"])
            patterns["born"] = f"""VALUES ?earlier {{ {earlier} }} VALUES ?home {{ {homes} }}
                ?p wdt:P27 ?earlier; wdt:P19/wdt:P17 ?home.
                FILTER NOT EXISTS {{ VALUES ?citizen {{ {homes} }} ?p wdt:P27 ?citizen. }}"""
        return patterns
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
                json.loads(answer)
                break
            except (OSError, ValueError):
                # Too many requests, a query out of time on a busy server, or an answer cut short.
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
    if not options.get("marks", True):
        label = "".join(c for c in unicodedata.normalize("NFD", label) if not unicodedata.combining(c))
    if options.get("scholarly"):
        label = unscholarly(label)
    if len(label) < 2 or not label[0].isupper() or not re.fullmatch(r"[^\W\d_]+(?:[ '-][^\W\d_]+)*", label):
        return None
    return label if all("LATIN" in unicodedata.name(c, "") for c in label if c.isalpha()) else None


def write(file, counts, most, decade=False):
    kept = [(name, count) for name, count in counts.items() if count >= FEWEST]
    enough = min(len(kept), most)
    too_few = (sum(counts.values()) < DECADE_PEOPLE or enough < DECADE_NAMES) if decade else enough < FEWEST_NAMES
    # A list too few people have is dropped, along with any an earlier run wrote.
    lists.write(out, file, [] if too_few else kept, most)


# A name's English label, or where it has none its label for every language written in Latin letters
# ("mul"), which many name items have in its place.
LABEL = """OPTIONAL { ?name rdfs:label ?en. FILTER(LANG(?en) = "en") }
    OPTIONAL { ?name rdfs:label ?mul. FILTER(LANG(?mul) = "mul") }
    BIND(COALESCE(?en, ?mul) AS ?label) FILTER(BOUND(?label))"""

def among(pattern, sex_item, decade):
    """The pattern for ?p being one of the culture's people of a sex born in a decade."""
    return f"""{pattern} ?p wdt:P21 wd:{sex_item}; wdt:P569 ?born.
        FILTER(?born >= "{decade}-01-01T00:00:00Z"^^xsd:dateTime && ?born < "{decade + 10}-01-01T00:00:00Z"^^xsd:dateTime)"""


def unscholarly(text):
    """`text` without the marks of a scholar's transliteration of Arabic: macrons and dots and breves
    below, and the acute on an a that ends a word, which no French name has."""
    kept = unicodedata.normalize("NFC", "".join(c for c in unicodedata.normalize("NFD", text) if c not in "\u0304\u0323\u032e\u0331"))
    return re.sub(r"á(?![^\W\d_])", "a", kept)


def skeleton(text):
    """The letters that spellings of one Arabic name share: its consonants, with no marks, doubled
    letters, or vowels but a first one, as a, and with j as g and q as k."""
    shape = ""
    for i, c in enumerate(c for c in unicodedata.normalize("NFD", text.lower()) if "LATIN" in unicodedata.name(c, "")):
        c = ("a" if i == 0 else "") if c in "aeiouy" else {"j": "g", "q": "k"}.get(c, c)
        if not shape.endswith(c):
            shape += c
    return shape


def fold(text):
    """`text` in lower case with a hyphen as a space, a character for each of its characters."""
    return "".join(" " if c == "-" else "'" if c in "’ʼ" else c.lower()[:1] for c in text)


def spelled(person, spellings, last=False):
    """Where in `person`, a person's label, one of `spellings` is, as (start, end, spelling): the first,
    or with `last` the last, and the longest there, with the person's own hyphens and case if the name
    has them. Where none is, the words that start the label, or with `last` end it, if they are a
    spelling of the same name; None when they are not."""
    person = unicodedata.normalize("NFC", person)
    if options.get("scholarly"):
        person = unscholarly(person)
    folded, found = fold(person), []
    for spelling in spellings:
        sought = fold(spelling)
        start = folded.find(sought)
        while start >= 0:
            end = start + len(sought)
            if (start == 0 or not folded[start - 1].isalnum()) and (end == len(folded) or not folded[end].isalnum()):
                found.append((-start if last else start, -len(sought), person[start:end] != spelling, start, end, spelling))
            start = folded.find(sought, start + 1)
    particles = options.get("particles", ())
    if found:
        start, end, spelling = min(found)[3:]
        if spelling not in particles:
            return start, end, spelling
        following = re.match(r"[ -][^\s-]+", person[end:])
        name = following and written(person[start:end + following.end()])
        return (start, end + following.end(), name) if name else None
    words, shapes = list(re.finditer(r"[^\s-]+", person)), {skeleton(spelling) for spelling in spellings}
    for size in range(min(4, len(words)), 0, -1):
        first, final = (words[-size], words[-1]) if last else (words[0], words[size - 1])
        name = written(person[first.start():final.end()])
        if name and name not in particles and skeleton(name) in shapes:
            return first.start(), final.end(), name
    return None


def spellings_of(items):
    """Each name item's spellings in Latin letters, from its labels and aliases in every language."""
    found, items = {}, sorted(items)
    for start in range(0, len(items), 200):
        values = " ".join(f"wd:{item}" for item in items[start:start + 200])
        rows = ask(f"spellings-{hashlib.sha1(values.encode()).hexdigest()[:12]}", f"""
            SELECT DISTINCT ?name ?spelling WHERE {{
              VALUES ?name {{ {values} }}
              {{ ?name rdfs:label ?spelling. }} UNION {{ ?name skos:altLabel ?spelling. }}
            }}""")
        for row in rows:
            spelling = written(row["spelling"])
            if spelling:
                found.setdefault(row["name"].rsplit("/", 1)[1], set()).add(spelling)
    return found


# A person's English label, or where it has none its label for every language written in Latin letters.
PERSON = """OPTIONAL { ?p rdfs:label ?en. FILTER(LANG(?en) = "en") }
    OPTIONAL { ?p rdfs:label ?mul. FILTER(LANG(?mul) = "mul") }
    BIND(COALESCE(?en, ?mul) AS ?person) FILTER(BOUND(?person))"""

# Keyed by (sex, decade), with decade None for all of them. A person's first given name counts: one
# without a place in the series of their names, or first in it. A query for each decade, as one for
# all of a country's people runs out of time.
given = {(sex, decade): Counter() for sex in SEXES for decade in [None, *DECADES]}
surnames = {sex: Counter() for sex in SEXES}

if options.get("spelled"):
    # A person's names as (sex, decade, person, label, name item), their given names counting as above.
    linked = {"given": [], "surnames": []}
    for sex, sex_item in SEXES.items():
        for decade in DECADES:
            for country, pattern in people().items():
                for row in ask(f"people-given-{sex}-{decade}-{country}", f"""
                        SELECT DISTINCT ?p ?person ?name WHERE {{
                          {among(pattern, sex_item, decade)}
                          ?p p:P735 ?statement. ?statement ps:P735 ?name.
                          FILTER NOT EXISTS {{ ?statement pq:P1545 ?place. FILTER(?place != "1") }}
                          {PERSON}
                        }}"""):
                    linked["given"].append((sex, decade, row["p"], row["person"], row["name"].rsplit("/", 1)[1]))
                for row in ask(f"people-surnames-{sex}-{decade}-{country}", f"""
                        SELECT DISTINCT ?p ?person ?name WHERE {{
                          {among(pattern, sex_item, decade)}
                          ?p wdt:P734 ?name. {PERSON}
                        }}"""):
                    linked["surnames"].append((sex, decade, row["p"], row["person"], row["name"].rsplit("/", 1)[1]))
    spellings = spellings_of({item for kind in linked.values() for *_, item in kind})
    # A person's given name is the one their label has first, and their surnames those it has, each
    # where it has it last; a name their label has in none of its spellings, often a link to the wrong
    # name, counts for no one.
    names = {}
    for kind, last in (("given", False), ("surnames", True)):
        for sex, decade, p, person, item in linked[kind]:
            found = spelled(person, spellings.get(item, ()), last)
            if found:
                start, end, name = found
                names.setdefault((kind, sex, p), {}).setdefault(decade, set()).add((start, start - end, name))
    for (kind, sex, p), decades in names.items():
        if kind == "given":
            # A person with two dates of birth counts in each decade but once in all of them.
            for decade, found in decades.items():
                given[(sex, decade)][min(found)[2]] += 1
            given[(sex, None)][min(min(found) for found in decades.values())[2]] += 1
        else:
            for name in {name for found in decades.values() for *_, name in found}:
                surnames[sex][name] += 1

for sex, sex_item in SEXES.items() if not options.get("spelled") else ():
    for decade in DECADES:
        for country, pattern in people().items():
            born = f"""{pattern} ?p wdt:P21 wd:{sex_item}; wdt:P569 ?born.
                FILTER(?born >= "{decade}-01-01T00:00:00Z"^^xsd:dateTime && ?born < "{decade + 10}-01-01T00:00:00Z"^^xsd:dateTime)"""
            rows = ask(f"given-{sex}-{decade}-{country}", f"""
                SELECT ?label (COUNT(DISTINCT ?p) AS ?count) WHERE {{
                  {born}
                  ?p p:P735 ?statement. ?statement ps:P735 ?name.
                  FILTER NOT EXISTS {{ ?statement pq:P1545 ?place. FILTER(?place != "1") }}
                  {LABEL}
                }} GROUP BY ?label""")
            for row in rows:
                name = written(row["label"])
                if name:
                    given[(sex, decade)][name] += int(row["count"])
                    given[(sex, None)][name] += int(row["count"])
            rows = ask(f"surnames-{sex}-{decade}-{country}", f"""
                SELECT ?label (COUNT(DISTINCT ?p) AS ?count) WHERE {{
                  {born}
                  ?p wdt:P734 ?name. {LABEL}
                }} GROUP BY ?label""")
            for row in rows:
                name = written(row["label"])
                if name:
                    surnames[sex][name] += int(row["count"])

for (sex, decade), counts in given.items():
    other = given[("masculine" if sex == "feminine" else "feminine", decade)]
    counts = {name: count for name, count in counts.items() if other[name] <= OTHER_SEX * count}
    write(f"{sex}-{decade}" if decade else sex, counts, GIVEN, bool(decade))

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
