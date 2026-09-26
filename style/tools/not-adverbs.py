"""Builds resources/<lang>/not-adverbs.txt for French, Spanish, and Italian: the frequent words ending
in the language's adverb suffix ("-ment", "-mente") that are not adverbs, such as "moment" and
"comente". Needs spylls (a Python Hunspell), importable or as spylls.whl in the working directory,
and there each language's frequency list and LibreOffice dictionary, as name-checker's
tools/word-lists.py describes.

    python3 not-adverbs.py <working directory> <resources directory>
"""
import os
import sys

TOP = 100_000
LANGUAGES = {"fr": ("fr/fr", "ment"), "es": ("es/es_ES", "mente"), "it": ("it/it_IT", "mente")}
# Italian builds adverbs from adjectives with an affix; French and Spanish list each as its own entry,
# and their affixes ending in the suffix make verb forms, such as "dorment".
BUILT = {"it"}
# Words the rules above take for adverbs, or cannot see, that a report should not count.
EXTRA = {"fr": ["comment", "ment"], "es": ["mente"], "it": ["mente"]}
# Flags an adverb's own entry may carry: French's elided "d'", "l'", and "qu'" before it.
ADVERB_FLAGS = {"D'", "L'", "Q'"}

work, out = sys.argv[1], sys.argv[2]
os.chdir(work)
sys.path.insert(0, "spylls.whl")
from spylls.hunspell import Dictionary  # noqa: E402


def adverb(dictionary, word, suffix, built):
    """Whether the dictionary has `word` as an adverb: an invariable entry of its own, an adjective
    with the suffix that makes adverbs ("veloce" to "velocemente"), or, for "-mente", a known word of
    three letters or more before it ("ligera" in "ligeramente"), which a verb form such as "comente"
    lacks."""
    stem = word[: -len(suffix)]
    if suffix == "mente" and len(stem) >= 3 and dictionary.lookup(stem):
        return True
    for form in dictionary.lookuper.good_forms(word):
        entry = getattr(form, "in_dictionary", None)
        affix = getattr(form, "suffix", None)
        if built and affix is not None and affix.add.endswith(suffix):
            return True
        if entry is not None and affix is None and str(entry.stem) == word and set(entry.flags) <= ADVERB_FLAGS:
            return True
    return False


for lang, (prefix, suffix) in LANGUAGES.items():
    dictionary = Dictionary.from_files(prefix)
    found = []
    with open(f"{lang}/{lang}_full.txt", encoding="utf-8") as f:
        for n, line in enumerate(f):
            if n >= TOP:
                break
            word = line.split(" ")[0]
            if not word.endswith(suffix) or len(word) <= len(suffix) or not word.isalpha() or word != word.lower():
                continue
            if dictionary.lookup(word) and not adverb(dictionary, word, suffix, lang in BUILT):
                found.append(word)
    found += EXTRA[lang]
    with open(os.path.join(out, lang, "not-adverbs.txt"), "w", encoding="utf-8") as f:
        f.write(f"# Words ending in \"{suffix}\" that are not adverbs, made by tools/not-adverbs.py.\n")
        f.write("\n".join(sorted(set(found))) + "\n")
    print(lang, len(set(found)))
