# Sci-fi names

Makes up names for science fiction (aliens, hackers, and machines; worlds; ships and companies) and
adds the ones you pick to the encyclopedia.

## Using it

Three actions, on Project Home or an encyclopedia entry, each adding its names as a different kind
of entry:

- **Sci-fi characters,** added as people: aliens that sound harsh, flowing, hissing, or clipped,
  handles, and machine designations.
- **Sci-fi worlds,** added as places: names like the stars, colonies and stations, and catalogue
  designations.
- **Sci-fi things,** added as things: naval, lyrical, and working ships, and companies.

Each asks for a kind or style and how many (1 to 20, 8 by default). Characters and things also have
**Long form**, on by default: a fuller name, such as an alien's two names or a ship's name after its
prefix. Hammer keeps these for the next run while the project is open. The page lists the names
with a button for each; pressing one adds it to the encyclopedia, and the list comes back with that
name marked as added. More names makes a new list. A style with few words may give fewer names than
asked for.

## How names are made

Each action is a kind, and each kind a folder of styles, `resources/<kind>/<style>/`. A style's
`format.txt` holds the patterns its names follow, one a line, picked as often as each is there;
without one a name is `{names}`. In a pattern:

- `{list}` is a made-up name that sounds like those of `<list>.txt`: each letter one that follows the
  three before it somewhere in them, as often as it does there.
- `[list]` is one of its lines as it is.
- `#` is a digit, and `@` a capital letter.

A list is the style's, or else the kind's, for all its styles. A style's `long.txt` holds the patterns
of its long form, in which `*` is the name. Adding a style is a folder and an option in the manifest's
`style` field for the action. No name in a list comes up twice, and none made up is in the list it
sounds like.

## Development

Written in Kotlin. It asks for `entry.create` and nothing else. The page works as the other name
generators' do: an inline button per name, the page's `state` holding the list's seed, options, and
which names were added, and a page-level error when the encyclopedia refuses a name.

`worlds/names.txt` holds the names of stars and moons, which `tools/build.py` fetches: the stars' from
the International Astronomical Union's Catalog of Star Names, and the moons' from Wikidata. The other
lists are the plugin's own. `resources/NOTICE.txt` gives the sources and terms.
