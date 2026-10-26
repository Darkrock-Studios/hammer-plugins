# Style report

Reports how a story reads: reading ease, adverbs and filter words per thousand words, the share of
dialogue, and repeated words and phrases, for the whole story and each scene. Works with English,
French, German, Spanish, Italian, and Ukrainian projects.

## Using it

Style report, in the project menu, asks which scenes to report on; leave it empty for the whole story.
It shows its progress scene by scene, then replies with a document page, which can be saved as a note.
The report is written in the UI's language when that is one of the six, and English otherwise, with
that language's plurals and decimal comma.

## Settings

- **Filter words:** count verbs such as "felt" and "saw" that stand between the reader and the scene.
  On by default.
- **Each scene:** a section for every scene after the whole story. On by default.
- **Repeats in a scene:** how often a word comes up in one scene before that scene lists it, 3 by
  default. Each scene lists its 5 most repeated.
- **Repeats in the story:** how often a word comes up in the whole story before the report lists it,
  5 by default.
- **List length:** how many adverbs, filter words, and repeats each of the story's lists shows, 20 by
  default.

## How it counts

It reads the project's language through `project.info`, and counts by that language's rules and word
lists. A project with no language is counted as English.

| Language | Reading ease | Adverbs |
| --- | --- | --- |
| English | Flesch, with Flesch–Kincaid grade | "-ly" |
| French | Kandel–Moles | "-ment" |
| German | Amstad | None, as German marks no adverbs |
| Spanish | Szigriszt-Pazos | "-mente" |
| Italian | Gulpease | "-mente" |
| Ukrainian | None | None |

The formulas, with words per sentence (W/S) and syllables per word (Sy/W):

- **Flesch:** 206.835 - 1.015 W/S - 84.6 Sy/W, and grade 0.39 W/S + 11.8 Sy/W - 15.59.
- **Kandel–Moles:** 207 - 1.015 W/S - 73.6 Sy/W.
- **Amstad:** 180 - W/S - 58.5 Sy/W.
- **Szigriszt-Pazos:** 206.835 - 62.3 Sy/W - W/S.
- **Gulpease:** 89 + (300 sentences - 10 letters) / words.

What it counts:

- **Syllables** are vowel groups in each language's vowels. English and French drop a silent final "e"
  after a consonant, though English keeps the "e" of "able" and "little".
- **Words:** French and Italian split a word at an apostrophe, so "l'homme" counts "homme". Headings
  are left out, and markdown marks are stripped.
- **Sentences** end at `.`, `!`, `?`, or `…`, and any closing quotes or brackets after it, but not at a
  full stop after a title such as "Mr".
- **Adverbs** are words of five letters or more with the language's ending, less those its list of
  words that are not adverbs has ("family", "moment", "comente").
- **Filter words** are verbs of seeing, feeling, and knowing ("felt", "saw", "realized"; "sentit",
  "fühlte", "sintió", "sentì", "відчув") that put a character between the reader and the scene.
- **Dialogue** is every word inside straight or curly double quotes, curly single quotes, „ “, or
  guillemets either way round, in any language. French, Spanish, Italian, and Ukrainian also count a
  line opening with a dash (U+2014 or U+2013) as dialogue, each later dash moving between speech and
  narration.
- **Repeats** are words of four letters or more that are not in the language's common words.
  **Repeated phrases** are runs of three words, at least one of them uncommon, repeated within a
  scene.

The whole story's figures are the sum of its scenes' counts, so they need no second pass. A project
in a language without lists gets its words, sentences, and dialogue, and a note that the rest needs
lists.

## Development

Written in C, a 72 KB package.

- **Word lists** are resources, in `resources/<language>/`: `common-words.txt`, `not-adverbs.txt`,
  `filter-words.txt`, and `titles.txt`, one lower-case word a line, `#` for comments. They are read at
  the start of each run. A language's vowels, adverb ending, formula, and dash dialogue are in the
  `LANGUAGES` table in `style.c`.
- **`tools/not-adverbs.py`** builds the French, Spanish, and Italian `not-adverbs.txt`: the words
  among a subtitle frequency list's 100,000 most frequent that end in the adverb suffix but that the
  LibreOffice dictionary does not take for adverbs. It needs spylls and each language's frequency list
  and dictionary; name-checker's `tools/word-lists.py` says
  where to get them. English's list, and the rest, are the
  plugin's own. `resources/NOTICE.txt` gives the sources and licences.
- **Translations:** `messages.h` has the report in each language, and `locales/` translates its name,
  description, and settings.
- **Memory:** it counts in its own hash maps and arenas, reused scene to scene, so a whole novel is
  counted within the default 64 MiB of memory.
- **Cache:** each scene's counts are kept in the plugin cache under a hash of the scene's text, its
  language, and the word lists, so a run counts only the scenes that changed. Keys carry a format
  version (`counts/2/`).
- **Timings:** on a 300,000-word story, a first run measured about 7 seconds, and a run with nothing
  changed about 1, the time to read every scene.
