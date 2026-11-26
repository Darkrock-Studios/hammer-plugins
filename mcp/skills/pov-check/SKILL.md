---
name: pov-check
description: Find point-of-view slips (head-hopping) in a Hammer project's scenes through the Hammer MCP server. Use when the writer asks to check POV, head-hopping, or whose head a scene is in, for one scene, a chapter, or the whole book. Reports quoted evidence only; never rewrites prose.
---

# POV check

Find places where a scene leaves its point-of-view character's perspective. You are a reader pointing
at passages, not an editor: report what you find with quotes, and let the writer decide.

## Never write prose

Do not suggest rewrites, alternative sentences, or "you could say instead". Not even when asked in
passing; if the writer explicitly asks for a rewrite, say this skill doesn't do that. Hammer's AI is a
tool for the writer, not a replacement.

## Gather

1. If the project isn't named, call `project_list` and ask which one.
2. Call `scene_tree` for the scenes and groups in story order, with word counts. Groups are chapters
   or parts. Resolve what the writer asked for to a group id or a list of scene ids. Each scene is
   checked on its own, so if they named no scene or chapter, ask which; don't assume the whole book.
3. Say how much that is ("14 scenes, about 38,000 words") before reading. Up to about 30,000 words,
   read it in one go. Over that, wait for a go-ahead, then work one top-level group at a time (scenes
   outside any group count as one more), giving each group's findings as you finish it and carrying
   straight on to the next.
4. Call `entry_list` with `type: "person"` for character names and aliases, so you can tell who a
   pronoun or nickname refers to.
5. Read with `scene_read_many`, passing `group` or `ids`. Each scene
   comes with its `path` of group names and its `meta` (`tags`, `outline`, `notes`). When the result
   has a non-null `next`, call again with `after: next` for the following page. For a single scene,
   `scene_read` is enough.

Check each page before reading the next, and keep only your findings and each scene's POV, not the
text of earlier pages.

## Decide each scene's POV

Use the first of these that answers it, and record which one did:

1. **Tag:** a scene tag like `pov-name` or `pov_name`, in any case. Hammer tags hold only letters,
   digits, `-`, and `_`: it drops a tag with a colon and splits one with a space, so never suggest
   either. A name's spaces are written `-` (`pov-mary-ann`). Several `pov-` tags mean the scene
   switches at its breaks: those are its POV characters, and you match each section to one of them by
   reading it. The name in a tag may be an entry's name or one of its aliases; match it ignoring case,
   with the name's spaces as `-` and its other marks dropped: `pov-obrien` is O'Brien.
2. **Metadata:** the outline or notes naming the POV character.
3. **Inferred:** from the text: the first-person narrator, or the one character whose thoughts and
   sensations the narration reports directly.

Also decide the mode: first person, close third, or omniscient. If a scene reads as deliberate
omniscient, head-hopping is a choice, not a slip: say so and skip the scene rather than flag every
sentence. If you can't tell whose scene it is, ask the writer instead of guessing.

A scene break (`---`, `***`, `* * *`, `#`, or similar on its own line) may switch POV. Treat each side
as its own section with its own POV.

## What counts as a slip

Flag a passage when the narration gives something the POV character couldn't know or perceive:

- **Another character's inner life** stated as fact: thoughts, feelings, intentions, memories, or
  sensations. "Tom wondered if she knew." "She was secretly relieved."
- **Out of sight or earshot:** what happens behind them, in another room, or after they leave.
- **Their own blind spots** stated from outside: "She didn't notice the knife." "His face went red",
  unless they could feel or see it.
- **Knowledge ahead of the story:** "Little did he know", "It would be years before", unless the POV
  is a narrator telling the story afterwards.
- **A switch mid-section:** the narration settles into a different character's head without a break.

Don't flag:

- Inference from what the POV character observes: "He looked angry." "Her voice shook, as if she'd
  been crying."
- Dialogue or action that reveals another character's feelings.
- The POV character guessing or assuming, even wrongly.

## Check your quotes

Every finding quotes the scene exactly. Before reporting, confirm each quote appears verbatim in the
scene's `markdown`. Drop any finding whose quote you can't find. A quote may leave out markdown marks
such as `*`, and nothing else. `text_find` checks one: the quote as `term`, the scene's id in `ids`,
`wholeWord: false`, and `caseSensitive: true`. It reads curly and straight quotes as the same, and any
run of spaces as one. No match means the quote isn't there.

## Report

Per scene with findings:

```
### <scene name> (id <id>)
POV: <name>, <mode> (from <tag | outline | notes | inferred>)

- "<exact quote>"
  <Kind of slip>: <one line on whose head or what knowledge this is>. <Clear slip | Borderline>
```

Then list the scenes checked with no findings on one line, and end with totals: scenes checked, clear
slips, borderline. Mark a finding Borderline when a reasonable writer could read it as the POV
character's inference.

If POV was inferred for any scene, say so at the top: tagging scenes `pov-name` makes the next check
more reliable, and the `pov-set` skill does that.

## Saving the report

Only if the writer asks: `note_create` saves it as a Hammer note. That tool exists only when the MCP
plugin's "Let AI agents make changes" setting is on; if it's missing, say so rather than asking them to
change it.
