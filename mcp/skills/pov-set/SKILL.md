---
name: pov-set
description: Work out whose point of view each scene in a Hammer project is told from, through the Hammer MCP server, and tag the scene pov-name with the writer's approval. Use when the writer asks to set, tag, or label POV, or to prepare scenes for pov-check, for one scene, a chapter, or the whole book. Never writes prose.
---
# POV set

Decide the point-of-view character of each scene and record it as a `pov-name` tag, which `pov-check`
and the writer's own filters read. You propose; the writer approves; only then do you tag.

## Never write prose

Do not suggest rewrites or comment on how the POV is handled. Finding slips is `pov-check`'s job; you
only say whose scene it is. Hammer's AI is a tool for the writer, not a replacement. You change only
scene tags.

## Scope

Each scene is decided on its own, so never read more than was asked for.

1. If the project isn't named, call `project_list` and ask which one.
2. Call `scene_tree` for the scenes and groups in story order, with word counts. Groups are chapters
   or parts. Resolve what the writer asked for to a group id or a list of scene ids. If they named no
   scene or chapter, ask which; don't assume the whole book.
3. Say how much that is ("14 scenes, about 38,000 words") before reading. Up to about 30,000 words,
   read it in one go and propose once. Over that, wait for a go-ahead, then work one top-level group
   at a time (scenes outside any group count as one more): propose and apply for it before reading the
   next, so stopping partway still leaves finished chapters. A group with nothing to propose needs no
   answer; say so and carry on.

## Gather

1. Call `entry_list` with `type: "person"` for character names and aliases.
2. Call `tag_list` for the tags the project already uses, to see which `pov-` tags exist.
3. Read with `scene_read_many`, passing `group` (a group's id) or `ids`, or neither for the whole
   book. Each scene comes with its `path` of group names and its `meta` (`tags`, `outline`, `notes`).
   When the result has a non-null `next`, call again with `after: next`. For a single scene,
   `scene_read` is enough.

Scenes that are already tagged are read too, so a tag the text disagrees with can be reported.

Decide each page before reading the next, and keep only your proposals, not the text of earlier pages.

## Decide each scene

A scene break (`---`, `***`, `* * *`, `#`, or similar on its own line) may switch POV. Decide each
section, and give the scene one tag per POV character across its sections.

Use the first of these that answers it, and record which one did:

1. **Already tagged:** the scene has `pov-` or `pov_` tags. Leave them; never change an existing POV
   tag. If most of the scene reads as another character's, or as no one character's, report it under
   Mismatch. A few stray lines in another head are not a mismatch. If a whole section, set off by a
   break, is another character's and the scene has no tag for them, propose that tag as an addition.
2. **Metadata:** the outline or notes name the POV character.
3. **Inferred:** the first-person narrator, or the one character whose thoughts and sensations the
   narration reports directly. Take one short quote that shows it.

A scene that is clearly one character's, with a few stray lines in another head, is that character's.
Don't add a tag for the stray lines.

Leave a scene untagged, and list it as unsure with your reason, when:

- it reads as deliberate omniscient narration;
- it moves between heads within a section too much to call;
- the narrator is first person and never named;
- you can't tell.

## The tag

`pov-` followed by the character's name. A new tag is all lower-case. Use the first of these that
applies:

1. A `pov-` tag the project already has for this character, exactly as it is, capitals and all: its
   name part is the entry's name or one of its aliases, whatever the case. If that name part fits more
   than one person's entry, the tag doesn't say whose it is: ask the writer before using it for
   either.
2. The name the narration calls them by, when that is the entry's name or one of its aliases and no
   other person's entry shares it: `pov-mara` for Mara Vell.
3. The entry's full name, or with no entry, the name as the scene gives it.

Hammer tags hold only letters, digits, `-`, and `_`, so turn spaces into `-` and drop anything else:
`pov-mary-ann`, `pov-obrien`. Use the same tag for the same character everywhere.

## Check your quotes

Confirm each quote appears verbatim in the scene's `markdown`. If it doesn't, find one that does or
move the scene to unsure. A quote may leave out markdown marks such as `*`, write curly quotes
straight, and write a run of spaces as one, and differ in nothing else. `text_find` checks one: the
quote as `term`, the scene's id in `ids`, `wholeWord: false`, and `caseSensitive: true`. It never
matches across a paragraph break: check a quote that runs over one a paragraph at a time, and see in
the text you read that those paragraphs follow one another in that order. No match means the quote
isn't there.

## Propose

```
### <group name>
- <scene name> (id <id>): pov-<name> (inferred) "<exact quote>"
- <scene name> (id <id>): pov-<name>, pov-<name> (inferred, switches at a break) "<quote>" / "<quote>"
- <scene name> (id <id>): pov-<name> (from outline | from notes)
- <scene name> (id <id>): add pov-<name> (tagged pov-<name>, a section is <Name>'s) "<exact quote>"
- Unsure <scene name> (id <id>): <why>
- Mismatch <scene name> (id <id>): tagged pov-<name>, reads as <Name | no one character>: "<exact quote>"
```

Leave out a group with nothing to report. Then, if any scenes were already tagged, give them as a
count per tag ("pov-mara: 5 scenes"), and end with totals: to tag, unsure, mismatch, already tagged.
If there is something to tag, ask the writer which to apply: all of them, a chapter's, or single
scenes, and take any corrections. If there is nothing, say so.

## Apply

Only what the writer approves. Call `scene_meta_write` with `tags` as every tag the scene already has
plus the new ones, so none is lost, and leave `outline` and `notes` out. Report what was tagged.

That tool exists only when the MCP plugin's "Let AI agents make changes" setting is on. If it's
missing, say so rather than asking the writer to change it, and give the approved tags so they can add
them under Tags in each scene's Metadata panel.