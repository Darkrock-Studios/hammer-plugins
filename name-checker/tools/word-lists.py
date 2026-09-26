"""Builds resources/words/<lang>.txt for French, German, Spanish, Italian, and Ukrainian; see
resources/words/NOTICE.txt. Needs spylls (a Python Hunspell), importable or as spylls.whl in the
working directory, and, in the working directory, each language's frequency list and dictionary:

    <lang>/<lang>_full.txt   https://github.com/hermitdave/FrequencyWords content/2018/<lang>/
    fr/fr.dic, fr.aff        https://github.com/LibreOffice/dictionaries fr_FR/dictionaries/
    de/de_DE_frami.*         the same, de/
    es/es_ES.*               es/
    it/it_IT.*               it_IT/
    uk/uk_UA.*               uk_UA/

and en_US.dic and en_US.aff in /usr/share/hunspell, for telling German nouns from names.

    python3 word-lists.py <working directory> <resources/words directory>
"""
import os
import re
import sys

TOP = 200_000
LANGUAGES = {"fr": "fr/fr", "de": "de/de_DE_frami", "es": "es/es_ES", "it": "it/it_IT", "uk": "uk/uk_UA"}

work, out = sys.argv[1], sys.argv[2]
os.chdir(work)
sys.path.insert(0, "spylls.whl")
from spylls.hunspell import Dictionary  # noqa: E402

dictionaries = {lang: Dictionary.from_files(prefix) for lang, prefix in LANGUAGES.items()}
english = Dictionary.from_files("/usr/share/hunspell/en_US")

# The German dictionary's flags a name's entry has: a genitive "s" and case endings. A noun with a
# plural has others; one without, such as "Zukunft", has only these, as a name does.
NAME_FLAGS = {"S", "M", "m"}


def german_noun(word):
    """Whether the German dictionary has capitalized `word` as a noun rather than a name: its entry has
    a noun's flags, or no other language's dictionary knows it only capitalized, as a name."""
    d = dictionaries["de"]
    if not d.lookup(word):
        return False
    # A form with no entry of its own is a compound, which names are not.
    if any(not getattr(f, "in_dictionary", None) or set(f.in_dictionary.flags) - NAME_FLAGS
           for f in d.lookuper.good_forms(word)):
        return True
    others = [dictionaries[lang] for lang in ("fr", "es", "it")] + [english]
    return not any(o.lookup(word) and not o.lookup(word.lower()) for o in others)


def known(word, lang):
    if dictionaries[lang].lookup(word):
        return True
    return lang == "de" and german_noun(word[:1].upper() + word[1:])


for lang in LANGUAGES:
    words = []
    with open(f"{lang}/{lang}_full.txt", encoding="utf-8") as f:
        for n, line in enumerate(f):
            if n >= TOP:
                break
            word = line.split(" ")[0].strip()
            if word and word == word.lower() and any(c.isalpha() for c in word):
                words.append(word)
    kept = {w for w in words if known(w, lang)}
    # The Ukrainian dictionary accepts any word not in Cyrillic.
    if lang == "uk":
        kept = {w for w in kept if not re.search(r"[A-Za-zÀ-ɏ]", w)}
    with open(os.path.join(out, f"{lang}.txt"), "w", encoding="utf-8") as f:
        f.write("\n".join(sorted(kept, key=lambda w: w.encode())) + "\n")
    print(lang, len(kept))
