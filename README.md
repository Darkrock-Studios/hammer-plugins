# hammer-plugins

Hammer's official runtime plugins for [Hammer](https://github.com/Wavesonics/hammer-editor), built on the
kits in hammer-plugin-sdk. Each directory is one plugin.

| Plugin | Language | What |
| --- | --- | --- |
| `simple-grammar` | C | Slips a spell checker passes, underlined in the editor (`[[diagnostics]]`), in English, French, German, Spanish, Italian, and Ukrainian: mixed-up words and phrases, French elision, a pronoun's verb that does not agree, punctuation, quotes and dialogue tags, and, when turned on in its settings, wordy and redundant phrases, repeated marks, and long sentences; its lists are resources, a folder per language; a 48 KB package, with native tests |
| `style` | C | Style report: readability, adverbs, filter words, dialogue, and repetition per scene and for the whole story, shown from the project menu (`[[actions]]` replying with a document page) for the whole story or the scenes chosen, with progress, and cached per scene; counts English, French, German, Spanish, Italian, and Ukrainian projects by their own rules and word lists (resources), writes the report in the UI's language, and has settings |
| `reading-level` | Kotlin | Reading level report: the level each scene and the whole story reads at, from early reader to dense, by sentence and word length as US school grades, with each scene's rare words and the scenes that stand out from the rest, shown from the project or scene menu (`[[actions]]` replying with a document page); counts English, French, German, Spanish, Italian, and Ukrainian projects by their own formulas, leaves encyclopedia names out of word length, ships each language's common words as resources (`resources/words/`), built from subtitle frequency lists by `tools/`, and caches each scene's counts |
| `plaintext` | C | Plain text export for submission forms, with scene break, italics, and paragraph settings; reads prose as Hammer parses it (`input = "prose"`) |
| `fantasy-names` | Kotlin | Made-up names in a fantasy style (elvish, dwarvish, imperial, orcish, and more), each with a button adding it to the encyclopedia as a person; most styles' names sound like an old language's, whose real names ship as resources (`resources/styles/`), built from Wiktionary and Wikidata by `tools/` |
| `real-names` | Kotlin | Real names by culture and decade of birth, picked as often as they were common, each with a button adding it to the encyclopedia as a person; ships name lists with counts as resources (`resources/names/`), built from public data by `tools/` |
| `scifi-names` | Kotlin | Made-up names for science fiction, an action for each of characters (aliens, handles, machines), worlds, and things (ships, companies), each name with a button adding it to the encyclopedia as a person, place, or thing; names follow each style's patterns over its lists (`resources/`), and worlds sound like the stars and moons, whose names `tools/` fetches |
| `character-interview` | Kotlin | Interviews a character from Project Home, a page of questions at a time, then writes the answers into a new person entry and opens it; the questions are a quick set, a full one, or those of the confession album Proust filled in around 1890 (public domain, in our own English wording), each a markdown file of pages and questions (`resources/sets/`), and the run is kept when the dialog is closed |
| `first-meeting` | Kotlin | Finds the first scene, in story order, with both of two characters in its References, run from Project Home or from a character's entry (picked first); picks the pair by name (`entity` fields), names the scene with its chapter, lists the other scenes they share with a button opening each of the first ten, and when they share none, says if either is in no scene's References at all |
| `harper-grammar` | Rust | English grammar on [Harper](https://github.com/Automattic/harper), underlined in the editor, with settings for each category of check and for long sentences; pre-initialized with Wizer |
| `languagetool` | Rust | Grammar, punctuation, and style from [LanguageTool](https://languagetool.org), underlined in the editor, through its free service, Premium with your own API key, or your own server; sends the text over HTTP (`permissions.http`), in batches under the service's limits, and caches each paragraph's matches by its text so a scene opened again is not sent again |
| `name-checker` | Rust | For names from the encyclopedia: underlines slips of a name and names in lower case in the editor, and reports each entry's mentions, likely misspellings, and names with no entry; ships word lists as resources (`resources/words/`) for English, French, German, Spanish, Italian, and Ukrainian, and checks any language with a list there |
| `mcp` | Kotlin | An MCP server for AI agents, as the `hammer mcp` command: reads your projects and, when its settings allow, organizes and edits the material around the prose, but never writes the prose itself; see its [README](mcp/README.md) |

## Building

Check out hammer-plugin-sdk beside this repo, then `./build.sh` builds every plugin into
`<plugin>/build/<plugin>.hammerplugin`, or `./build.sh style` one. `./test.sh` runs every plugin's unit
tests. Each language needs:

- C: clang with the wasm32 target and lld (`sudo apt install clang lld`), and zip.
- Rust: [rustup](https://rustup.rs) and `rustup target add wasm32-unknown-unknown`. `harper-grammar` also
  needs wabt, Binaryen (on PATH, or its `bin` in `BINARYEN`), python3, and `cargo install wizer
  --all-features`.
- Kotlin: a JDK; `./gradlew` fetches Gradle.

## Installing

In Hammer on desktop: Settings, Plugins, Install plugin, pick the `.hammerplugin`, approve its
permissions, and restart Hammer.

## License

MIT; see [LICENSE](LICENSE). Some word lists are built from other sources and shared under their
terms, which ship with them: see `name-checker/resources/words/NOTICE.txt`,
`style/resources/NOTICE.txt`, `reading-level/resources/NOTICE.txt`,
`fantasy-names/resources/styles/NOTICE.txt`,
`scifi-names/resources/NOTICE.txt`, and
`real-names/resources/names/NOTICE.txt`, whose Chinese lists are for non-commercial use only.
