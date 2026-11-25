# Harper grammar

English grammar and style checks from [Harper](https://writewithharper.com/), underlined as you
write. More thorough than [simple grammar](../simple-grammar/README.md), but large: it needs about
100 MB of memory.

## Using it

It checks English projects only; a project in another language gets nothing. The project's language
picks the dialect: `en-GB`, `en-UK`, `en-IE`, and `en-NZ` are British, `en-AU` Australian, `en-CA`
Canadian, `en-IN` Indian, and any other English, or no language at all, American. Spelling is left to
Hammer's own spell check, with Harper's turned off.

Style lints are suggestions, underlined more quietly than mistakes. Where several lints cover exactly
the same words, the one kept offers the others' fixes too.

## Settings

Each turns off a whole category, which groups Harper's finer kinds of lint. All are on by default.

- **Grammar:** agreement, word order, "a" or "an", words run together or split, and repeated words.
- **Word choice:** commonly confused words, misused phrases, malapropisms, eggcorns, nonstandard and
  regional forms, and typos.
- **Punctuation and capitalization**, with formatting.
- **Style:** wordiness, redundancy, readability, and every other kind.
- **Long sentences:** sentences over 40 words. A style lint, so it needs Style on.

A long sentence's lint covers the whole sentence, so it is kept apart when overlapping lints are pared
down; otherwise it would hide every other issue in the sentence. Harper's rules can be switched on and
off by name but take no parameters, so the 40-word limit is fixed.

## Development

Written in Rust on `harper-core`. `./build.sh harper-grammar` runs this folder's `build.sh`, which needs
wabt, Binaryen (on PATH, or its `bin` in `BINARYEN`), and python3; CI uses Binaryen 120.

The module is about 8 MB, 3 MB zipped. Harper builds its dictionary and rules for the project's dialect
on the first check, which measured about 16 seconds on desktop under chasm (0.6 natively, 5.7 on Android
compiled to DEX) and about 140 MB of memory, hence `limits.memory = 256`. Only one dialect's linter is
kept, so moving between projects of different dialects builds it again.

Loading the module measured about 1.1 seconds on desktop, and a paragraph after the first check about
0.4.
