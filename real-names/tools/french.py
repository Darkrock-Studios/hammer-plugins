"""Builds resources/names/french/: the most common given names by sex, over all years and for each
decade of birth, and the most common surnames, each with its count; see resources/names/NOTICE.txt.
Needs, in the working directory:

    nat2022.csv              https://www.insee.fr/fr/statistiques/fichier/7633685/nat2022_csv.zip, unzipped
    noms2008nat_txt.txt      https://www.insee.fr/fr/statistiques/fichier/3536630/noms2008nat_txt.zip, unzipped

    python3 french.py <working directory> <resources/names/french directory>
"""
import csv
import os
import re
import sys
import unicodedata
from collections import Counter

GIVEN = 1000
SURNAMES = 5000
# A given name this many times as common for the other sex is left out.
OTHER_SEX = 20

# INSEE writes surnames in capitals without accents. These are the ones among the most common that
# have them; a surname that is also a given name takes the given name's, and "-iere" is "-ière".
SURNAME_FORMS = [
    "Guérin", "Carré", "Ménard", "Léger", "Prévost", "Lévêque", "Barré", "Lemaître", "Bénard", "Maréchal",
    "Barthélémy", "Hébert", "Vallée", "Lelièvre", "Régnier", "Pagès", "Dupré", "Labbé", "Péron", "Chrétien",
    "Lévy", "Macé", "Bègue", "Prévot", "Ferré", "Técher", "Léonard", "Guéguen", "Bérard", "Doré", "Théry",
    "Quéré", "Duchêne", "Crépin", "Lécuyer", "Thébault", "Guégan", "Maître", "Leprêtre", "Carrère",
    "Pélissier", "Leclère", "Pépin", "Thévenin", "Sénéchal", "Thévenet", "Château", "Véron", "Génin",
    "Métayer", "Bruyère", "Chéron", "Soulié", "Hérault", "Ménager", "Honoré", "Jégou", "Lépine", "Février",
    "Hémery", "Augé", "Guéry", "Trouvé", "Nédélec", "Liénard", "Traoré", "Estève", "Frémont", "Béraud",
    "Mathé", "Béguin", "Després", "Jézéquel", "Fèvre", "Féron", "Guédon", "Brûlé", "Élie", "Lévesque",
    "Pérès", "Nègre", "Lemarié", "Chêne", "Dugué", "Périer", "Héraud", "Périn", "Héry", "Boué", "Dieudonné",
    "Bergé", "Péan", "Théron", "Barrère", "Pellé", "Guérard", "Métais", "Gérardin", "Métivier", "Pérot",
    "Jaffré", "Hébrard", "Méry", "Mazé", "Béal", "Delépine", "Éthève", "Brémond", "Rémond", "Thiébaut",
    "Évrard", "Étienne", "Stéphan", "Sébastien", "Lefèvre", "Lefrançois", "Besançon", "Gonçalves", "Lainé",
]


def fold(name):
    """`name` in capitals without accents, as INSEE writes surnames."""
    return "".join(c for c in unicodedata.normalize("NFD", name.upper()) if not unicodedata.combining(c))


def cased(name):
    """`name`, in capitals, with a capital only after a space, a hyphen, or an apostrophe."""
    return re.sub(r"(^|[ '-])(.)", lambda m: m.group(1) + m.group(2).upper(), name.lower())


def write(path, counts, most):
    ranked = sorted(counts.items(), key=lambda item: (-item[1], item[0]))[:most]
    with open(path, "w", encoding="utf-8") as f:
        f.writelines(f"{name}\t{count}\n" for name, count in ranked)
    print(os.path.basename(path), len(ranked))


work, out = sys.argv[1], sys.argv[2]
os.makedirs(out, exist_ok=True)

# Keyed by (sex, decade), with decade None for all years.
given = {}
with open(os.path.join(work, "nat2022.csv"), encoding="utf-8") as f:
    for row in csv.DictReader(f, delimiter=";"):
        # "_PRENOMS_RARES" gathers the rare names, and "XXXX" the years of names rare in each year.
        if row["preusuel"].startswith("_") or not row["annais"].isdigit():
            continue
        for key in ((row["sexe"], int(row["annais"]) // 10 * 10), (row["sexe"], None)):
            given.setdefault(key, Counter())[cased(row["preusuel"])] += int(row["nombre"])
for (sex, decade), counts in sorted(given.items(), key=lambda item: (item[0][0], item[0][1] or 0)):
    kind = {"1": "masculine", "2": "feminine"}[sex]
    other = given[("1" if sex == "2" else "2", decade)]
    counts = {name: count for name, count in counts.items() if other[name] <= OTHER_SEX * count}
    write(os.path.join(out, f"{kind}-{decade}.txt" if decade else f"{kind}.txt"), counts, GIVEN)

forms = {}
for name, _ in (given[("1", None)] + given[("2", None)]).most_common()[::-1]:
    forms[fold(name)] = name
forms.update({fold(form): form for form in SURNAME_FORMS})

surnames = Counter()
with open(os.path.join(work, "noms2008nat_txt.txt"), encoding="ascii") as f:
    for row in csv.reader(f, delimiter="\t"):
        # The first row is the header, and "AUTRES NOMS" gathers the rare surnames.
        if row[0] in ("NOM", "AUTRES NOMS"):
            continue
        name = forms.get(row[0]) or re.sub(r"iere$", "ière", cased(row[0]))
        surnames[name] += sum(int(count) for count in row[1:])
write(os.path.join(out, "surnames.txt"), surnames, SURNAMES)
