"""Builds resources/names/chinese/: the most common given names by sex, over all years and for each
cohort of birth, and the most common surnames, each with its count, in pinyin; see
resources/names/NOTICE.txt, as its source's terms cover these lists. Needs, in the working directory,
from the ChineseNames package's data-csv/, https://github.com/psychbruce/ChineseNames:

    givenname.csv            the characters of given names, with their pinyin
    top1000name.prov.csv     the 1,000 most common given names
    top100name.year.csv      the 100 most common given names of each sex in each cohort
    familyname.csv           surnames

    python3 chinese.py <working directory> <resources/names/chinese directory>
"""
import csv
import os
import re
import sys
from collections import Counter

# A given name this many times as common for the other sex is left out.
OTHER_SEX = 20

# The pinyin of surnames read otherwise than as the given names' character table has them.
SURNAMES = {"曾": "zeng", "覃": "qin", "单": "shan", "解": "xie", "缪": "miao", "卜": "bu", "仇": "qiu", "查": "zha",
            "乐": "yue", "盖": "ge", "朴": "piao", "区": "ou", "都": "du", "薄": "bo", "宿": "su"}

work, out = sys.argv[1], sys.argv[2]
os.makedirs(out, exist_ok=True)


def table(name):
    with open(os.path.join(work, f"{name}.csv"), encoding="utf-8-sig") as f:
        yield from csv.DictReader(f)


# Each character's pinyin without its tone's number, "v" being how the table writes "ü".
pinyin = {row["character"]: re.sub(r"\d", "", row["pinyin"]).replace("v", "ü") for row in table("givenname")}


def written(name, readings={}):
    """`name` in pinyin as English writes it, or None when the table lacks one of its characters."""
    syllables = [readings.get(character) or pinyin.get(character) for character in name]
    if not all(syllables):
        return None
    # An apostrophe keeps a syllable that starts with a vowel apart from the one before it.
    joined = syllables[0] + "".join(("'" if syllable[0] in "aeo" else "") + syllable for syllable in syllables[1:])
    return joined[0].upper() + joined[1:]


def write(file, counts):
    ranked = sorted(counts.items(), key=lambda item: (-item[1], item[0]))
    with open(os.path.join(out, f"{file}.txt"), "w", encoding="utf-8") as f:
        f.writelines(f"{name}\t{count}\n" for name, count in ranked)
    print(file, len(ranked))


# Names of different characters and the same pinyin count as one.
given = {"feminine": Counter(), "masculine": Counter()}
for row in table("top1000name.prov"):
    name = written(row["name"])
    if name:
        given["feminine"][name] += int(row["n.female"])
        given["masculine"][name] += int(row["n.male"])
for kind, other in (("feminine", "masculine"), ("masculine", "feminine")):
    write(kind, {name: count for name, count in given[kind].items() if count and given[other][name] <= OTHER_SEX * count})

# The cohorts are those born from 1930 to 1959, under 1950, then each decade to 2000 to 2008.
for kind, sex in (("feminine", "f"), ("masculine", "m")):
    for decade in range(1950, 2010, 10):
        counts = Counter()
        for row in table("top100name.year"):
            name = written(row[f"name.{sex}.{decade}"])
            if name:
                counts[name] += int(row[f"n.{sex}.{decade}"])
        write(f"{kind}-{decade}", counts)

surnames = Counter()
for row in table("familyname"):
    name = written(row["surname"], SURNAMES)
    if name:
        surnames[name] += int(row["n.1930_2008"])
write("surnames", surnames)
