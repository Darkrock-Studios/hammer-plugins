"""Builds resources/styles/<style>/ for every style: the real names each style's made-up names take
after, a name on each line, and everyday.txt, today's names, which no style has or makes up; see
resources/styles/NOTICE.txt. Asks Wiktionary and Wikidata, and keeps
each answer in the working directory, so a second run asks only for what is missing.

    python3 build.py <working directory> <resources/styles directory>
"""
import json
import os
import re
import sys
import time
import unicodedata
import urllib.parse
import urllib.request

AGENT = {"User-Agent": "hammer-plugins fantasy-names/tools/build.py"}
SEXES = {"feminine": ("female", "Q11879590"), "masculine": ("male", "Q12308941")}
# What Finnish has only in borrowed names: some letters, a g without its n, two consonants at the
# start, and some endings.
BORROWED = re.compile("[bcfqwxzå]|(?<!n)g|^[^aeiouyäö]{2}|(nen|us|ias|es|rd|ks)$")
# The parts Norse names are made of. An Icelandic name starts or ends with one, unless borrowed.
NORSE = re.compile(
    "^(adal|alf|arn|as|aud|berg|bjorn|bjart|bod|bryn|dag|egg|ein|eir|ey|finn|frey|frid|geir|gest|gisl|grim|gud|gunn|haf|hall|heid|helg|her|"
    "hild|hjalm|hjor|hrafn|hreid|hrod|hrolf|ing|jar|jor|ketil|kol|ljot|magn|odd|orm|ragn|rann|run|sae|sig|snae|sol|stein|svan|svein|thjod|"
    "thor|ulf|unn|val|ve|vig|vil|yngv)"
    "|(bjorg|bjorn|bjort|brand|dis|dor|finn|frid|geir|gerd|grim|gunn|heid|hild|kel|laug|leif|ljot|mar|mod|mund|ny|rid|rik|run|stein|thor|"
    "thrud|ulf|unn|vald|vard|veig|vid|vor|ar|ir|i|a)$"
)
IMPORTED = re.compile("(ina|ia|ea|us|as|es|ons|el|an)$|[cqwz]")

# For each style: "wiktionary", the languages whose given names it takes; "wikidata", the language
# whose given names it takes from there too; "letters", what to write for letters English lacks;
# "norse", whether to take the nominative endings off as English does, Haraldr to Harald; "keep",
# which names to keep; and "bynames", the language whose surnames it takes for bynames.
STYLES = {
    "elvish": {"wiktionary": ["Welsh"], "wikidata": "Q9309"},
    "high-elvish": {
        "wiktionary": ["Finnish"], "wikidata": "Q1412", "bynames": "Q1412",
        "keep": lambda name, language: not BORROWED.search(name.lower()),
    },
    "dwarvish": {
        "wiktionary": ["Old Norse", "Icelandic"], "norse": True,
        "keep": lambda name, language: language != "Icelandic" or bool(NORSE.search(name.lower())) and not IMPORTED.search(name.lower()),
        "letters": {"þ": "th", "ð": "d", "ǫ": "o", "ö": "o", "ø": "o", "æ": "ae", "œ": "oe"},
    },
    "old-kingdom": {
        "wiktionary": ["Old English"],
        "letters": {"þ": "th", "ð": "th", "æ": "ae", "ƿ": "w"},
    },
}

work, out = sys.argv[1], sys.argv[2]
os.makedirs(work, exist_ok=True)
tools = os.path.dirname(os.path.abspath(__file__))


def fetch(name, url):
    """A URL's JSON answer, kept in the working directory under `name`."""
    path = os.path.join(work, f"{name}.json")
    if not os.path.exists(path):
        for attempt in range(5):
            try:
                with urllib.request.urlopen(urllib.request.Request(url, headers=AGENT), timeout=120) as reply:
                    answer = json.load(reply)
                break
            except (OSError, ValueError):
                # Too many requests, or an answer cut short.
                if attempt == 4:
                    raise
                time.sleep(30 * (attempt + 1))
        with open(path, "w", encoding="utf-8") as f:
            json.dump(answer, f, ensure_ascii=False)
        time.sleep(5)
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def category(title):
    """The pages of a Wiktionary category."""
    name = "wiktionary-" + title.replace(" ", "_")
    path = os.path.join(work, f"{name}.json")
    if not os.path.exists(path):
        pages, more, part = [], {}, 0
        while more is not None:
            query = {"action": "query", "list": "categorymembers", "cmtitle": "Category:" + title, "cmlimit": "500", "cmnamespace": "0", "format": "json", **more}
            answer = fetch(f"{name}-{part}", "https://en.wiktionary.org/w/api.php?" + urllib.parse.urlencode(query))
            pages += [page["title"] for page in answer["query"]["categorymembers"]]
            more, part = answer.get("continue"), part + 1
        with open(path, "w", encoding="utf-8") as f:
            json.dump(pages, f, ensure_ascii=False)
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def wikidata(name, query):
    """The labels a Wikidata query answers with."""
    url = "https://query.wikidata.org/sparql?" + urllib.parse.urlencode({"query": query, "format": "json"})
    return [row["label"]["value"] for row in fetch("wikidata-" + name, url)["results"]["bindings"]]


def modern(*files):
    """The names, in lower case, of real-names' lists of today's American names."""
    names = set()
    for file in files:
        with open(os.path.join(tools, "../../real-names/resources/names/american", file), encoding="utf-8") as f:
            names.update(line.split("\t")[0].lower() for line in f)
    return names


def labels(name, query):
    return [(label, None) for label in wikidata(name, query)]


EVERYDAY = modern("feminine.txt", "masculine.txt")


def written(name, style):
    """A name as the style's list has it, or None for one that is not a single word in Latin letters."""
    name = unicodedata.normalize("NFC", name.strip())
    if len(name) < 3 or not name[0].isupper() or not name.isalpha() or not all("LATIN" in unicodedata.name(c, "") for c in name):
        return None
    letters = style.get("letters")
    if letters:
        name = "".join(letters.get(c, c) for c in name.lower())
        # The marks on the rest of its letters go too.
        name = "".join(c for c in unicodedata.normalize("NFD", name) if not unicodedata.combining(c)).capitalize()
    if style.get("norse"):
        name = re.sub(r"(?<=[^aeiouy])ur$", "", name)
        name = re.sub(r"rr$", "r", name)
        name = re.sub(r"(?<=[^aeiouyr])r$", "", name)
        name = re.sub(r"([nl])\1$", r"\1", name)
    return name if len(name) >= 3 else None


def write(directory, file, names, style):
    """Writes a list of `names`, each a name and the language it is from."""
    keep = style.get("keep", lambda name, language: True)
    names = ((written(name, style), language) for name, language in names)
    names = sorted({name for name, language in names if name and name.lower() not in EVERYDAY and keep(name, language)})
    os.makedirs(directory, exist_ok=True)
    with open(os.path.join(directory, f"{file}.txt"), "w", encoding="utf-8") as f:
        f.writelines(name + "\n" for name in names)
    print(os.path.basename(directory), file, len(names))


with open(os.path.join(out, "everyday.txt"), "w", encoding="utf-8") as f:
    f.writelines(name + "\n" for name in sorted(EVERYDAY))

for key, style in STYLES.items():
    directory = os.path.join(out, key)
    for kind, (sex, item) in SEXES.items():
        names = [(name, language) for language in style["wiktionary"] for name in category(f"{language} {sex} given names")]
        if "wikidata" in style:
            names += labels(f"{key}-{kind}", f"""
                SELECT DISTINCT ?label WHERE {{
                  ?name wdt:P31 wd:{item}; wdt:P407 wd:{style["wikidata"]}; rdfs:label ?label. FILTER(LANG(?label) = "en")
                }}""")
        write(directory, kind, names, style)
    if "bynames" in style:
        write(directory, "bynames", labels(f"{key}-bynames", f"""
            SELECT DISTINCT ?label WHERE {{
              ?name wdt:P31 wd:Q101352; wdt:P407 wd:{style["bynames"]}; rdfs:label ?label. FILTER(LANG(?label) = "en")
            }}"""), style)
