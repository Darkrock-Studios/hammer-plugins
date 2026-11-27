---
name: outline-check
description: Compare each scene's outline with what the scene actually does, in a Hammer project through the Hammer MCP server, and report where they have drifted apart. Use when the writer asks to check outlines against scenes, whether a scene follows its outline, or which outlines are out of date, for one scene, a chapter, or the whole book. Can bring an outline up to date with the page, only when the writer asks. Never writes prose.
---
# Outline check

Every Hammer scene can carry an outline: the writer's note of what the scene is meant to do. Scenes
change as they are written and the outline is left behind. You read both and report where they differ.
Which one is right is not yours to say: the outline may be stale, or the scene may not yet do what it
was meant to.

## Never write prose

Do not suggest rewrites or changes to a scene, and don't say which side should give way. Hammer's AI
is a tool for the writer, not a replacement.

## Scope

Each scene is checked on its own, so never read more than was asked for.

1. If the project isn't named, call `project_list` and ask which one.
2. Call `scene_tree` for the scenes and groups in story order, with word counts. Groups are chapters
   or parts. Resolve what the writer asked for to a group id or a list of scene ids. If they named no
   scene or chapter, ask which; don't assume the whole book.
3. Say how much that is ("14 scenes, about 38,000 words") before reading. Up to about 30,000 words,
   read it in one go. Over that, wait for a go-ahead, then work one top-level group at a time (scenes
   outside any group count as one more), giving each group's findings as you finish it and carrying
   straight on to the next.
4. Read with `scene_read_many`, passing `group` (a group's id) or `ids`, or neither for the whole
   book. Each scene comes with its `path` of group names and its `meta`, where `outline` is the
   outline. When the result has a non-null `next`, call again with `after: next`. For a single scene,
   `scene_read` is enough.

Check each page before reading the next, and keep only your findings, not the text of earlier pages.

## Compare

Break the outline into its separate points: each thing it says happens, is revealed, is decided, or
who is there. For each point, find it in the scene:

- **On the page:** the scene does it, or near enough that a reader would say so. No finding.
- **Missing:** the outline says it and the scene doesn't.
- **Different:** the scene does it another way: another character, place, order, or outcome.

Then look the other way:

- **Not in the outline:** the scene leaves the story somewhere the outline doesn't say. Flag it only
  when, by the end of the scene, something is known, decided, done, or lost that wasn't before, and a
  later scene could depend on it: a reveal, a decision, an agreement, a death, a verdict.

One finding per turn of the story: a reveal with several proofs is one finding, and so is a verdict
with several consequences; name them together. Known means known to a character or to the reader.

How it is said, the steps on the way there, and who else is in the room are not findings, unless the
outline itself lists such things. Judge an outline by what it sets out to say: a one-line outline is
not wrong for leaving out the middle of the scene.

A scene with no outline gets no findings: list it as having none. An outline whose scene has no text
is a plan, not a drift: list it as unwritten.

Each scene is checked against its own outline only. Two scenes that disagree with each other are not
this skill's business. If a Missing point is plainly on the page of another scene you read, add where
to the finding: the outlines may have slipped out of step with the scenes.

## Check your quotes

Different and Not-in-the-outline findings quote the scene exactly; Missing and Different quote the
outline point. Confirm each scene quote appears in the scene's `markdown`, and drop any finding whose
quote you can't find. Write a quote as a reader sees the text: without the `*`, `_`, `~~`, or `==`
around styled words, the `#` or `>` that starts a line, or the backslash before an escaped character.
Every other character stays as the scene has it, a backtick or apostrophe used as a quotation mark
included, though curly quotes may be written straight and a run of spaces as one. `text_find` checks
one: the quote as `term`, the scene's id in `ids`, `wholeWord: false`, and `caseSensitive: true`. It
never matches across a paragraph break: check a quote that runs over one a paragraph at a time, and
see in the text you read that those paragraphs follow one another in that order, with nothing left out
between them. After no match, find a few plain words of the passage with `text_find` and take the
quote from the `context` it returns. If none holds the passage, the quote isn't there. Quote the words
that show the thing happening, not only the nearest short line.

## Report

Per scene with findings:

```
### <scene name> (id <id>)
- Missing: outline says "<outline point>"; not in the scene. <It is in <scene name>, if it is>.
- Different: outline says "<outline point>"; the scene has "<exact quote>"
- Not in the outline: "<exact quote>" (<what happens, in a few plain words>)
```

Then one line per kind, listing the scenes, leaving out a kind with none: match their outline, no
outline, not yet written. End with totals: findings of each kind, and scenes of each kind.

If you noticed something another skill covers, such as a point-of-view slip or two scenes that
disagree, say what and in which scenes in one closing line, and leave it there.

## Updating an outline

Never by default: the writer may mean to change the scene instead. This needs `scene_meta_write`,
which exists only when the MCP plugin's "Let AI agents make changes" setting is on. If you have it,
you may say after the report, in one line, that you can bring outlines up to date with the page. If
you don't and the writer asks, say the setting is off rather than asking them to change it.

When they ask, for the scenes with findings unless they name others:

1. Write the outline as a plain statement of what the scene does on the page, in the form the existing
   outline uses (a sentence, a list of beats). Keep every point of the old outline that still holds,
   in its own words, and add each finding as briefly as the old outline would have put it. It may
   grow; it shouldn't change register. Nothing the scene doesn't do, no judgement, and nothing about
   what the writer meant by it.
2. Show each old outline with the new one under it and wait for approval, scene by scene or all at
   once. If the writer rewords one, theirs is the one to save.
3. Call `scene_meta_write` with the `project`, the scene's `id`, and `outline` only, so tags and notes
   are untouched.

For a scene with no outline, write one only if the writer asks for that too.