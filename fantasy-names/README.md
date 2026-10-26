# Fantasy names

Makes up names in a fantasy style, and adds the ones you pick to the encyclopedia as people. Most
styles sound like the names of an old language.

## Using it

Generate fantasy names, on Project Home or an encyclopedia entry, asks for:

- **Style:** Elvish (Welsh), High Elvish (Finnish), Dwarvish (Old Norse), Old kingdom (Old English),
  Imperial (Latin), Mythic (Ancient Greek), Highland (Gaelic), Forest (Slavic), or Orcish.
- **Given names:** any, feminine, or masculine.
- **How many:** 1 to 20, 8 by default.
- **With second names:** a patronymic, byname, or clan in the style's own form, such as "ap" in
  Elvish, "-sson" and "-sdottir" in Dwarvish, or "of" a city in Mythic. On by default.

Hammer keeps these for the next run while the project is open. The page lists the names with a
button for each; pressing one adds it to the encyclopedia as a person, and the list comes back with
that name marked as added. More names makes a new list. No name it makes up is one of the style's real
names or one of today's common American given names, and no part of a name comes up twice in a list.

## How names are made

Each letter of a made-up name is one that follows the three before it somewhere in the style's real
names, as often as it does there, and a name is kept only when it is about as long as most of them
(all but the shortest and longest tenths).

Each style is a folder, `resources/styles/<style>/`, of real names for its made-up ones to sound
like, a name on each line: `feminine.txt` and `masculine.txt`, and any others its format names. A full
name follows `format-feminine.txt` or `format-masculine.txt`, or `format.txt` for both, such as
`{given} ap {masculine}` or `{given} of {places}`: `{given}` is a name of the kind asked for, and any
other part a name like those of the list of that name. `resources/styles/everyday.txt` holds today's
names, in lower case, which are never made up. Adding a style is a folder and an option in the
manifest's `style` field.

## Development

Written in Kotlin. It asks for `entry.create` and nothing else.

- **Pages and buttons:** an inline button per name; pressing one adds the entry and replies with the
  list again, that name marked and its button gone. More names is a footer button.
- **State:** the page's `state` holds the list's seed, its options, and which names were added, so
  each call rebuilds the same names.
- **Errors:** a name the encyclopedia refuses replies with a page-level error, leaving the list as it
  is.

`tools/build.py` builds every style but Orcish, which is written by hand, from Wiktionary's
categories of given names and from Wikidata, and `everyday.txt` from the US Social Security
Administration's baby names. It keeps each answer in its working directory, so a second run asks only
for what is missing. `resources/styles/NOTICE.txt` says what each list holds and where it is from.
