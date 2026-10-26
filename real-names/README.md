# Real-world names

Suggests real names from a culture and a decade of birth, picked as often as they were common then,
and adds the ones you pick to the encyclopedia as people.

## Using it

Generate real-world names, on Project Home or an encyclopedia entry, asks for:

- **Culture:** one of 26, from American to Ukrainian.
- **Born in:** any time, or a decade from the 1880s to the 2020s.
- **Given names:** any, feminine, or masculine.
- **Names:** common (only from the most common names, which half the people have between them), a
  realistic mix (the default, each name as often as it is common), or rare (any of the rest, each as
  often as another).
- **How many:** 1 to 20, 8 by default.
- **With surnames:** on by default.

Hammer keeps these for the next run while the project is open. The page lists the names with a
button for each; pressing one adds it to the encyclopedia as a person, and the list comes back with
that name marked as added. More names makes a new list.

A decade uses the culture's list for the nearest decade within 30 years that has one, and its list of
all years otherwise. Names are written in the culture's order: Chinese, Japanese, and Korean surname
first, and Spanish and Mexican with two surnames.

## Development

Written in Kotlin. It asks for `entry.create` and nothing else. The page works as the other name
generators' do: an inline button per name, the page's `state` holding the list's seed, options, and
which names were added, and a page-level error when the encyclopedia refuses a name.

Each culture is a folder, `resources/names/<culture>/`, of lists with a name and how many people have
it on each line, a tab between them, most common first: `feminine.txt` and `masculine.txt`, optionally
`feminine-<decade>.txt` and `masculine-<decade>.txt` for those born in a decade, such as
`feminine-1950.txt`, and optionally `surnames.txt`, or `surnames-feminine.txt` and
`surnames-masculine.txt` where they differ. A full name is `{given} {surname}` unless `format.txt`
says otherwise, such as `{surname} {given}`. Adding a culture is a folder and an option in the
manifest's `culture` field.

`tools/` builds the lists, a script for each culture with published statistics (American, Chinese,
English, French, Irish, Norwegian, Polish, Scottish) and `wikidata.py` for the rest, from the people
Wikidata has born since 1880. `resources/names/NOTICE.txt` gives each list's source and terms; the
Chinese lists are for non-commercial use only.
