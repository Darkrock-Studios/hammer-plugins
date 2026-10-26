# Simple grammar

Underlines slips a spell checker passes, since every word in them is a real one: "could of", "le
arbre", "they was", doubled words, and spacing, capitals, and dialogue punctuation. Works in English,
French, German, Spanish, Italian, and Ukrainian, and is small and fast.

## Settings

Its settings turn groups of rules on and off:

| Group | Default | Rules |
| --- | --- | --- |
| Word mistakes | On | Repeated words, mixed-up words and phrases ("could of", "your welcome", "sneak peak"; "quelque soit"; "seid wann", "das selbe"; "a sido", "de el"; "un amica", "c'è la faccio"; "приймати участь"), and each language's own: "a" and "an" and "better then" in English, elision in French ("le arbre" for "l'arbre") |
| Verb agreement | On | A pronoun with a verb form that does not agree ("they was", "il sont", "wir hat", "ellos está", "loro ha", "вона був"), but not after a word that makes it right: the subjunctive ("if he were"), a subject of several ("you and I are"), an auxiliary ("does he have"), or a preposition ("para él son"). Pronouns that are objects too, such as "you", "it", "nous", and "sie", are left out, and so is speech |
| Agreement in dialogue | Off | Verb agreement in speech too: quotes, and paragraphs that open with a dash. Needs Verb agreement on |
| Punctuation and capitalization | On | A lower-case "i" in English, spacing around punctuation, French's space before a colon, doubled marks (",,", ".." but not an ellipsis), unmatched parentheses, and a sentence starting in lower case after a full stop |
| Dialogue | On | `"Wait." she said` wants a comma and `"Wait," She said` lower case, in English. In every language, a quote run into the next word wants a space, and a quote a comma follows ends without a full stop: English moves the comma in (`"Wait," she said`), and the others drop the stop (`„Warte“, sagte sie`) |
| Wordy phrases | Off | "in order to" for "to", "dans le but de" for "pour", "zum jetzigen Zeitpunkt" for "jetzt" |
| Redundant phrases | Off | "free gift" for "gift", "monter en haut" for "monter", "subir arriba" for "subir" |
| Repeated marks | Off | "!!" and "?!?", but not "?!" |
| Long sentences | Off | More than a set number of words, 40 unless changed (10 to 200) |

## What it leaves alone

The rules flag only what they are sure of:

- "had had", "that that", and "nous nous" pass.
- "a" and "an" are left alone before words starting with "h" or "u", acronyms, and single letters.
- French elision is left alone before an "h", which may be aspirated, after a hyphen ("prends-le avec
  toi"), and before a capital's letter ("de A à Z").
- "better then." at the end of a sentence is the time, and so is "then" before "and" or "but".
- A sentence may go on in lower case after "?" and "!" ("alas! either"), and after an abbreviation
  or an initial.
- Two spaces after a sentence are a typing habit, not a slip.
- A phrase's first word matches in either case and the rest as written, so "de el" passes "de El
  Salvador" and German keeps its nouns' capitals.

Fixes use the paragraph's apostrophes: curly where it has curly quotes or apostrophes, and
Ukrainian's U+02BC where it has one. Messages are in the UI's language when that is one of the six,
and English otherwise.

## Development

Written in C on the C kit's `hammer_diagnose.h`, which reads the request and writes the reply, so the
plugin is its rules; a 48 KB package. Every rule is one pass over a paragraph with small tables, and a
word skips, in one step, the phrases of a list whose first word it is not. It returns byte offsets,
which Hammer turns into the editor's UTF-16 ranges. `test.c` holds its native tests, which
`./test.sh` runs.

**Word lists** are resources, a folder per language in `resources/<language>/`, one entry a line, `#`
for comments. The phrase lists (`mistakes.txt`, `wordy.txt`, `redundant.txt`, `agreement.txt`) are
`phrase = fix`, where a fix may offer several joined by ` | `, and a `*` stands for a possessive from
`possessives.txt`. Each file's header says what it holds. The lists of the language last checked are
kept in a 64 KB buffer of 2,048 entries, read once and kept for as long as projects in that language
are checked. A project with no language is checked as English, and one in a language with no lists
gets nothing.

**Adding a language** is a folder of lists in `resources/`. Each rule runs on the lists the folder
has: elision with an `elision.txt`, dialogue tags with a `tag-verbs.txt` and `pronouns.txt`. English's
own rules ("a" and "an", "then" for "than", a lone "i") and French's spacing are switched on by flags
in the `TYPOGRAPHY` table in `simple-grammar.c`. That table also gives each language's quotation marks
besides straight ones: English has curly doubles, German „ and “, French, Spanish, and Italian curly
doubles and guillemets, and Ukrainian guillemets and „ “. A language not in it gets curly doubles.
Add the language to the manifest's `languages` too, and its messages to `messages.h` and a
`locales/<tag>.toml` if the plugin should speak it.

**Timings:** 40 paragraphs of 74 words measured about 110 ms under chasm with every group on, against
25 to 30 ms a paragraph for [Harper grammar](../harper-grammar/README.md).
