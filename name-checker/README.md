# Name checker

Keeps a story's names in line with its encyclopedia: underlines likely misspellings of a name and
names written in lower case as you write, and reports where each entry is mentioned and which names
have no entry. Works with English, French, German, Spanish, Italian, and Ukrainian projects.

## As you write

It underlines:

- **A slip of a name:** a capitalized word one edit from a name (two for a name of eight letters or
  more), starting with the same letter, a swap of neighbours counting as one edit. "Hermoine" for
  "Hermione".
- **A name in lower case:** "kaelen" for "Kaelen".

Each offers the name as the fix. A plural ("the Grangers") and a possessive count as the name, and a
word in capitals is left alone.

Only names the project language's word list does not know take part, so a name that is also a word
("Grace", "Harry") is left to spell check, and a word is never taken for a misspelled name. A name
must be capitalized in the encyclopedia and four letters or more to have its slips found. When spell
check takes the encyclopedia's names, as it does by default, a slip also gets spell check's own
underline.

It reads the encyclopedia's names and aliases on every check, so it follows edits to the
encyclopedia.

## The Names report

Names report, in the project menu, replies with a document page listing:

- **Each entry's mentions,** by name, alias, or a word of its name that is unique to it: how many
  mentions, in how many scenes, and the scene where it first appears, most mentioned first.
- **Entries never mentioned.**
- **Likely misspellings** of names, with how often each comes up, the first three scenes it is in,
  and the names it is close to.
- **Names with no entry:** up to 30 capitalized words of three letters or more that the word list
  does not know, seen more than once and at least once mid-sentence.

Headings are left out. A project in a language with no word list gets the mentions alone.

The report and the messages are in the UI's language when that is one of the six, with that
language's plural forms, and English otherwise; `locales/` translates its name and labels.

## Development

Written in Rust, a module of about 350 KB with a word list per language as resources.

**Word lists** are `resources/words/<language>.txt`, lower case and sorted by byte, one word a line;
the plugin checks any language with a list there, read the first time a project in it is checked and
kept. English is SCOWL's American and British lists (`resources/words/SCOWL-COPYRIGHT`).
`tools/word-lists.py` builds the French, German, Spanish, Italian, and Ukrainian lists: the words
among a subtitle frequency list's 200,000 most frequent that the LibreOffice dictionary accepts in
lower case. German also keeps the words its dictionary has capitalized as nouns, and Ukrainian only
words in Cyrillic. The script needs spylls and each language's frequency list and dictionary; its
docstring says where to get them, and `resources/words/NOTICE.txt` gives the sources and licences.

The check reads the encyclopedia through `entry.list`, using the diagnose request's `project`. The
check and the report share the name rules in `src/lib.rs`; `src/ui.rs` has the messages in each
language.
