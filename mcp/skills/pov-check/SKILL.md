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
   or parts. Resolve what the writer asked for to a group id, a list of scene ids, or everything.
3. Call `entry_list` with `type: "person"` for character names and aliases, so you can tell who a
   pronoun or nickname refers to.
4. Read with `scene_read_many`, passing `group` or `ids` (or neither, for the whole book). Each scene
   comes with its `path` of group names and its `meta` (`tags`, `outline`, `notes`). When the result
   has a non-null `next`, call again with `after: next` for the following page. For a single scene,
   `scene_read` is enough.

Check each page before reading the next, and keep only your findings and each scene's POV, not the
text of earlier pages. The word counts from `scene_tree` tell you up front how many pages to expect.

## Decide each scene's POV

Use the first of these that answers it, and record which one did:

1. **Tag:** a scene tag like `pov:Name`, `pov-Name`, or `POV Name`.
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
`markdown` from `scene_read`. Drop any finding whose quote you can't find.

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

If POV was inferred for any scene, say so at the top: tagging scenes `pov:Name` makes the next check
more reliable.

## Saving the report

Only if the writer asks: `note_create` saves it as a Hammer note. That tool exists only when the MCP
plugin's "Let AI agents make changes" setting is on; if it's missing, say so rather than asking them to
change it.
