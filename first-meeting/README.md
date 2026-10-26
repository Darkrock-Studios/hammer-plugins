# First meeting

Finds where two characters first meet: the first scene, in story order, with both of them in its
References, and the other scenes they share.

## Using it

Find where two characters first meet, on Project Home or a character's encyclopedia entry, asks for
two characters. Run from an entry, the first starts as that entry when it is a person.

It names the first scene they share, with the groups it is in, and lists the other scenes they share
in story order, with a button opening each of the first 10; the list names them all. Open opens the
first. When they share none, it says so, naming whichever of them is in no scene's References at all.
Back returns to the picks.

Each result says, in italics, that it goes by each scene's References in Scene Info. Hammer adds an
entry to a scene's References when the scene's text names it, and never takes one away, so a
character only mentioned counts until you dismiss it in Scene Info, and one there but never named is
missed until you add it.

## Development

Written in Kotlin. It asks for `entry.read` and `scene.tree` and nothing else.

- **Entity fields:** two required `entity` fields of `person` entries, on a page the plugin replies
  with rather than the manifest's, so it can give the first a default from the entry it is run from.
  Hammer shows nothing picked when that entry is not a person.
- **Errors:** the same character twice is refused under the second field. A failed operation, or a
  button for a scene the pair does not share, is an error for the whole page.
- **Open:** each scene's button, and the primary Open for the first, reply with `open` for the scene,
  which ends the run. The page's `state` holds the scenes found, and only those are opened.
- **Buttons:** one inline button per shared scene, the first 10 only, so a pair sharing a hundred
  scenes does not bury the page. A scene with the same name as another with a button is labelled with
  its groups too. Back has `validate = false`, and shows the picking page with the pair still picked,
  from `state`.
- **References:** it reads each character's `referencedBy` with `entry.read`, keeps the scenes in
  both, and orders them by a walk of `scene.tree`, which leaves out archived scenes. That takes two
  reads and the tree rather than every scene's text, and follows what the writer has dismissed or
  added in Scene Info, which a search for names cannot.

The manifest has no `keep`: a run is two picks, as quick to make again.
