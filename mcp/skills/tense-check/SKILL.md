---
name: tense-check
description: Find tense slips in a Hammer project's scenes through the Hammer MCP server - narration that drifts from past to present or back without reason. Use when the writer asks to check tense, tense consistency, or tense slips, for one scene, a chapter, or the whole book. Reports quoted evidence only; never rewrites prose.
---
# Tense check

Find places where a scene's narration leaves the tense it is told in. You are a reader pointing at
passages, not an editor: report what you find with quotes, and let the writer decide.

## Never write prose

Do not suggest rewrites, corrected verbs, or "you could say instead". Not even when asked in passing;
if the writer explicitly asks for a rewrite, say this skill doesn't do that. Hammer's AI is a tool for
the writer, not a replacement.

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
   book. When the result has a non-null `next`, call again with `after: next`. For a single scene,
   `scene_read` is enough.

Check each page before reading the next, and keep only your findings, not the text of earlier pages.
Check a scene's `markdown` only. The outline and notes in its `meta` are the writer's own, and often
in another tense.

## Decide each scene's tense

The base tense is the one most of the narration is in: past ("she walked") or present ("she walks").
Record it per scene. A scene break (`---`, `***`, `* * *`, `#`, or similar on its own line) may change
tense on purpose: treat each side as its own section with its own base tense.

If a scene moves between tenses in a steady pattern (alternating sections, a present-tense frame
around a past-tense story), that is a choice: say so and skip it rather than flag every sentence. If
you can't tell what the base tense is, say so and skip it.

## What counts as a slip

Flag a verb in the narration that is in the other tense with nothing to account for it:

- **Drift:** a sentence or run of sentences in the other tense. "She walked to the door. She opens it
  and looks out." A run is one finding.
- **Mixed sentence:** one sentence with narration verbs in both tenses. "He turned and sees her."
- **Flashback that doesn't return:** past-tense narration steps back with "had" and never comes
  forward again, so later events of the scene read as part of the flashback.

Don't flag:

- **Dialogue,** or anything inside quotation marks.
- **Direct thought:** thoughts given in the character's own words, in italics or not. "This is a
  mistake, she thought."
- **Earlier events** told with "had" in a past-tense scene, or in simple past in a present-tense one.
- **Things still true:** standing facts, true outside the scene's moment. "The river runs south from
  here." "Dogs know these things." Something happening in the scene is not one: "the tide is coming
  in" is a slip in a past-tense scene.
- **Letters, signs, ledgers, and other quoted text,** however set off: quotation marks, italics, or a
  block of its own.
- **Would, could, might,** and other conditionals; "if she were".
- **Verbs that aren't the sentence's tense:** "walking", "to walk", "having walked".
- **Verbs spelled alike in both tenses** ("read", "put", "set"): take them as the base tense.
- **A first-person narrator's asides** from the time of telling: "I still don't know why I went."

## Check your quotes

Every finding quotes the scene exactly, the whole sentence or run that slips. Before reporting,
confirm each quote appears in the scene's `markdown`. Drop any finding whose quote you can't find.
Write a quote as a reader sees the text: without the `*`, `_`, `~~`, or `==` around styled words, the
`#` or `>` that starts a line, or the backslash before an escaped character. Every other character
stays as the scene has it, a backtick or apostrophe used as a quotation mark included, though curly
quotes may be written straight and a run of spaces as one. `text_find` checks one: the quote as
`term`, the scene's id in `ids`, `wholeWord: false`, and `caseSensitive: true`. It never matches
across a paragraph break: check a quote that runs over one a paragraph at a time, and see in the text
you read that those paragraphs follow one another in that order, with nothing left out between them.
After no match, find a few plain words of the passage with `text_find` and take the quote from the
`context` it returns. If none holds the passage, the quote isn't there.

## Report

Per scene with findings:

```
### <scene name> (id <id>)
Base tense: <past | present>

- "<exact quote>"
  <Kind of slip>: <the verbs that are in the other tense>. <Clear slip | Borderline>
```

Then list the scenes checked with no findings on one line, each with its base tense, and the scenes
skipped with the reason. End with totals: scenes checked (skipped ones not counted), clear slips,
borderline. If there are no findings at all, that line and the totals are the report.

Mark a finding Borderline when it could be read as direct thought or as something still true. A
thought given in the narration's own voice ("What was she doing here?") keeps the base tense; in the
other tense it is Borderline, not Clear.

If you noticed something another skill covers, such as a point-of-view slip, name the scene in one
closing line and leave it there.

## Saving the report

Only if the writer asks: `note_create` saves it as a Hammer note. That tool exists only when the MCP
plugin's "Let AI agents make changes" setting is on; if it's missing, say so rather than asking them
to change it.