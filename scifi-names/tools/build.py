"""Builds resources/worlds/names.txt: the names of stars and moons, a name on each line, for made-up
worlds' names to sound like; see resources/NOTICE.txt. Fetches the IAU's catalogue of star names
and asks Wikidata for the moons, and keeps both in the working directory, so a second run fetches
only what is missing.

    python3 build.py <working directory> <resources directory>
"""
import json
import os
import sys
import urllib.parse
import urllib.request

AGENT = {"User-Agent": "hammer-plugins scifi-names/tools/build.py"}
STARS = "https://www.pas.rochester.edu/~emamajek/WGSN/IAU-CSN.txt"
MOONS = 'SELECT DISTINCT ?label WHERE { ?x wdt:P31/wdt:P279* wd:Q2537; rdfs:label ?label. FILTER(LANG(?label) = "en") }'

work, out = sys.argv[1], sys.argv[2]
os.makedirs(work, exist_ok=True)


def fetch(file, url):
    path = os.path.join(work, file)
    if not os.path.exists(path):
        with urllib.request.urlopen(urllib.request.Request(url, headers=AGENT), timeout=120) as reply:
            answer = reply.read()
        with open(path, "wb") as f:
            f.write(answer)
    with open(path, encoding="utf-8") as f:
        return f.read()


# A star's name in plain letters is its line's first column.
names = {line.split()[0] for line in fetch("IAU-CSN.txt", STARS).splitlines() if line and line[0] not in "#$"}
moons = fetch("moons.json", "https://query.wikidata.org/sparql?" + urllib.parse.urlencode({"query": MOONS, "format": "json"}))
names.update(row["label"]["value"] for row in json.loads(moons)["results"]["bindings"])
# One word of plain letters: not a moon known only by its designation, such as S/2003 J 2.
names = sorted(name for name in names if len(name) >= 3 and name.isascii() and name.isalpha() and name[0].isupper() and name[1:].islower())
with open(os.path.join(out, "worlds", "names.txt"), "w", encoding="utf-8") as f:
    f.writelines(name + "\n" for name in names)
print(len(names))
