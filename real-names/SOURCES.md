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

Some scripts download what they need; others need files put in the cache first, as each script's
docstring says.

| Culture | Script | Cache | Official lists | From Wikidata |
|---|---|---|---|---|
| American | american.py | | all | none |
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
| Levantine | israeli.py | il | all (Arab sectors) | none |
| Mexican | mexican.py | mx | 2010s and 2020s, surnames | overall, earlier decades |
| Norwegian | norwegian.py | no | all | none |
| Polish | polish.py | pl | overall, decades since 2000, surnames | earlier decades |
| Québécois | canadian.py | ca | overall, decades since the 1980s | surnames, earlier decades |
| Scottish | scottish.py | sc | overall, decades since the 1970s, surnames | earlier decades |
| Spanish | spanish.py | es | all since the 1920s | decades before the 1920s |
| Swedish | swedish.py | se | overall, decades since the 1940s, surnames | earlier decades |
| Swiss | swiss.py | ch | overall, decades since the 1920s, surnames | masculine before the 1920s |

Every other culture is Wikidata's alone: Danish, Dutch, Egyptian, Filipino, Greek, Hungarian, Indian, Iranian, Italian,
Japanese, Korean, Maghrebi, Nigerian, Russian, Turkish, Ukrainian.

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
- **Italian:** ISTAT's Contanomi has newborns 1999 to 2025, CC BY 4.0, but its API returns empty
  answers past a certain length for most years (accented names appear to break it); nothing official
  covers the population or surnames.
- **Japanese:** no government name statistics; Meiji Yasuda's yearly survey of policyholders' children
  is a top 100 in kanji, all rights reserved.
- **Korean:** given names: only the Supreme Court's top-N viewer, no download. Surnames: KOSIS table
  DT_1IN15SD (2015 census, surname by clan seat, 5+ people) is reusable with citation, but bulk access
  needs a free KOSIS OpenAPI key.
- **Russian, Ukrainian:** no national or surname statistics; Moscow and Ukraine's Ministry of Justice
  publish newborn top lists without full counts.
- **Turkish:** TÜİK's population portal gives only the top 30 per year, sex and province (2018 to 2025).
- **Arab countries:** none of Saudi Arabia, Egypt, Jordan, Morocco, Algeria, Tunisia or the UAE
  publishes name statistics; Levantine comes from Israel's register instead.

## Sources ruled out

- Name datasets built from the 2021 Facebook leak (such as the `names-dataset` package): leaked
  personal data, whatever the volume.
- Forebears and similar sites: modelled estimates with no reuse licence.
- Getting around access controls (Cloudflare challenges, API keys, internal endpoints of interactive
  tools): not done; such sources wait for proper access.
