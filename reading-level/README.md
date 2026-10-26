# Reading level

Reports the level each scene and the whole story reads at, from early reader to dense, with each
scene's rare words and the scenes that read well above or below the rest. Works with English, French,
German, Spanish, Italian, and Ukrainian projects.

## Using it

Reading level, in the project menu or a scene's, asks which scenes to report on, starting with the
scene it is run from; leave it empty for the whole story. It shows its progress, then replies with a
document page:

- **Whole story:** its level and grade, its words, sentences, words per sentence, share of rare words,
  and share of dialogue; how many scenes are at each level; the scenes that **stand out**, 2 grades or
  more from the story's median (up to 10, once three scenes have a steady level); and its 15 most used
  rare words.
- **Scenes:** a table of each scene's level, grade, words, words per sentence, rare words, and
  dialogue, and each scene's 8 most used rare words.

A report on one scene shows that scene alone. A scene of fewer than 30 words gets no level, and one
of fewer than 150 a rough one, marked ≈, left out of the median and the stand-outs.

| Level | US school grades |
| --- | --- |
| Early reader | below 2 |
| Chapter book | 2 to 4 |
| Middle grade | 4 to 7 |
| Teen and up | 7 to 12 |
| Dense | 12 and up |

## How it counts

Levels go by sentence and word length, as US school grades; they measure how the text reads, not what
it is about. Each language has its formula:

| Language | Grade | Shown beside it |
| --- | --- | --- |
| English | Flesch–Kincaid | |
| French | Flesch–Kincaid with Kandel and Moles's weights | Kandel–Moles reading ease |
| German | Wiener Sachtextformel (the first) | |
| Spanish | Flesch–Kincaid with Szigriszt-Pazos's weights | Szigriszt-Pazos reading ease |
| Italian | Flesch–Kincaid with Franchina and Vacca's weights | Gulpease |
| Ukrainian | Flesch–Kincaid with Oborneva's Russian weights, as Ukrainian has none of its own | Oborneva reading ease |

A weighted Flesch–Kincaid weighs sentence and word length as the language's reading ease weighs them
against Flesch's own, which is how Oborneva made Russian's Flesch–Kincaid from her reading ease.
Grades are at least 0. A project with no language is counted as English, and one in another language
gets its words, sentences, and dialogue.

- **Syllables** are vowel groups in each language's vowels, less a silent final "e" in English and
  French.
- **Rare words** are those outside the language's most used words in film and TV subtitles.
- **Names** count toward a sentence's length but not toward word length or rarity: a reader meets
  "Aelindrathor" once and knows it after. A name is an uncommon word of an encyclopedia entry's name or
  aliases, or, outside German, one a scene writes with a capital somewhere other than the start of a
  sentence or quotation.
- **Sentences** end at `.`, `!`, `?`, or `…` and any closing marks after it, but not at a full stop
  after a title such as "Mr". Lines run on into one paragraph until a blank line, as markdown reads
  them.
- **Dialogue** is every word inside straight or curly quotes, „ “, or guillemets either way round. In
  French, Spanish, Italian, and Ukrainian, a paragraph opening with a dash (U+2014 or U+2013) is
  dialogue too, each later dash moving between speech and narration.

## Development

Written in Kotlin. The scene picker is a page of the plugin's own rather than the manifest's, so it
can start with the scene run from. Kotlin/Wasm's `Regex` is slow in Hammer, so it reads text by hand.

- **Word lists** are resources: `resources/words/<language>.txt`, each language's common words, most
  frequent first, and `resources/titles/<language>.txt`, the titles a full stop does not end a
  sentence after. `tools/words.py` builds the word lists from FrequencyWords' subtitle frequency
  lists: English keeps its 20,000 most frequent words, since subtitles are speech, which seldom uses
  narration's past tenses ("swam", "whispered"), and those rank between 10,000 and 20,000. The others
  keep as many as cover the same share of their list's occurrences, since a language that inflects
  more spreads one word over more forms. `resources/NOTICE.txt` gives the source and licence.
- **Formulas** are in `Languages.kt`, and counting in `Counting.kt`.
- **Cache:** each scene's counts are kept in the plugin cache under a hash of its text, its language,
  and the encyclopedia's names, so a run counts only the scenes that changed.
- **Memory:** Kotlin/Wasm's objects live in chasm's guest heap, and a call holds all it allocates
  until it returns. The report measured 192 MiB of it on a 100,000-word novel and 384 MiB on a
  300,000-word one.
