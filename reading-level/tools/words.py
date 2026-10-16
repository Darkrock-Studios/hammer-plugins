"""Builds resources/words/<lang>.txt: each language's common words, most frequent first, a word on
each line; see resources/NOTICE.txt. A word outside its language's list counts as rare. Fetches each
language's 50,000 most frequent words from FrequencyWords, and keeps them in the working directory, so
a second run fetches only what is missing.

English keeps its 20,000 most frequent words: subtitles are speech, which seldom uses narration's past
tenses ("swam", "whispered"), and those rank between 10,000 and 20,000. The others keep as many as
cover the same share of their list's occurrences, since a language that inflects more spreads one
word over more forms.

    python3 words.py <working directory> <resources directory>
"""
import os
import sys
import urllib.request

AGENT = {"User-Agent": "hammer-plugins reading-level/tools/words.py"}
URL = "https://raw.githubusercontent.com/hermitdave/FrequencyWords/master/content/2018/{0}/{0}_50k.txt"
LANGUAGES = ["en", "fr", "de", "es", "it", "uk"]
ENGLISH_SIZE = 20_000

work, out = sys.argv[1], sys.argv[2]
os.makedirs(work, exist_ok=True)


def fetch(lang):
    path = os.path.join(work, f"{lang}_50k.txt")
    if not os.path.exists(path):
        with urllib.request.urlopen(urllib.request.Request(URL.format(lang), headers=AGENT), timeout=120) as reply:
            answer = reply.read()
        with open(path, "wb") as f:
            f.write(answer)
    words = []
    with open(path, encoding="utf-8") as f:
        for line in f:
            word, count = line.split()
            words.append((word, int(count)))
    return words


def coverage(words, size):
    return sum(count for _, count in words[:size]) / sum(count for _, count in words)


share = coverage(fetch("en"), ENGLISH_SIZE)
for lang in LANGUAGES:
    words = fetch(lang)
    total, seen, size = sum(count for _, count in words), 0, 0
    while size < len(words) and seen < share * total:
        seen += words[size][1]
        size += 1
    # Clitics such as English "'s" are left out: the plugin looks up a word's longest part between
    # apostrophes, as the lists split "don't" into "don" and "'t".
    kept = [word for word, _ in words[:size] if word.isalpha()]
    os.makedirs(os.path.join(out, "words"), exist_ok=True)
    with open(os.path.join(out, "words", f"{lang}.txt"), "w", encoding="utf-8") as f:
        f.write(f"# The {size:,} most frequent words of OpenSubtitles 2018, from FrequencyWords; made by tools/words.py.\n")
        f.writelines(word + "\n" for word in kept)
    print(lang, size, len(kept))
