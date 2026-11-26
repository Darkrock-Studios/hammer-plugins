---
name: true-up-encyclopedia
description: Check a Hammer project's encyclopedia against its scenes through the Hammer MCP server - people, places, and things on the page with no entry, names an entry lacks as an alias, entries nothing mentions, and entries the page contradicts. Use when the writer asks to check, audit, or true up the encyclopedia, find missing entries, or find unused ones. Adds entries and aliases only with the writer's approval. Never writes prose or entry text.
---
 # True up encyclopedia

The encyclopedia is the writer's record of the story's people, places, things, events, and ideas. It
falls behind the manuscript: a character appears who was never entered, one goes by a nickname the
entry doesn't list, an entry outlives the thing it describes. You compare the two and report the
differences, with quotes.

## Never write or invent

- Never write or rewrite prose.
- Never write an entry's text. A new entry is a name and a type, nothing more; describing it is the
  writer's work.
- Never choose between spellings or decide what a thing is called. Report what the page uses.

## Scope

1. If the project isn't named, call `project_list` and ask which one.
2. Call `entry_list` for every entry's id, name, type, and aliases.
3. Call `scene_tree` for the scenes and groups in story order, with word counts.

There are four checks. Settle which the writer wants and over what:

- **Missing entries** and **missing aliases** need scenes read. If the writer named no scene or
  chapter, ask which.
- **Disagreements** needs the same scenes, and every entry that appears in them read with
  `entry_read`, one call each.
- **Unused entries** is about the whole book, and needs no reading: `text_count` counts every
  entry's name and aliases across it in a call or two.

Say how much that is before starting ("14 scenes, about 38,000 words, and 60 entries to read"). Over
about 30,000 words, or over about 100 `entry_read` calls, wait for a go-ahead.

## Read

For Disagreements, read the entries first: `entry_read` each one that could appear, and keep one line
per entry of what it states as fact (appearance, age, family, rank, where a place is, what a thing
does). You need these beside you while reading scenes.

Read scenes with `scene_read_many`: `group` (a group's id) or `ids`, or neither for the whole book.
When `next` is non-null, call again with `after: next`. Finish each page's rows before reading the
next, and keep only rows, not the text of earlier pages. Go by each scene's `markdown`, not the
outline or notes in its `meta`.

## Missing entries

Note every named person, place, thing, event, or idea of the story's world that matches no entry name
or alias. Named means a proper name, an invented term, or a title used as a name: "Captain Reyes",
"the Grey Keep", "ashroot", "the Duchess". Not "the innkeeper" or "the old road", however often they
come up.

Leave out real-world names the story only mentions in passing (a city, a brand, an author). If you
can't tell whether a word is the story's own ("Midsummer"), list it with a note. If words match an
entry but plainly mean something else (a tavern's name used of an actual lantern), that is neither a
missing entry nor a use of the entry.

Keep one row per name, adding to it as the name comes up again:

```
<name as written> | <person|place|thing|event|idea, or unclear> | <scenes it appears in> | "<exact quote from its first appearance>"
```

Two spellings of what looks like one name are two rows with a note; don't merge them. Report
everything, recurring names first.

## Missing aliases

A name on the page that plainly means an existing entry, and that Hammer's matching would miss. Hammer
finds a scene's references by matching entry names and aliases, so a missing alias means missed
references.

Report:

- a nickname or short form: "Mags" for Margaret Ashby;
- a surname or given name used alone, or with a title ("Miss Vell"), when the entry lists only the
  other. Report the form as the page has it;
- initials or a signature: "E.V.";
- a word used in place of the name, capitalised as one: "Father", "the Captain".

Don't report:

- a form that already contains the entry's name or an alias, ignoring a leading "the": "Master Holm"
  where "Holm" is an alias;
- an ordinary description, even if it means them every time: "the clerk", "the office", "her father".

Only when the scene makes plain who or what is meant. If the form, or the name inside it, would also
fit another entry ("Vell" or "Miss Vell", with two Vells in the encyclopedia), report it with that
warning: the bare name can't be added as an alias without matching both.

Keep one row per alias, with the scenes it appears in.

## Unused entries

An entry is unused when nothing in the scenes names it, by name or any alias. Capitals and a leading
"the" don't matter: "the harbor seal" names The Harbor Seal.

Call `text_count` with every entry's name and aliases as `terms`, up to 50 a call, and neither
`group` nor `ids`. It counts whole-word uses in the scenes' text, ignoring capitals, so "the Gull"
doesn't count "the gulls". An entry whose name and aliases all come to zero is unused.

Without `text_count`, this check means reading the whole book and noting each entry you see named.
`search` won't do: it matches inside longer words and matches scene titles.

For an unused entry, `entry_read` gives `referencedBy`: the ids of scenes that still confirm a
reference to it. Name them from `scene_tree`; those references are stale.

If an entry's name never appears and it is found only through an everyday alias ("the letter", "the
charter"), it is not unused: list it apart, as named only by an alias.

Unused is not wrong: an entry may be backstory, or for a part not yet written. Report it and leave it.
Never propose deleting one.

## Disagreements

Compare what each entry states as fact with what the scenes you read state. Flag only a plain
contradiction, with both quotes. The entry saying more than the page, or the page more than the entry,
is not one. Nor is something the two could both be right about.

Which side is right is the writer's call. Two scenes that disagree with each other are outside this
skill; if you notice it, name the scenes in one closing line.

## Check your quotes

Confirm every scene quote appears verbatim in its scene's `markdown`, and every entry quote in the
entry's text. Drop any finding whose quote you can't find. A quote may leave out markdown marks such
as `*`, and nothing else. `text_find` checks one exactly: the quote as `term`, the scene's id in
`ids`, and `wholeWord: false`. No match means the quote isn't there. Never quote from a `search`
snippet.

## Report

```
## Missing entries
- <name> (<type, or type unclear>): <n> scenes, first in <scene name>: "<exact quote>". <note, if any>

## Missing aliases
- "<alias>" for <entry name>: <n> scenes, first in <scene name>: "<exact quote>". <Also fits <entry name>>

## Unused entries
- <entry name> (<type>): not named in any scene. <Still referenced by <scene name> | No references>

## Named only by an alias
- <entry name> (<type>): its name never appears; found only as "<alias>".

## Disagreements
- <entry name>: entry says "<exact quote>"; <scene name> says "<exact quote>". <note, if any>
```

Give only the sections for the checks you ran, with "none" under one that found nothing. End with
totals of each kind and what was read: which scenes, how many entries. If only part of the book was
read, say that missing entries, aliases, and disagreements cover only that part.

## Apply

Only what the writer approves, item by item or a whole section:

- **Add an entry:** `entry_create` with the `name` exactly as the writer confirms it, the `type` they
  confirm, and `text` as an empty string. If the type is unclear, ask; don't pick.
- **Add an alias:** `entry_read` the entry, then `entry_update` with its `text` unchanged and
  `aliases` as the existing ones plus the new one. Not one that also fits another entry, unless the
  writer says so knowing that.

Unused entries and disagreements are never applied: they are for the writer to act on. Stale
references are `true-up-references`' job.

Those tools exist only when the MCP plugin's "Let AI agents make changes" setting is on. If they're
missing, say so rather than asking the writer to change it; the report stands on its own.