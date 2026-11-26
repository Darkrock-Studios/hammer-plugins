"""Builds resources/names/mexican.txt's lists from INEGI: the most common given names by sex among births
registered in Mexico from 2017 to 2019, for the 2010s, and in 2020 and 2021, for the 2020s, and the most
common surnames registered from 2017 to 2020, each with its count; see resources/names/NOTICE.txt. Run it
after wikidata.py, whose lists of all years and for earlier decades it leaves.
INEGI's tables are gone from its site, so it downloads the Internet Archive's copies of them:

    Nombres_mas_populares_2017_2021.csv     given names of births registered from 2017 to 2021
    Nombres_mas_populares_2020.csv          given names of births registered in 2020
    Nombres_mas_populares_2021.csv          given names of births registered in 2021
    Apellidos_mas_comunes_2017_2020.csv     surnames registered from 2017 to 2020, 20 times or more

INEGI writes names in capitals without accents. To restore them, it asks https://query.wikidata.org for
the Spanish spellings of the given names and surnames of Mexico's people. It keeps each download and
answer in the working directory, so a second run asks for nothing.

    python3 mexican.py <working directory> <resources/names/mexican.txt>
"""
import csv
import io
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
# A given name this many times as common for the other sex is left out.
OTHER_SEX = 20

ARCHIVE = "https://web.archive.org/web/{}id_/https://cuentame.inegi.org.mx/poblacion/natalidad/{}"
TABLES = {
    "Nombres_mas_populares_2017_2021.csv": "20230626153723",
    "Nombres_mas_populares_2020.csv": "20211016194907",
    "Nombres_mas_populares_2021.csv": "20230802081456",
    "Apellidos_mas_comunes_2017_2020.csv": "20220219220725",
}
SEXES = {"MUJER": "feminine", "HOMBRE": "masculine"}
# Words written in small letters inside a name, as in "José de Jesús" or "Montes de Oca".
PARTICLES = {"de", "del", "la", "las", "los", "y"}

# Words of common names that Wikidata lacks, or has mostly otherwise than Mexico writes them.
FORMS = [
    "Ian", "Vladimir", "Lucía", "Jazmín", "Bárbara", "América", "Génesis", "Sarahí", "Esaú", "Elián", "Mauleón",
    "Mejía", "Macías", "Morán", "Limón", "Cantú", "Alanís", "Velásquez", "Terán", "Lomelí", "Quiñónez", "Jáuregui",
    "Castrejón", "Tobías", "Tristán", "Nápoles", "Rabadán", "Terrón", "Carlín", "Laínez", "Absalón", "Reséndiz",
    "Padrón", "Olán", "Antúnez", "Ascención", "Cázarez", "Patlán", "Santibáñez", "Malagón", "Jácome", "Ricárdez",
    "Rayón", "Urióstegui", "Meléndrez", "Cárcamo", "Olivárez", "Chaídez", "Almendárez", "Manzanárez", "Sanjuán",
    "Málaga", "Borbón", "Castellón", "Tobón", "Várguez", "Chavarín", "Compeán", "Abraján", "Jáimez", "Natarén",
    "Malváez", "Galdámez", "Sahagún", "Bórquez", "Paulín", "Herrejón", "Brígido", "Colón", "Capistrán", "Merlín",
    "Tavárez", "Celedón", "Marrón", "Belmán", "Mónico", "Gijón", "Sóstenes", "Balandrán", "Liévano", "Leónides",
    "Simbrón", "Ciénega", "Clímaco", "Mártir", "Jaén", "Recéndiz", "Villafán", "Capulín", "Cambrón", "Ulín",
    "Abúndez", "Cástulo", "Gaucín", "Álamo", "Gárate", "Guía", "Canizález", "Jardínez", "Minjárez", "Anzúrez",
    "Jácquez", "Estupiñán", "Luquín", "Eguía", "Morúa", "Interián", "Ortegón", "Jijón", "Baylón", "Mayorquín",
    "Argáez", "Benavídez", "Alvídrez", "Carrizález", "Yánez", "Quistián", "Basaldúa", "Kantún", "Amézquita",
    "Luévanos", "Zunún", "Patishtán", "Xicohténcatl", "Montúfar", "Mañón", "Butrón", "Pájaro", "Valderrábano",
    "Mencías", "Tovías", "Cazarín", "Castán", "Angón", "Elotlán", "Citalán", "Tepatlán", "Acatitlán", "Saguilán",
    "Oregón", "Dórame", "Ánimas", "Telésforo", "Perrusquía", "Ronzón", "Altúzar", "Arévalos", "Pichón", "Salmorán",
    "Milián", "Periáñez", "Orendáin", "Esperón", "Cáñez", "Arízaga", "Ciprés", "Tabárez", "Magallán", "Urquídez",
    "Cantorán", "Albíter", "Bámaca", "Espínola", "Borjón", "Marchán", "Castrellón", "Sagahón", "Jimárez", "Santín",
    "Magadán", "Pulgarín", "Almodóvar", "Laureán", "Diéguez", "Martiñón", "Agatón", "Guardián", "Bogarín",
    "Gachupín", "Guadián", "Vílchiz", "Gascón", "Ciprián", "Sapién", "Pánuco", "Mogollán", "Falfán", "Terríquez",
    "Verónico", "Abadía", "Espiridión", "Razón", "Bibián", "Márez", "Ojéndiz", "Álamos", "Escárrega", "Linárez",
    "Monteón", "Morillón", "Palmerín", "Recéndez", "Otáñez", "Pazarán", "Ocádiz", "Gamón", "Baltiérrez", "Apáez",
    "Manrríquez", "Amigón", "Escalón", "Monjardín", "Zerón", "Calcáneo", "Fúnez", "Ágreda", "Alejándrez",
    "Enrríquez", "Pasión", "Vílchez", "Noguerón", "Arjón", "Nárez", "Ibargüen", "Bósquez", "Agramón", "Sigüenza",
    "Euán", "Collí", "Chablé", "Chalé", "Cimé", "Batún", "Ucán", "Itzá", "Joachín", "Colín", "Servín", "Medellín",
    "Castelán", "Cháirez", "Arreguín", "Payán", "Aguillón", "Huitrón", "Monzón", "Zamarrón", "Jáquez", "Bartolón",
    "Pinzón", "Verdín", "Morfín", "Rochín", "Villalón", "Monárrez", "Barón", "Alducín", "Tarín", "Carlón", "Picón",
    "Castillón", "Licón", "Marbán", "Lazarín", "Comparán", "Antillón", "Guitrón", "Andalón", "Amparán", "Montalván",
    "Flórez", "Henríquez", "Lupián", "Petatán", "Zacatlán", "Tudón", "Arrizón", "Gracián", "Platón", "Olazarán",
    "Florián", "Malfavón", "Quilantán", "Tavizón", "Laborín", "Tarazón", "Cachón", "Gavilán", "Cebrián", "Martagón",
    "Saviñón", "Miñón", "Sansón", "Talancón", "Infanzón", "Ayllón", "Espín", "Asención", "Cantún", "Huízar",
    "Alpízar", "Nájar", "Bazaldúa", "Alcántar", "Cázares",
]
# Not surnames: "JR" is a junior's, and "DUE" is "DUEÑAS" cut short at its Ñ.
NOT_SURNAMES = {"JR", "DUE"}

work, out = sys.argv[1], sys.argv[2]
os.makedirs(work, exist_ok=True)


def fetch(file, url):
    """The bytes at `url`, kept in the working directory as `file`."""
    path = os.path.join(work, file)
    if not os.path.exists(path):
        request = urllib.request.Request(url, headers={"User-Agent": "hammer-plugins real-names/tools/mexican.py"})
        for attempt in range(4):
            try:
                with urllib.request.urlopen(request, timeout=120) as reply:
                    data = reply.read()
                break
            except OSError:
                if attempt == 3:
                    raise
                time.sleep(30 * (attempt + 1))
        with open(path, "wb") as f:
            f.write(data)
        time.sleep(2)
    with open(path, "rb") as f:
        return f.read()


def table(file):
    """A table's rows. INEGI writes the given names' tables of 2017 to 2021 and of 2021 in UTF-8, and the
    rest in Latin-1."""
    data = fetch(file, ARCHIVE.format(TABLES[file], file))
    text = data.decode("utf-8-sig") if data.startswith(b"\xef\xbb\xbf") else data.decode("latin-1")
    return list(csv.reader(io.StringIO(text)))


def given_names(file):
    """A given names' table's counts, by (sex, name). A year's table has its women and men side by side."""
    counts = Counter()
    for row in table(file):
        for i in range(len(row) - 3):
            if row[i].isdigit() and row[i + 2] in SEXES:
                counts[(SEXES[row[i + 2]], row[i + 1])] += int(row[i + 3].replace(",", ""))
    return counts


def fold(name):
    """`name` in capitals without accents, as INEGI writes names; it keeps the Ñ."""
    name = unicodedata.normalize("NFD", name.upper()).replace("N\u0303", "Ñ")
    return "".join(c for c in name if not unicodedata.combining(c))


def spellings():
    """Each word of the given names and surnames of Mexico's people that Wikidata has, in the spelling most
    of them have, by its letters as INEGI writes them."""
    words = Counter()
    # P735 is given name, and P734 family name.
    for prop in ("P735", "P734"):
        query = f"""SELECT ?label (COUNT(DISTINCT ?p) AS ?count) WHERE {{
              ?p wdt:P27 wd:Q96; wdt:{prop} ?name. ?name rdfs:label ?label. FILTER(LANG(?label) IN ("es", "mul"))
            }} GROUP BY ?label"""
        url = "https://query.wikidata.org/sparql?" + urllib.parse.urlencode({"query": query, "format": "json"})
        for row in json.loads(fetch(f"wikidata-{prop}.json", url))["results"]["bindings"]:
            for word in unicodedata.normalize("NFC", row["label"]["value"]).split():
                # Spanish's letters only, which leaves out Catalan's and Portuguese's spellings.
                if re.fullmatch(r"[A-ZÁÉÍÓÚÜÑ][a-zA-ZáéíóúüñÁÉÍÓÚÜÑ]+", word):
                    words[word] += int(row["count"]["value"])
    forms = {fold(word): word for word, _ in sorted(words.items(), key=lambda item: (item[1], item[0]))}
    forms.update({fold(form): form for form in FORMS})
    return forms


def written(name, forms):
    """`name`, in INEGI's capitals, written as `forms` has its words, or capitalized."""
    words = []
    for word in name.split():
        lower = word.lower()
        words.append(lower if lower in PARTICLES else forms.get(word, word[0] + lower[1:]))
    return " ".join(words)


forms = spellings()

# The table of 2017 to 2021 less the years' tables gives 2017 to 2019. A name missing from a year's
# table, which has fewer names, is under its least count that year, and counted as none. A name given
# fewer times from 2017 to 2019 than the least of the table of 2017 to 2021 is left out, as the table
# has only those of them that stayed common, such as "Liam".
period = given_names("Nombres_mas_populares_2017_2021.csv")
recent = given_names("Nombres_mas_populares_2020.csv") + given_names("Nombres_mas_populares_2021.csv")
least = min(period.values())
earlier = Counter({key: count - recent[key] for key, count in period.items() if count - recent[key] >= least})
for decade, counts in ((2010, earlier), (2020, recent)):
    for kind in SEXES.values():
        other = "masculine" if kind == "feminine" else "feminine"
        kept = Counter()
        for (sex, name), count in counts.items():
            if sex == kind and counts[(other, name)] <= OTHER_SEX * count:
                kept[written(name, forms)] += count
        lists.write(out, f"{kind}-{decade}", kept.items(), GIVEN)

# Some records double a surname's Ñ, as "MUÑÑOZ".
registered = Counter()
for row in table("Apellidos_mas_comunes_2017_2020.csv"):
    if len(row) > 1 and re.fullmatch(r"[\d,]+", row[1]):
        registered[row[0].replace("ÑÑ", "Ñ")] += int(row[1].replace(",", ""))
surnames = Counter()
for name, count in registered.items():
    if len(name) < 2 or name in NOT_SURNAMES:
        continue
    # Some records lose a surname's Ñ, as "MUOZ" for "MUÑOZ"; such a surname is counted under the more
    # common one with its Ñ, unless Wikidata has it.
    if any(word not in forms for word in name.split()):
        whole = max((name[:i] + "Ñ" + name[i:] for i in range(len(name) + 1)), key=registered.__getitem__)
        name = whole if registered[whole] > count else name
    surnames[written(name, forms)] += count
lists.write(out, "surnames", surnames.items(), SURNAMES)
