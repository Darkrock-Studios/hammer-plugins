"""Builds surnames in resources/names/korean.txt: the surnames of South Korea's population in the 2015
census, each with how many have it, spelled as Koreans write them in English; see
resources/names/NOTICE.txt. Run it after wikidata.py, whose given names it leaves. Reads, from the
working directory, Statistics Korea's table of population by surname and clan seat, 5 or more people,
nationwide, viewed by surname and downloaded by hand from KOSIS as xlsx:

    kosis-DT_1IN15SD-view.xlsx    https://kosis.kr/statHtml/statHtml.do?orgId=101&tblId=DT_1IN15SD

The table names each surname in hangul and hanja, such as 강(姜) and 강(康); those of the same hangul
are one surname in English. Each is spelled as more of South Korea's people on Wikidata with that
surname spell it in their English labels than any other way, where 2 or more do, and in the Revised
Romanization of Korean otherwise; surnames spelled alike, such as 이 and 리 (Lee), are counted
together. Asks https://query.wikidata.org once, and keeps the answer in the working directory.

    python3 korean.py <working directory> <resources/names/korean.txt>
"""
import json
import os
import re
import sys
import time
import urllib.parse
import urllib.request
from collections import Counter, defaultdict

import lists
from xlsx import sheets

SURNAMES = 5000
# A Wikidata spelling needs this many people, and more than any other spelling has.
FEWEST = 2
# South Korea's people with a family name, their English and Korean labels, and their family name's
# Korean label.
QUERY = """SELECT ?p ?en ?pko ?ko WHERE {
  ?p wdt:P27 wd:Q884; wdt:P31 wd:Q5; wdt:P734 ?name.
  ?name rdfs:label ?ko. FILTER(LANG(?ko) = "ko")
  ?p rdfs:label ?en. FILTER(LANG(?en) = "en")
  ?p rdfs:label ?pko. FILTER(LANG(?pko) = "ko")
}"""

# Revised Romanization's letters for a syllable's initial, vowel, and final, in Unicode's order.
INITIALS = ["g", "kk", "n", "d", "tt", "r", "m", "b", "pp", "s", "ss", "", "j", "jj", "ch", "k", "t", "p", "h"]
VOWELS = ["a", "ae", "ya", "yae", "eo", "e", "yeo", "ye", "o", "wa", "wae", "oe", "yo", "u", "wo", "we", "wi",
          "yu", "eu", "ui", "i"]
FINALS = ["", "k", "k", "k", "n", "n", "n", "t", "l", "k", "m", "l", "l", "l", "p", "l", "m", "p", "p", "t", "t",
          "ng", "t", "t", "k", "t", "p", "t"]
# The letters an English spelling may start with, by the surname's first initial. South Korea writes
# a surname's first ㄹ as ㄴ or ㅇ (李 as 이, 盧 as 노), which English spellings often keep (Lee,
# Roh), and one still written ㄹ may be spelled without it (류 as Yoo).
STARTS = ["KG", "KG", "NRL", "DT", "DT", "RLNYI", "M", "BP", "PB", "S", "S", "AEIOUYWLR", "JCZ", "JCZ", "CJ",
          "K", "T", "P", "HW"]

work, out = sys.argv[1], sys.argv[2]
os.makedirs(work, exist_ok=True)


def syllable(c):
    """The initial, vowel, and final of a hangul syllable, as indexes."""
    code = ord(c) - 0xAC00
    return code // 588, code // 28 % 21, code % 28


def revised(hangul):
    """A surname in the Revised Romanization, its syllables run together, as in Namgung."""
    return "".join(INITIALS[i] + VOWELS[v] + FINALS[f] for i, v, f in map(syllable, hangul)).capitalize()


def census():
    """Each surname's count, by its hangul, the table's rows of the same hangul added together."""
    counts = Counter()
    for name, rows in sheets(os.path.join(work, "kosis-DT_1IN15SD-view.xlsx")):
        if name == "데이터":
            for row in rows:
                # A surname row is "강(姜)" and a count, which may be written as 1.0689959E7; 기타 is the rest.
                match = re.fullmatch(r"([가-힣]+)\(.+\)", row[0]) if len(row) >= 2 else None
                if match and match[1] != "기타":
                    counts[match[1]] += int(float(row[1]))
    return counts


def ask():
    """The rows of the Wikidata query's answer, kept in the working directory."""
    path = os.path.join(work, "wikidata-people.json")
    if not os.path.exists(path):
        url = "https://query.wikidata.org/sparql?" + urllib.parse.urlencode({"query": QUERY, "format": "json"})
        request = urllib.request.Request(url, headers={"User-Agent": "hammer-plugins real-names/tools/korean.py"})
        for attempt in range(4):
            try:
                with urllib.request.urlopen(request, timeout=300) as reply:
                    answer = reply.read()
                json.loads(answer)
                break
            except (OSError, ValueError):
                if attempt == 3:
                    raise
                time.sleep(30 * (attempt + 1))
        with open(path, "wb") as f:
            f.write(answer)
    with open(path, encoding="utf-8") as f:
        return [{key: value["value"] for key, value in row.items()} for row in json.load(f)["results"]["bindings"]]


def candidates(label, hangul):
    """The words of an English label that could be its surname: the first or the last, as a label may
    put the surname either end, and not a hyphened given name such as Ji-sung."""
    words = label.split()
    if len(words) < 2:
        return set()
    starts = STARTS[syllable(hangul[0])[0]]
    return {word[0].upper() + word[1:].lower() for word in (words[0], words[-1])
            if re.fullmatch(r"[A-Za-z]+", word) and word[0].upper() in starts}


def spellings():
    """For each hangul surname, how many of South Korea's people on Wikidata spell it each way."""
    people = defaultdict(set)
    for row in ask():
        # Some family names' Korean labels end in 씨, "clan", as 부씨.
        hangul = re.sub(r"(?<=.)씨$", "", row["ko"])
        # Leaves out stage names and family names linked in error, whose labels start otherwise.
        if re.fullmatch(r"[가-힣]+", hangul) and row["pko"].startswith(hangul):
            people[row["p"]].add((hangul, row["en"]))
    labels = defaultdict(list)
    for each in people.values():
        # Of two family names, such as 남 and 남궁, the longer.
        hangul, label = max(each, key=lambda pair: len(pair[0]))
        labels[hangul].append(label)
    spelled = {}
    for hangul, each in labels.items():
        # A surname's spelling is the same across its people and their given names differ, so of a
        # label's two ends the surname is the one more of them have.
        ends = Counter(word for label in each for word in candidates(label, hangul))
        spelled[hangul] = Counter(max(sorted(words), key=lambda word: ends[word])
                                  for words in (candidates(label, hangul) for label in each) if words)
    return spelled


def english(hangul, spelled):
    """A surname's usual English spelling."""
    ranked = spelled.get(hangul, Counter()).most_common(2) + [(None, 0)] * 2
    (first, most), (_, next_most) = ranked[0], ranked[1]
    return first if most >= FEWEST and most > next_most else revised(hangul)


spelled = spellings()
counts, sources = Counter(), defaultdict(list)
for hangul, count in census().items():
    name = english(hangul, spelled)
    counts[name] += count
    sources[name].append(hangul)
for name, each in sorted(sources.items()):
    if len(each) > 1:
        print(name, "is", " and ".join(sorted(each)))
lists.write(out, "surnames", counts.items(), SURNAMES)
