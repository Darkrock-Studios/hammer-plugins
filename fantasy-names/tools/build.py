"""Builds resources/styles/<style>/ for every style but those written by hand: the real names each style's made-up names take
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
FEMININE, MASCULINE = "Q11879590", "Q12308941"
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
# The parts Slavic names are made of, which one starts with unless borrowed, and the endings of pet names.
SLAVIC = re.compile(
    "^(bog|boh|bol|bor|boz|bran|brat|bron|cest|cast|dal|dobr|drag|drah|draz|gost|host|jar|kaz|kras|lad|ljub|lub|lud|mil|mir|mlad|mst|mysl|"
    "ned|neg|prib|pred|rad|rat|rost|slav|sob|stan|stoj|sud|svat|svet|tih|tech|tvrd|vac|vec|vel|ven|vit|vlad|vlast|voj|vrat|vuk|zbys|zdes|"
    "zdrav|zel|ziv|zlat|zor|zvon)"
)
PET = re.compile("(ek|ik|us|os|as|ys|es|in|ic|oj|sz|rz|[aeiouy])$")
SLAVS = ("Czech", "Slovak", "Serbo-Croatian", "Polish", "Slovene")


def wiktionary(title, tag=None, change=None):
    return {"wiktionary": title, "tag": tag, "change": change}


def wikidata(where, tag=None):
    return {"wikidata": where, "tag": tag, "change": None}


def named(kind, language):
    """Wikidata's given names of a kind, or family names, of a language."""
    return wikidata(f"?x wdt:P31 wd:{kind}; wdt:P407 wd:{language}.")


# For each style, the lists it has, each with where its names are from: a Wiktionary category, or
# what Wikidata's ?x is, with a tag for "keep" to tell them by and a change to make to each name. And
# "letters", what to write for letters English lacks, the marks on the rest going too; "plain",
# whether the marks go anyway; "norse", whether to take the nominative endings off as English does,
# Haraldr to Harald; and "keep", which names to keep, given the name, its source's tag, and its list.
STYLES = {
    "elvish": {
        "feminine": [wiktionary("Welsh female given names"), named(FEMININE, "Q9309")],
        "masculine": [wiktionary("Welsh male given names"), named(MASCULINE, "Q9309")],
    },
    "high-elvish": {
        "feminine": [wiktionary("Finnish female given names"), named(FEMININE, "Q1412")],
        "masculine": [wiktionary("Finnish male given names"), named(MASCULINE, "Q1412")],
        "bynames": [named("Q101352", "Q1412")],
        "keep": lambda name, tag, file: not BORROWED.search(name.lower()),
    },
    "dwarvish": {
        "feminine": [wiktionary("Old Norse female given names"), wiktionary("Icelandic female given names", "Icelandic")],
        "masculine": [wiktionary("Old Norse male given names"), wiktionary("Icelandic male given names", "Icelandic")],
        "norse": True,
        "letters": {"þ": "th", "ð": "d", "ǫ": "o", "ö": "o", "ø": "o", "æ": "ae", "œ": "oe"},
        "keep": lambda name, tag, file: tag != "Icelandic" or bool(NORSE.search(name.lower())) and not IMPORTED.search(name.lower()),
    },
    "old-kingdom": {
        "feminine": [wiktionary("Old English female given names")],
        "masculine": [wiktionary("Old English male given names")],
        "letters": {"þ": "th", "ð": "th", "æ": "ae", "ƿ": "w"},
    },
    # A Roman woman had her family's name, with a woman's ending.
    "imperial": {
        "feminine": [wiktionary("Latin nomina gentilia", change=lambda name: re.sub("ius$", "ia", name))],
        "masculine": [wiktionary("Latin nomina gentilia"), wiktionary("Latin praenomina")],
        "bynames": [wiktionary("Latin cognomina")],
        "keep": lambda name, tag, file: file == "bynames" or name.endswith("ia" if file == "feminine" else "us"),
    },
    # Those of Greek myth, and the cities of ancient Greece.
    "mythic": {
        "feminine": [wikidata("?x wdt:P31 wd:Q22988604; wdt:P21 wd:Q6581072.")],
        "masculine": [wikidata("?x wdt:P31 wd:Q22988604; wdt:P21 wd:Q6581097.")],
        "places": [wikidata("?x wdt:P31 wd:Q148837.")],
        "plain": True,
    },
    "highland": {
        "feminine": [wiktionary(f"{language} female given names") for language in ("Old Irish", "Middle Irish", "Irish", "Scottish Gaelic")] + [named(FEMININE, "Q9142")],
        "masculine": [wiktionary(f"{language} male given names") for language in ("Old Irish", "Middle Irish", "Irish", "Scottish Gaelic")] + [named(MASCULINE, "Q9142")],
        "plain": True,
    },
    # A man's name ends in a consonant, for "ovic" to follow.
    "forest": {
        "feminine": [wiktionary(f"{language} female given names") for language in SLAVS],
        "masculine": [wiktionary(f"{language} male given names") for language in SLAVS],
        "letters": {"đ": "dj", "ł": "l", "w": "v"},
        "keep": lambda name, tag, file: bool(SLAVIC.search(name.lower())) and len(name) >= 5
        and (name.endswith("a") and not re.search("(ka|ca|da|ina)$", name) if file == "feminine" else not PET.search(name)),
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


def labels(name, where):
    """The English labels of what `where` says Wikidata's ?x is."""
    query = f'SELECT DISTINCT ?label WHERE {{ {where} ?x rdfs:label ?label. FILTER(LANG(?label) = "en") }}'
    url = "https://query.wikidata.org/sparql?" + urllib.parse.urlencode({"query": query, "format": "json"})
    return [row["label"]["value"] for row in fetch("wikidata-" + name, url)["results"]["bindings"]]


def modern(*files):
    """The names, in lower case, of real-names' lists of today's American names."""
    names = set()
    for file in files:
        with open(os.path.join(tools, "../../real-names/resources/names/american", file), encoding="utf-8") as f:
            names.update(line.split("\t")[0].lower() for line in f)
    return names


EVERYDAY = modern("feminine.txt", "masculine.txt")


def written(name, style):
    """A name as the style's list has it, or None for one that is not a single word in Latin letters."""
    name = unicodedata.normalize("NFC", name.strip())
    if len(name) < 3 or not name[0].isupper() or not name.isalpha() or not all("LATIN" in unicodedata.name(c, "") for c in name):
        return None
    letters = style.get("letters", {})
    if letters or style.get("plain"):
        name = "".join(letters.get(c, c) for c in name.lower())
        name = "".join(c for c in unicodedata.normalize("NFD", name) if not unicodedata.combining(c)).capitalize()
    if style.get("norse"):
        name = re.sub(r"(?<=[^aeiouy])ur$", "", name)
        name = re.sub(r"rr$", "r", name)
        name = re.sub(r"(?<=[^aeiouyr])r$", "", name)
        name = re.sub(r"([nl])\1$", r"\1", name)
    return name if len(name) >= 3 else None


def write(directory, file, sources, style):
    keep = style.get("keep", lambda name, tag, file: True)
    names = set()
    for source in sources:
        found = category(source["wiktionary"]) if "wiktionary" in source else labels(f"{os.path.basename(directory)}-{file}", source["wikidata"])
        for name in found:
            name = written(source["change"](name) if source["change"] else name, style)
            if name and name.lower() not in EVERYDAY and keep(name, source["tag"], file):
                names.add(name)
    os.makedirs(directory, exist_ok=True)
    with open(os.path.join(directory, f"{file}.txt"), "w", encoding="utf-8") as f:
        f.writelines(name + "\n" for name in sorted(names))
    print(os.path.basename(directory), file, len(names))


with open(os.path.join(out, "everyday.txt"), "w", encoding="utf-8") as f:
    f.writelines(name + "\n" for name in sorted(EVERYDAY))

for key, style in STYLES.items():
    for file, sources in style.items():
        if isinstance(sources, list):
            write(os.path.join(out, key), file, sources, style)
