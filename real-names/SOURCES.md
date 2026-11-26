# Name sources

Where each culture's lists come from, how to rebuild them, and what was checked and not used. This
file is for maintainers; `resources/names/NOTICE.txt` is the attribution that ships with the plugin,
and the two should agree.

## Rebuilding

Each script in `tools/` writes the lists it builds into `resources/names/<culture>.txt` and keeps the
rest. Run `wikidata.py` first, then the culture's own script, which lays its official lists over
Wikidata's. Downloads and API answers are kept in `tools/cache/<code>/` (ignored by git), so a second
run asks for nothing. From `real-names/tools/`:

    python3 wikidata.py <culture> cache/wd ../resources/names/<culture>.txt
    python3 <culture>.py cache/<code> ../resources/names/<culture>.txt

`canadian.py` and `israeli.py` also write a second culture's file, given as a third argument
(`quebecois.txt` and `levantine.txt`). Some scripts download what they need; others need files put in
the cache first, as each script's docstring says.

A cached answer is kept under its culture and query's name, not the query's text, so after changing a
query delete the cache files it wrote (for `wikidata.py`, `cache/wd/<culture>-<name>.json`) or it keeps
answering from the old ones. `argentine.py` takes surnames' accents from `spanish.txt` and
`mexican.txt`, so run it after their scripts.

| Culture | Script | Cache | Official lists | From Wikidata |
|---|---|---|---|---|
| American | american.py | | all | none |
| Argentine | argentine.py | ar | all since the 1920s | masculine before the 1920s |
| Australian | australian.py | au | overall, decades 1940s to 2020s | surnames, earlier decades |
| Austrian | austrian.py | at | decades since the 1980s | overall, surnames, earlier decades |
| Belgian | belgian.py | be | overall, decades since the 1990s, surnames | earlier decades |
| Brazilian | brazilian.py | br | all since the 1930s | masculine before the 1930s |
| Canadian | canadian.py | ca | overall, decades 1920s to 2020s | surnames, earlier decades |
| Chinese | chinese.py | cn | all | none |
| Czech | czech.py | cz | 2020s (2025 top 100) | the rest |
| English | english.py | en | decades since the 1990s | the rest |
| Finnish | finnish.py | fi | overall, surnames | all decades |
| French | french.py | | all | none |
| German | german.py | de | 2010s and 2020s | the rest |
| Irish | irish.py | ie | overall, decades since the 1960s, surnames | earlier decades |
| Israeli | israeli.py | il | all since the 1940s (Jewish sector) | masculine before the 1940s |
| Italian | italian.py | it | decades since the 2000s | overall, surnames, earlier decades |
| Korean | korean.py | kr | surnames | the rest |
| Levantine | israeli.py | il | all (Arab sectors) | none |
| Mexican | mexican.py | mx | 2010s and 2020s, surnames | overall, earlier decades |
| New Zealander | new_zealander.py | nz | overall, decades 1900s to 2020s | surnames, masculine before the 1900s |
| Norwegian | norwegian.py | no | all | none |
| Polish | polish.py | pl | overall, decades since 2000, surnames | earlier decades |
| Québécois | canadian.py | ca | overall, decades since the 1980s | surnames, earlier decades |
| Scottish | scottish.py | sc | overall, decades since the 1970s, surnames | earlier decades |
| Slovenian | slovenian.py | si | overall, decades since the 1930s, surnames | masculine before the 1930s |
| Spanish | spanish.py | es | all since the 1920s | decades before the 1920s |
| Swedish | swedish.py | se | overall, decades since the 1940s, surnames | earlier decades |
| Swiss | swiss.py | ch | overall, decades since the 1920s, surnames | masculine before the 1920s |

Every other culture is Wikidata's alone: Danish, Dutch, Egyptian, Filipino, Greek, Hungarian, Indian, Iranian,
Japanese, Maghrebi, Nigerian, Russian, Turkish, Ukrainian.

## Cultures with official statistics

### American
- **Lists:** all.
- **Source:** Social Security Administration, national baby names, https://www.ssa.gov/oact/babynames/limits.html
  (`names.zip`); Census Bureau, Frequently Occurring Surnames from the 2010 Census,
  https://www.census.gov/topics/genealogy/data/2010_surnames.html (`names.zip`).
- **Licence:** works of the US government, public domain.
- **Coverage:** Social Security card applications for births 1880 to the latest year, names given 5+
  times a year; top 1,000 per sex overall and per decade. Surnames: top 5,000 of the 2010 census.
- **Rebuild:** unzip both into the working directory first.
- **Caveats:** SSA writes names without spaces, hyphens or inner capitals; the census writes surnames
  in capitals without punctuation, which the script restores for the common ones.

### Argentine
- **Lists:** feminine, masculine, decades 1920s to 2010s (whole given names), 2020s (single given
  names), surnames. masculine-1880 to 1910 from Wikidata.
- **Source:** RENAPER (Registro Nacional de las Personas): Nombres de personas físicas,
  https://infra.datos.gob.ar/catalog/otros/dataset/2/distribution/2.1/download/historico-nombres.zip
  (its dataset page is gone); Nombres propios en Argentina,
  https://datos.gob.ar/dataset/nombres-propios-en-argentina1; Distribución de apellidos en Argentina,
  https://datos.gob.ar/dataset/distribucion-de-apellidos-en-argentina1. CSV URLs in `argentine.py`.
- **Licence:** CC BY 4.0; attribute RENAPER.
- **Coverage:** whole given names as registered, by year of birth 1922 to 2015. The 2020s: births 2020
  to 2024, each given name counted on its own (a person with two counts twice). Surnames: residents in
  December 2021, those more than 10 of a province's residents have, summed over provinces. Yearly
  totals run above births from 1975 to 1996 and jump in 2012, so read counts as shares.
- **Rebuild:** about 78 MB of downloads and 4 Wikidata queries; about 3 minutes from cache. Reads
  spanish.txt and mexican.txt beside the output for surname accents.
- **Caveats:** the source has no sex. A word tells one when Argentines on Wikidata, or RENAPER's top 20
  by sex, have it over 20 times as often for one sex; other words learn it from the names they appear
  in; a name takes the sex of its first word that tells one (José María, María José). 0.48% of people
  are left out; a hand check of 400 names found no errors. Accents from Wikidata, RENAPER's own accented
  records, and the Spanish and Mexican lists, with small fix lists. Spanish particles lowercase (de la
  Fuente), Italian capitalized (De Luca). The 2020s tails keep real misspellings (Bejamín).
- **Considered and not used:** the 2012 to 2024 single-name file for the whole-name lists (it counts
  words, not names); the top-20 surname files.

### Australian
- **Lists:** feminine, masculine, decades 1940s to 2010s from South Australia's complete lists; 2020s from
  the pooled top 100s of SA, NSW and Victoria. Surnames and earlier decades from Wikidata (Australia Q408).
- **Source:** Popular Baby Names from South Australia's Attorney-General's Department
  (https://data.sa.gov.au/data/dataset/popular-baby-names: `baby-names-1944-2013.zip`, yearly CSVs for
  2014 to 2017, top 100s since 2018), the NSW Registry of Births, Deaths and Marriages
  (https://data.nsw.gov.au/data/dataset/popular-baby-names-from-1952, one CSV), and the Registry of
  Births, Deaths and Marriages Victoria (https://discover.data.vic.gov.au/dataset/popular-baby-names,
  an XLSX a year). Each found through its portal's CKAN API (NSW's and SA's under `/data/api`).
- **Licence:** SA CC BY 4.0 (its own portal; the data.gov.au mirror says CC BY 3.0 AU); NSW "Creative
  Commons Attribution", no version stated; Victoria CC BY 4.0.
- **Coverage:** SA births registered 1944 to 2017, every name down to a count of 1 (SA is about 7% of
  Australia's births, but its top 100 shares 79 to 90 names with NSW's). 2020 to 2025: top 100 per sex
  per state only. Names given once in a decade are left out.
- **Rebuild:** about 50 downloads, 35 seconds; wikidata.py about 60 queries.
- **Caveats:** the 2020s lists are only the head (about 155 names), and names near a state's cut-off
  are undercounted. Casing by rule (Jo-Anne, O'Shea, McKenzie); accent variants counted together under
  the commonest spelling. Placeholders (UNNAMED, BABY) and garbled entries dropped.
- **Considered and not used:** Queensland's downloads sit behind an AWS WAF challenge. Western Australia
  and the other states publish top-100 pages only. Adding top 100s into SA's complete lists would
  inflate the head. No open national surname statistics.

### Austrian
- **Lists:** decades 1980s to 2020s. Overall, surnames and earlier decades from Wikidata (citizens of
  Austria, and of Austria-Hungary and Cisleithania born in what is now Austria).
- **Source:** Statistik Austria, open data Vornamen OGDEXT_VORNAMEN_1,
  https://data.statistik.gv.at/data/OGDEXT_VORNAMEN_1.csv; spellings from Vornamen 1984 bis 2025 original
  Schreibweise, https://www.statistik.at/fileadmin/pages/426/Vornamen_1984_bis_2025_original_Schreibweise.ods,
  tables 12 and 13.
- **Licence:** the open data CC BY 4.0, "Datenquelle: Statistik Austria, data.statistik.gv.at". The .ods
  under statistik.at's Impressum: any use with the source "STATISTIK AUSTRIA" named and changes noted;
  used only to choose spellings.
- **Coverage:** births 1984 to 2025 by year, sex and district; Austrian citizens only until 2009, all
  babies from 2010. Names given fewer than 4 times a year appear only under district GEH, which is
  summed with the rest; names given once in the whole period are absent. The 1980s are 1984 to 1989.
- **Rebuild:** 2 downloads (28 MB CSV, 2.7 MB .ods). The .ods URL names its last year and will change.
- **Caveats:** the open data strips every mark but umlauts; each name takes its sex's most common
  original spelling from the .ods (René, but Lea). Overall stays Wikidata's, as the open data covers
  only births since 1984; its feminine list has 545 names, and the decades before the 1980s 53 to 169.
- **Considered and not used:** no official surname or whole-population first-name statistics.

### Belgian
- **Lists:** feminine, masculine, decades 1990s to 2020s, surnames. Earlier decades from Wikidata.
- **Source:** Statbel, first names and family names,
  https://statbel.fgov.be/en/themes/population/names-and-first-names: `Firstnames_Girls_1995-.xlsx`,
  `Firstnames_Boys_1995-.xlsx`, `First names_Population_2026.xlsx`, `Family_names_2026.xlsx` (paths in
  `belgian.py`).
- **Licence:** CC BY 4.0.
- **Coverage:** everyone living in Belgium on 1 January 2026 (names 5+ people have; the 10,000 most
  common surnames); newborns 1995 to 2025, names given 5+ times a year. The 1990s lists are 1995 to 1999.
- **Rebuild:** 4 downloads.
- **Caveats:** the workbooks drop letters outside Latin-1; restored for the common names. Spellings
  differing only in capitals are counted together. One list for the whole country: Flemish and Walloon
  names are mixed.

### Brazilian
- **Lists:** feminine, masculine, decades 1930s to 2020s, surnames. masculine-1880 to 1920 from Wikidata.
- **Source:** IBGE, Nomes no Brasil, 2022 census, https://censo2022.ibge.gov.br/nomes/, via the
  undocumented API https://servicodados.ibge.gov.br/api/v3/nomes/2022 (`localidade/0/ranking/nome`
  with `sexo` or `decada`, `localidade/0/ranking/sobrenome`, `nome/<name>`). Technical note:
  https://censo2022.ibge.gov.br/nomes/nota_tecnica/notatecnica.pdf.
- **Licence:** none stated; IBGE lists its names service among its open-data channels (Decree
  8.777/2016). Used with IBGE named as the source. Unverified.
- **Coverage:** all residents on 1 August 2022; first word of the given name; names 20+ people have,
  nationally and within each decade. The 1930 bucket is everyone born before 1940.
- **Rebuild:** about 5,700 requests of 30 rows each (the page size is fixed), about 1.5 hours. API docs
  and the census site are behind Cloudflare; the API itself answers plain requests.
- **Caveats:** decades are not split by sex in the source, so a decade's split is the name's overall
  split. No accents in the source: restored from the Wikidata lists (so run wikidata.py first), an
  ending table and hand lists. The census counts second given names among surnames; most are filtered
  (Maria, José) but Aparecida and Fátima remain. Typo filter: a name 100 times rarer than one a letter
  away is dropped; milder typos remain (Perreira, Guilerme).
- **Considered and not used:** the 2010 census API (v2) gives only the top 20 per ranking.

### Canadian
- **Lists:** feminine, masculine, decades 1990s to 2020s from Statistics Canada; 1980s from Quebec,
  Ontario, Alberta and British Columbia summed; 1920s to 1970s from Ontario and British Columbia.
  Surnames, feminine-1910 and masculine before the 1920s from Wikidata.
- **Source:** Statistics Canada table 17-10-0147-01, First names at birth by sex at birth,
  https://www150.statcan.gc.ca/t1/tbl1/en/tv.action?pid=1710014701; Ontario's top baby names
  (https://data.ontario.ca); British Columbia's most popular names for the past 100 years
  (https://www2.gov.bc.ca); Alberta's frequency and ranking of baby names 1980 to 2020
  (https://open.alberta.ca); Retraite Québec (see Québécois). Download URLs in `canadian.py`.
- **Licence:** Statistics Canada Open Licence ("Adapted from Statistics Canada, <product>, <reference
  date>. This does not constitute an endorsement by Statistics Canada of this product."); Open
  Government Licences of Ontario, British Columbia and Alberta ("Contains information licensed under
  the Open Government Licence – <province>"); CC BY 4.0 for Retraite Québec.
- **Coverage:** births. StatCan 1991 to 2025, all Canada, names given 5+ times a year. Ontario 1917
  to 2024 and BC 1924 to 2023, 5+ a year; Alberta 1980 to 2020, complete. The 1920s to 1940s lists
  have 593 to 742 names.
- **Rebuild:** `python3 canadian.py cache/ca ../resources/names/canadian.txt ../resources/names/quebecois.txt`
  right after `wikidata.py` for both, as it reads the Québécois lists for spellings. 8 downloads (about
  65 MB), about a minute.
- **Caveats:** the 1920s to 1970s are English Canada only. Ontario's Marie and Joseph (often a French
  Canadian's first given name but not the one used) are counted at BC's share. Abbreviations expanded
  (WM, JOS, CHAS, THOS, ROBT, GEO), "JR" and placeholders dropped, Ontario's 2024 mojibake repaired.
  Sources drop accents: a French name takes Quebec's spelling, but a name among the 1,000 most common
  American ones takes the spelling most bearers have, so Chloe, Zoe and Stephanie stay plain while
  Félix, Léa and Geneviève are accented. Surnames are Wikidata's (Smith, Roy, Tremblay, Brown, Gagnon).
- **Considered and not used:** no open official Canadian surname source. Nova Scotia publishes a top
  20 only.

### Chinese
- **Lists:** all, in pinyin without tones.
- **Source:** Bao, H.-W.-S. (2023), ChineseNames: Chinese Name Database 1930-2008,
  https://github.com/psychbruce/ChineseNames (`data-csv/`), from the National Citizen Identity
  Information Center, 2008.
- **Licence:** GPL-3.0 and CC BY-NC-SA. **Non-commercial use only**, shared under the same terms.
- **Coverage:** births 1930 to 2008 by cohort; names of the same pinyin counted as one.

### Czech
- **Lists:** feminine-2020, masculine-2020. Everything else from Wikidata (citizens of Czechia, and of
  Czechoslovakia and Austria-Hungary born in what is now Czechia).
- **Source:** Czech Statistical Office (ČSÚ), 100 nejčetnějších dětských jmen 2025,
  https://csu.gov.cz/docs/107508/0a6170f4-bc53-7d35-afe2-3d5fcd0acb47/data_detska_jmena_top_100_cesko_2025.xlsx.
- **Licence:** CC BY 4.0 per
  https://csu.gov.cz/podminky_pro_vyuzivani_a_dalsi_zverejnovani_statistickych_udaju_csu; attribute
  Český statistický úřad.
- **Coverage:** births of 2025 only, the top 100 or so names per sex with counts.
- **Rebuild:** one 16 KB download.
- **Caveats:** the 2020s lists are the head only. Surnames are Wikidata's for both sexes in the man's
  form, the woman's formed by ending rules (Novák, Nováková; Hájek, Hájková; Němec, Němcová; Černý,
  Černá), or as women on Wikidata have it where they do. About 1% of people sit under unaccented
  English labels (Novak beside Novák).
- **Considered and not used:** ČSÚ's 2022 to 2024 top 100s give ranks without counts. The Interior
  Ministry withdrew its name and surname statistics.

### English
- **Lists:** decades since the 1990s. Overall, earlier decades and surnames from Wikidata.
- **Source:** ONS, Baby names in England and Wales: from 1996,
  https://www.ons.gov.uk/peoplepopulationandcommunity/birthsdeathsandmarriages/livebirths/datasets/babynamesinenglandandwalesfrom1996.
- **Licence:** Open Government Licence v3.0.
- **Coverage:** births in England and Wales 1996 to 2025.
- **Considered and not used:** ONS historical top 100 (1904 to 1994, one year a decade) gives ranks
  only, no counts.

### Finnish
- **Lists:** feminine, masculine, surnames. Every decade list from Wikidata (citizens of Finland, and of
  the Grand Duchy and the Russian Empire born in what is now Finland).
- **Source:** Digital and Population Data Services Agency (DVV), names in the Population Information
  System, https://avoindata.suomi.fi/data/dataset/57282ad6-3ab1-48fb-983a-8aba5ff8d29a: the latest
  `etunimitilasto-<date>-dvv.xlsx` (first given names) and `sukunimitilasto-<date>-dvv.xlsx`, found
  through the CKAN API.
- **Licence:** CC BY 4.0; attribute DVV.
- **Coverage:** living Finnish citizens, in Finland or abroad, on 4 August 2026; no years of birth.
  Given names 5+ people have, surnames 20+.
- **Rebuild:** 3 requests. avoindata.suomi.fi answers 403 to the script's User-Agent; the script then
  says to download the file by hand into the working directory.
- **Caveats:** spellings differing only by accent stay apart (Sirén, Siren), as DVV counts them;
  surnames differing only in capitals are merged. Dropped: "etunimetön" (no given name), abbreviations
  (Md, Mohd, Mst). The Wikidata decades are thin: 56 to 184 feminine names, none before 1900 or after
  the 2000s.
- **Considered and not used:** DVV's Nimipalvelu (https://nimipalvelu.dvv.fi) has counts by decade of
  birth, but its robots.txt disallows every query URL. DVV could be asked for that data in bulk.

### French
- **Lists:** all.
- **Source:** INSEE, Fichier des prénoms 2022, https://www.insee.fr/fr/statistiques/7633685; Fichier des
  noms de famille 2008, https://www.insee.fr/fr/statistiques/3536630.
- **Licence:** Licence Ouverte 2.0.
- **Coverage:** births 1900 to 2022; surnames of births 1891 to 2000.
- **Caveats:** surnames are in capitals without accents; restored for the common ones.

### German
- **Lists:** feminine-2010, masculine-2010, feminine-2020, masculine-2020. Everything else from Wikidata.
- **Source:** city open data, first given names only:
  - Berlin, https://github.com/berlin/haeufige-vornamen-berlin, `data/<year>/all_names.json`, 2017 to
    2023. CC BY 3.0 DE; attribution "BerlinOnline GmbH, auf Basis von Daten des Berliner Landesamtes für
    Bürger- und Ordnungsangelegenheiten (LABO)". Names given once in the whole city are left out.
  - Cologne, https://www.offenedaten-koeln.de, `Gesamt_Vornamen_2019-2022_0.csv`,
    `Vornamenstatistik_2023.csv`, 2019 to 2023. dl-de/zero-2.0.
  - Leipzig, https://opendata.leipzig.de, 2014 to 2023 and 2025. dl-de/by-2.0.
  - Munich, https://opendata.muenchen.de, dataset `vornamen-von-neugeborenen`, residents under a year old
    at year end, 2013 to 2025; counts of 4 or fewer (5 or fewer in 2025) not given. dl-de/by-2.0.
- **Coverage:** about 120,000 to 145,000 babies per decade and sex.
- **Rebuild:** about 35 requests.
- **Caveats:** four cities, not Germany. The 2010s lean on Munich. Years that count names in any
  position (Berlin 2012 to 2016, Cologne 2010 to 2018) are left out, as they push middle names up.
- **Considered and not used:** no national source (Destatis, GfdS publish nothing open). Bielefeld's
  residents by decade of birth 1920s to 2020s (CC BY 4.0) is one city's survivors, 300 to 740 names per
  list, with a strong returnee signature (Olga, Irina, Viktor); not clearly better than Wikidata.
  Leipzig 2024 has no open licence. Düsseldorf, Dortmund, Essen, Rostock and others publish similar
  newborn data and could widen the pool.

### Irish
- **Lists:** overall, decades since the 1960s, surnames. Earlier decades from Wikidata.
- **Source:** Central Statistics Office tables VSA50, VSA60, VSA110,
  https://ws.cso.ie/public/api.restful/PxStat.Data.Cube_API.ReadDataset/<table>/CSV/1.0/en.
- **Licence:** CC BY 4.0.
- **Coverage:** births registered 1964 to 2024, names given 3+ times a year; surnames of babies of 2023
  and 2024, 10+ occurrences.

### Israeli and Levantine
- **Lists:** israeli.txt: everything from the Jewish sector, except masculine-1890 to 1930 from Wikidata.
  levantine.txt: everything from the Muslim, Christian-Arab and Druze sectors pooled by count; no Wikidata.
- **Source:** Israel Central Bureau of Statistics. Given names: release 391/2025, The current names of
  those born 1949-2024, https://www.cbs.gov.il/he/mediarelease/DocLib/2025/391/11_25_391t1.xlsx (a
  sheet per sector and sex). Surnames: release 037/2019, Last Names in Israel 2017,
  https://www.cbs.gov.il/he/mediarelease/doclib/2019/037/11_19_037t3.xls (Jewish), t4 (Muslim), t5
  (Christian), t6 (Druze); notes in 11_19_037b.pdf.
- **Licence:** CBS Open License, https://www.cbs.gov.il/en/Pages/Enduser-license.aspx: commercial use
  allowed; attribution "The Central Bureau of Statistics (CBS), [file name / product], [(date of
  access)], available online: [link]"; no misleading presentation or implied endorsement.
- **Coverage:** the current first given name in the population register (April 2025) of residents born
  1949 to 2024: survivors, after any name changes. A name needs a total of 5+; yearly cells of 1 to 4
  are "..", counted as 0. The 1940s lists are 1949 alone (thin for Levantine: about 2,000 people).
  Surnames: the whole population in 2017, more than 30 bearers. The Muslim surname table repeats about
  305 names in a second block whose counts add to the first; they are summed.
- **Rebuild:** `python3 israeli.py cache/il ../resources/names/israeli.txt ../resources/names/levantine.txt`
  after `wikidata.py israeli`. Six files, about 13 MB. cbs.gov.il resets connections unless the
  User-Agent looks like a browser's; the script sends one. The surname tables exist only as BIFF8 .xls,
  read by `tools/xls.py`.
- **Transliteration:** the register writes every name in Hebrew letters. `tools/israeli-names.tsv`
  (17,277 rows: sector, kind, Hebrew, spelling, or "-" for left out) gives each name the spelling its
  community usually uses in English, following `tools/israeli-names.md`: Israeli forms for the Jewish
  sector (Yosef, Yitzhak, Itai, Chen), source-language spellings for immigrants' names (Sergey,
  Friedman, Kogan), Palestinian/Levantine forms for the Arab sectors (Mohammad, Yousef, Abu Ahmad,
  Al-Krenawi, Salameh) and Western forms for Christian-Arab names (George, Elias). It was transliterated
  by hand in batches against those conventions and reviewed at the top of each list. Changing a
  spelling is a one-row edit and a rebuild.
- **Caveats:** names without a sure spelling are left out: 0.8 to 3.2% of given-name holders, 12% of
  Jewish and 16% of Arab surname holders (many Bedouin and Ethiopian surnames among them, such as
  Ghadir). Some spellings are choices: Yisrael not Israel, Hana not Hannah, Jabarin not Jabareen.
  Surnames recorded with and without the article stay apart (Abu Hija, Abu al-Hija). The Christian
  surname table covers all Christians, but nearly all its names are Arab.
- **Considered and not used:** babynamesIL's `data-raw/intl/matches.csv` (github.com/aviezerl/babynamesIL)
  matches Hebrew names to US, English, French and Canadian counts, and its top spelling is noisy (Noah
  for נועה, Tacko for תקוה). Wikidata's he/en and ar/en labels cover about 1,350 of the Jewish names,
  rarely match Arab names in Hebrew letters, and often give English cognates (Joseph). IGRA's
  population-registry tables (https://genealogy.org.il/pop/) have no years and no stated licence.

### Italian

- **Lists:** feminine-2000, masculine-2000, feminine-2010, masculine-2010, feminine-2020,
  masculine-2020. Overall, earlier decades and surnames from Wikidata (newborns since 1999 are not the
  population).
- **Source:** ISTAT, Contanomi, https://www.istat.it/dati/calcolatori/contanomi/, through the JSONP
  service its page uses (named in its birthName.js):
  `https://www.istat.it/wp-content/themes/EGPbs5-child/contanomi/nati/index2022.php`, with
  `type=list&year=<y>&gender=f|m&limit=<n>` (a year's names) and `type=name&name=<name>&gender=f|m`
  (one name's count in each year, 1999 to 2025).
- **Licence:** CC BY 4.0, per https://www.istat.it/note-legali/.
- **Coverage:** births registered 2000 to 2019 and 2022 to 2024. The 2020s are 2022 to 2024: 2020's
  and 2021's lists stop at the top 255 to 610 names, and 2025 is updated weekly. Known counts cover
  89 to 90% of births in the 2000s, 85 to 86% in the 2010s and all of 2022 to 2024.
- **Rebuild:** about 700 list requests (a binary search on each year and sex) and 1,850 single-name
  requests. The service slows to 25 to 38 seconds an answer when asked often, so a full fetch took
  about 7 hours; from the cache, seconds.
- **Caveats:** before 2022 a year's list comes back empty once it would include a row the service
  cannot encode (a stray curly apostrophe or accented letter, which sorts after plain-letter names), so
  each year and sex keeps the longest list that works. Names past its end are counted from their
  single-name series, asked for every name that could reach a decade's top 1,000, on the guess that a
  missing year is no higher than the name's highest year within two years either side. The service
  escapes apostrophes, so names stored with a final apostrophe (Noè, Mosè, José, René, André, Josué,
  Nikolò, Marilù) cannot be asked for and are short in 2016 to 2019. A series also counts a name
  written with an accented letter (Céline with Celine); the script drops those list rows so they are
  not counted twice. Accents come back as a grave on the marked vowel, acute for a fixed list (José,
  René, André, Chloé, Josué, ...), or Wikidata's spelling; each word takes its commonest spelling
  since 2011 (Nicolò, Niccolò, Giosuè; Zoe and Chloe stay unaccented). Placeholders (DATO MANCANTE,
  NON INDICATO, SCONOSCIUTO) are dropped.
- **Considered and not used:** replacing the overall lists, since newborns since 1999 are not the
  population; nothing official covers surnames.

### Korean
- **Lists:** surnames. Given names and decades from Wikidata (citizens of South Korea).
- **Source:** Statistics Korea (since October 2025 the Ministry of Data and Statistics, 국가데이터처),
  2015 Population and Housing Census: KOSIS table DT_1IN15SD, 성씨ㆍ본관별 인구(5인 이상) - 전국
  (population by surname and clan seat, 5+ people, nationwide),
  https://kosis.kr/statHtml/statHtml.do?orgId=101&tblId=DT_1IN15SD, viewed by surname and downloaded
  as xlsx. Spellings from one Wikidata query: South Korea's people with a family name, with their
  English and Korean labels and their family name's Korean label.
- **Licence:** KOSIS's terms, https://kosis.kr/serviceInfo/useGuide.do: free to use, reuse and
  redistribute, commercially too, with the source cited (agency, survey, date, table, URL); figures
  must not be altered, nor sold unprocessed. Copyright is 국가데이터처's. No KOGL type is stated for
  statistics (only for publications). International and North Korean statistics are non-commercial
  only; this table is neither.
- **Coverage:** the whole population in the 2015 census; surnames 5+ people have. 532 rows of surname
  and hanja make 191 hangul surnames and 188 English ones; the 91,107 people under 기타 (other) are left
  out.
- **Rebuild:** download the table by hand into `cache/kr/` as `kosis-DT_1IN15SD-view.xlsx` (surname
  level only, 2015); one Wikidata query (11 MB), kept as `wikidata-people.json`.
- **Caveats:** a surname's hanja are one English surname (姜 and 康 are both Kang). Each is spelled
  as more of South Korea's people on Wikidata with its hangul spell it than any other way, where 2+
  do, and in the Revised Romanization otherwise. A person counts when their Korean label starts with
  their family name's hangul (not a stage name); their spelling is their English label's first or last
  word, not hyphened, and starting with a letter the hangul allows, whichever more of that surname's
  people have. Wikidata's labels lean to the Revised Romanization: Eom (30) over Um (19) and Uhm (11),
  Cheon over Chun, Gong over Kong, and Jung only just over Jeong (462 to 444). 이 and 리 are both Lee,
  조 and 초 both Cho, 선 and 순 both Sun; 유 (Yoo) and 류 (Ryu) stay apart, as do 나 (Na) and 라 (Ra).
  Revised Romanization for lack of data gives Hyeong, Du, Beom (often Hyung, Doo, Bum), one-letter A
  (阿), and Japanese surnames naturalized in hangul (등정, 藤井, as Deungjeong).
- **Considered and not used:** the KOSIS OpenAPI, whose key needs Korean identity verification; the
  Supreme Court's given-name viewer, which has no download.

### Mexican
- **Lists:** feminine-2010, masculine-2010, feminine-2020, masculine-2020, surnames. Overall and earlier
  decades from Wikidata.
- **Source:** INEGI, Nombres más populares 2017-2021, 2020 and 2021; Apellidos más comunes 2017-2020.
  Gone from https://cuentame.inegi.org.mx; downloaded from the Internet Archive's copies (URLs in
  `mexican.py`).
- **Licence:** INEGI Términos de Libre Uso, https://www.inegi.org.mx/inegi/terminos.html: copy, adapt
  and use commercially with the credit "Fuente: INEGI" and a note of changes.
- **Coverage:** births registered 2017 to 2021, names given 745+ times (a top list, about 500 to 700
  per sex); 2010s are 2017 to 2019 (the five-year table less 2020 and 2021). Surnames registered 20+
  times 2017 to 2020, first and second together.
- **Rebuild:** 6 requests (4 archived CSVs, 2 Wikidata queries for accents), about 3 minutes.
- **Caveats:** names are kept whole as registered ("María José"), unlike the Wikidata decades, which
  count first given names. Accents restored from Wikidata and a hand list (`FORMS`). N-for-Ñ spellings
  (Munoz) kept as registered.
- **Considered and not used:** INEGI birth microdata has no names; the census does not publish them.

### New Zealander
- **Lists:** feminine, masculine, decades 1900s to 2020s. Surnames, masculine-1880 and masculine-1890
  from Wikidata (New Zealand Q664; no one Wikidata has born in New Zealand since 1880 is a citizen of
  the Colony or Dominion of New Zealand alone). Wikidata has too few women for a feminine decade before
  the 1900s.
- **Source:** Department of Internal Affairs (DIA), Baby name popularity over time,
  https://catalogue.data.govt.nz/dataset/baby-name-popularity-over-time: `baby-names-1900-to-2025.csv`
  (Year, Sex, Name, Count) and `top-names-by-year-1900-2025.xlsx` (the same names a column a year).
- **Licence:** CC BY 4.0, as the dataset's catalogue page states ("Creative Commons Attribution 4.0
  International", read in a browser November 2026). DIA's copyright page,
  https://www.dia.govt.nz/Legal-Copyright-Index, asks to "attribute the work to the Department of
  Internal Affairs", with the source and its copyright status (Crown copyright) acknowledged.
- **Coverage:** births registered 1900 to 2025, names given 10+ times in a year. Decade lists run short
  of 1,000 (feminine 222 to 745, masculine 170 to 739; fewest in the 1900s). The 2020s are 2020 to 2025.
- **Rebuild:** download the CSV into `cache/nz` by hand; the catalogue answers automated requests
  with a bot check.
- **Caveats:** the CSV mixes encodings: é as 0x82 (code page 437) in some rows and 0xe9 (Latin-1) in
  others, ö as 0xf6, ī and ō as 0x8d and 0x8f (Nīkau, Kōwhai), and in 2025 "?" for a macron's letter
  (?ria, M?ia), restored from the workbook and earlier years. It writes Zoë as Zoé to 2024, where the
  workbook has Zoë. Spellings differing only in marks stay apart as registered (Zoe, Zoë; Maia, Māia),
  but the CSV counts some 2022 and 2023 macron spellings under the plain one (its Maia of 2023 is the
  workbook's Maia and Māia), so macron spellings are short in those years. Spellings differing only in
  capitals are counted together (Jo-Anne, Mackenzie, McKenzie). "Baby" (2006 to 2008) dropped. A
  name given under 10 times every year is missing, so tails are undercounted.
- **Considered and not used:** the workbook's counts: for 2022 and 2023 they run a little lower than
  the CSV's (likely an earlier extract) and split those macron spellings out; it
  is used only to check spellings. No official surname statistics.

### Norwegian
- **Lists:** all.
- **Source:** Statistics Norway StatBank tables 10467 and 12891, https://www.ssb.no/en/statbank.
- **Licence:** CC BY 4.0.
- **Coverage:** births 1880 to 2025 as per 100,000 born a year; surnames 200+ people have.

### Polish
- **Lists:** feminine, masculine, decades since 2000, surnames-feminine, surnames-masculine. Earlier
  decades from Wikidata.
- **Source:** Ministry of Digital Affairs, https://dane.gov.pl: datasets 1501 (PESEL first given names),
  568 (PESEL surnames) and 219, Imiona nadawane dzieciom w Polsce (newborns). Resource ids in `polish.py`,
  each from `https://api.dane.gov.pl/resources/<id>/file`.
- **Licence:** CC0 1.0.
- **Coverage:** PESEL register including the dead; newborns 2000 to 2025, names given fewer than 5
  times a year left out up to 2012 and fewer than 2 from 2013.
- **Rebuild:** 17 downloads, about a minute.
- **Caveats:** names registered without diacritics (Mikolaj, Michal) appear at low counts beside the
  proper form; kept as registered.
- **Considered and not used:** nothing official covers births by decade before 2000.

### Québécois
- **Lists:** feminine, masculine, decades 1980s to 2020s. Surnames and earlier decades from Wikidata
  (Canada's citizens born in Quebec).
- **Source:** Retraite Québec, Banque de prénoms (Filles, Garçons) on https://www.donneesquebec.ca.
- **Licence:** CC BY 4.0; attribute Retraite Québec.
- **Coverage:** children born 1980 to 2025 eligible for family benefits; counts under 5 given as "<5",
  counted as 2; names given once in the whole period removed.
- **Rebuild:** with Canadian, by `canadian.py`.
- **Caveats:** the source writes capitals without accents or hyphens. Spellings come from Wikidata's
  Québécois labels, then France's lists and the provinces' accented spellings, then a 29-name
  `QUEBEC_FORMS` list (mainly É- names France writes plain); compounds are built from their parts
  (Marie-Ève, Jean-François). Shortened entries (M CHRISTINE, LS PHILIPPE) are counted as the name
  they stand for. Uncertain spellings stay plain (Maeva). Wikidata is thin here: feminine decades of
  50 to 61 names.
- **Considered and not used:** ISQ's surname lists (2005) forbid reproduction without permission.

### Scottish
- **Lists:** overall, decades since the 1970s, surnames. Earlier decades from Wikidata.
- **Source:** National Records of Scotland, Babies' First Names and Most Common Surnames,
  https://www.nrscotland.gov.uk/publications/.
- **Licence:** Open Government Licence v3.0.
- **Coverage:** births registered 1974 to 2025; surnames in the registers in every fifth year 1975 to
  2010 and each year since 2013.

### Slovenian

- **Lists:** `feminine`, `masculine`, `surnames` from SURS's residents' tables (2026);
  `feminine-`/`masculine-1930` to `-1980` from SURS's Names and birthdays app (living population by
  period of birth); `-1990` to `-2020` from SURS's newborn tables. Wikidata: `masculine-1890` to
  `-1920` (no feminine decade before 1930 had enough people).
- **Source:** Statistical Office of the Republic of Slovenia (SURS). PxWeb API
  `POST https://pxweb.stat.si/SiStatData/api/v1/en/Data/<table>.px` (json-stat2, MERITVE=1): 05X1005S
  and 05X1010S (male and female first names, LETO=2026), 05X1015S to 05X1018S (surnames A-F, G-L, M-R,
  S-Ž, LETO=2026), 05X2001S and 05X2002S (baby boys' and girls' first names, 1992 to 2025). App:
  `GET https://www.stat.si/ImenaRojstva/sl/ImenaData/GetNameChartValues?name=<name>&gender=M|Z` (10
  counts: up to 1940, 1941-50, ... 2011-20, 2021-25).
- **Licence:** SURS terms, https://www.stat.si/StatWeb/en/StaticPages/Index/Copyright: free for any
  use, including commercial, with SURS acknowledged as the source ("Source: SURS") and changes marked.
- **Coverage:** residents on 1 January 2026, names with 5+ bearers (4,252 masculine and 4,435 feminine
  given names, about 30,400 surnames). Decade lists for the 1930s to 1980s are those living in 2026
  (immigrants included); 1941-50 is the 1940s and so on, and "up to 1940" the 1930s. The 1990s to
  2020s are births: names given to 5+ babies a year, 1992 to 2025 (the 1990s lack 1990 and 1991).
  Names with fewer than 10 living bearers are not asked about in the app.
- **Rebuild:** 8 PxWeb requests plus about 5,600 app requests at 0.5 s spacing (about 2.5 hours);
  answers are cached in `surs-*.json` and `imena-M.jsonl` / `imena-Z.jsonl`, so a rerun asks for
  nothing. The app answers `null` (or HTTP 500) for some names; these are recorded and skipped.
- **Caveats:** survivor lists under-represent men in old cohorts and include immigrants born abroad;
  the app hides some small counts ("z"), and 261 feminine names (3,168 people, each with 41 or fewer
  bearers) get no answer, so they are missing from the 1930s to 1980s lists. Resident lists include
  many names from former Yugoslavia and Albania, as the population does. Surnames are one list, as
  Slovenian surnames are the same for both sexes. A word in capitals is written with a capital only at
  its start, and names with one-letter words (such as "B K") are dropped.
- **Robots:** pxweb.stat.si's robots.txt disallows `/sistatdata/` (lower case) for every agent and
  everything for AI crawlers; the script uses only the documented API (`/SiStatData/api`), 8 requests,
  which the maintainer accepted. www.stat.si has no robots.txt.
- **Considered and not used:** survivors for the 1990s to 2020s (births leave out later immigrants);
  the app's newborn tables (the same data as 05X2001S and 05X2002S); PxWeb's CSV output (cp1250;
  json-stat2 is UTF-8).

### Spanish
- **Lists:** feminine, masculine, decades 1920s to 2020s, surnames. Decades before the 1920s from Wikidata.
- **Source:** INE, Apellidos y nombres más frecuentes, annual population census of 1 January 2025,
  https://www.ine.es/daco/daco42/nombyapel/nombyapel2.htm: `nombres_por_edad_media.xlsx`, the names tool
  (`https://www.ine.es/tnombres/formGeneralresult.do?vista=3`, a POST per decade and sex) and the surname
  tool (`https://www.ine.es/apellidos/formGeneralresult.do?vista=1`).
- **Licence:** CC BY 4.0 (https://ine.es/dyngs/AYU/index.htm?cid=125); cite "Elaboración propia con
  datos extraídos del sitio web del INE: www.ine.es".
- **Coverage:** all residents; names 20+ people have overall, top 5,000 per decade and sex (5+ people);
  top 5,000 surnames, counted as first plus second surname. The 1920 lists are everyone born before 1930.
- **Rebuild:** 24 INE requests (about 6 minutes) and about 200 Wikidata queries for accents.
- **Caveats:** INE gives capitals without accents and drops particles of compound names. Accents come
  from the spelling most Spanish citizens on Wikidata have, with overrides; some surnames come out in
  Catalan spelling (Tomàs, Solà) where Castilian may be commoner. Particles restored from a fixed list
  (María del Carmen); uncertain compounds are left out.
- **Considered and not used:** `apellidos_frecuencia.xls` is legacy binary Excel, which the standard
  library cannot read.

### Swedish
- **Lists:** feminine, masculine, decades 1940s to 2020s, surnames. Earlier decades from Wikidata.
- **Source:** Statistics Sweden, PxWeb API https://api.scb.se/OV0104/v1/doris/en/ssd/BE/BE0001, tables
  BE0001TNamn10, BE0001ENamn10, BE0001T100, BE0001Nyfodda.
- **Licence:** CC BY 4.0, https://www.scb.se/en/About-us/about-the-website-and-terms-of-use/; state
  Statistics Sweden as the source.
- **Coverage:** whole population at the end of 2020 (names and surnames 10+ people have); newborns
  2000 to 2022 (names given 2+ times); 1940s to 1990s from the 100 most common names among those
  living at the end of 2021, by year of birth.
- **Rebuild:** 8 requests. On some networks the IPv6 route to api.scb.se is very slow.
- **Caveats:** SCB stopped producing name statistics after 2022. The 1940s to 1990s lists are
  survivors' top 100 with spellings counted together (Carl under Karl). The 1920s and 1930s are left
  to Wikidata: survivors' top 100 lack that era's names (Bertil, Birgit).
- **Considered and not used:** Skatteverket's newborn top 1,000 (2021 to 2025) is only in an
  interactive app; SCB's 2022 workbook duplicates the API in capitals.

### Swiss
- **Lists:** feminine, masculine, decades 1920s to 2020s, surnames. masculine-1880 to 1910 from Wikidata.
- **Source:** Federal Statistical Office (STATPOP) on https://opendata.swiss: datasets
  `weibliche-vornamen-der-bevolkerung-nach-jahrgang-schweiz-2025`,
  `mannliche-vornamen-der-bevolkerung-nach-jahrgang-schweiz-2025` and
  `nachnamen-der-standigen-wohnbevolkerung-nach-sprachregion3`, found through the CKAN API and
  downloaded from https://dam-api.bfs.admin.ch.
- **Licence:** opendata.swiss "Open use. Must provide the source." (terms_by); source: FSO, STATPOP.
- **Coverage:** the permanent resident population at the end of 2025, by year of birth: survivors,
  wherever born. Names fewer than 3 people have are left out (surnames, per language region). A decade
  needs 2,000 people of a sex still living, so the 1910s go to Wikidata.
- **Rebuild:** 6 requests. opendata.swiss refuses Python's default User-Agent; the script sends its own.
- **Caveats:** the four language regions' surnames are counted together, and given names are national,
  so German, French and Italian Swiss names are mixed.

## Wikidata

`wikidata.py` builds lists from the people Wikidata has born since 1880, by citizenship (and, for
predecessor states, birthplace): each person's first given name and family name, by the name item's
English label, or its `mul` label where it has no English one. A name needs 3+ people; a decade list
needs 500 people and 50 names. It asks https://query.wikidata.org once per culture, sex, decade and
state, about 120 queries a culture. Data CC0.

These are people of note: mostly men, and elites, so the lists follow the population only roughly and
women's lists are thin for many cultures (Iranian, Filipino, Nigerian, Greek: under 250 names).

Danish and Hungarian are Wikidata's alone. Danish counts citizens of the Kingdom of Denmark (Q756617),
which Wikidata uses far more than Denmark (Q35), so a few Faroese and Greenlandic names appear.
Statistics Denmark has only per-name lookups and top 20 to 50 lists. Hungarian counts citizens of
Hungary, and of the Kingdom of Hungary and Austria-Hungary born in what is now Hungary; its name items'
English labels are already the native spellings (László, Erzsébet), and its file writes the surname
first. The Interior Ministry's Hungarian top 100s are all rights reserved.

Egyptian and Maghrebi (Morocco, Algeria and Tunisia pooled) are Wikidata's alone, with options of their
own: each country's people, and those of its predecessor states born where it is now, are counted
once; a name is spelled as the person's own English label spells it, since Wikidata often links a
Mohamed to the name item Muhammad (a name the label lacks counts for no one); and transliteration marks
(Muḥammad) are left off. Egyptian is thin (about 130 feminine names). The top Maghrebi surname,
Boushaki, reflects one family's many articles on Wikidata, not the population.

## Cultures without official statistics

Checked November 2026; each stays on Wikidata.

- **Dutch:** the Meertens Instituut's Nederlandse Voornamenbank (https://nvb.meertens.knaw.nl) has the
  top 100 names with counts for every year since 1880, but its terms
  (https://meertens.knaw.nl/disclaimer) forbid reusing a substantial part without permission. Ask
  nvb@meertens.knaw.nl. SVB and CBS publish nothing usable.
- **Filipino:** PSA publishes only top-10 infographics.
- **Greek:** ELSTAT's 2021 census names appear only as top-5 infographics and press coverage.
- **Indian, Iranian, Nigerian:** nothing official with counts. Iran's civil registration portal blocks
  foreign access and gives only top-N in Persian script.
- **Japanese:** no government name statistics; Meiji Yasuda's yearly survey of policyholders' children
  is a top 100 in kanji, all rights reserved.
- **Russian, Ukrainian:** no national or surname statistics; Moscow and Ukraine's Ministry of Justice
  publish newborn top lists without full counts.
- **Turkish:** TÜİK's population portal gives only the top 30 per year, sex and province (2018 to 2025).
- **Arab countries:** none of Saudi Arabia, Egypt, Jordan, Morocco, Algeria, Tunisia or the UAE
  publishes name statistics; Levantine comes from Israel's register instead.

## Sources ruled out

- Name datasets built from the 2021 Facebook leak (such as the `names-dataset` package): leaked
  personal data, whatever the volume.
- Forebears and similar sites: modelled estimates with no reuse licence.
- Getting around access controls (Cloudflare challenges, API keys, sign-ins): not done; such sources
  wait for proper access. The keyless services that ISTAT's Contanomi and SURS's Names and birthdays
  pages call are used as those pages use them, with requests spaced out.
