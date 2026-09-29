---
name: who-knows-what
description: Track what each character knows, scene by scene, in a Hammer project through the Hammer MCP server, and flag characters acting on information they haven't learned yet, or forgetting what they have. Use when the writer asks about plot holes, continuity of secrets or reveals, or "does X know about Y yet". Reports quoted evidence only; never rewrites prose.
---

# Who knows what

Build a ledger of when each character learns each plot-significant fact, then check every place a
character uses a fact against it. You are a careful reader pointing at possible holes, not an editor:
report what you find with quotes, and let the writer decide.

## Never write prose

Do not suggest rewrites, added lines, or fixes in the story's voice. You may say what kind of thing
would close a hole ("she needs to learn this before scene 12"), never the words. Hammer's AI is a tool
for the writer, not a replacement.

## Scope

- **Whole book** (default): every fact, every character.
- **Focused:** one character ("what does Mara know when"), or one fact ("who knows the letter is
  forged"). Use `search` with key terms to find the scenes that matter, but still read every scene
  from the first hit to the last, since a character can learn something without the keyword
  appearing.

## Gather

1. If the project isn't named, call `project_list` and ask which one.
2. Call `scene_tree` for scenes and groups in story order with word counts, and `entry_list` with
   `type: "person"` for characters and aliases.
3. Call `timeline_list`. If the book isn't told in chronological order (flashbacks, parallel
   timelines, a frame story), ask the writer which order knowledge should follow, and use the timeline
   or their answer to place scenes in story-world time. Otherwise story order is the order.

## Pass 1: build the ledger

Read with `scene_read_many`: no `group` or `ids` for the whole book, or `ids` for a focused range.
It returns scenes in story order a page at a time, each with its `path` of group names and its
`meta`. When `next` is non-null, call again with `after: next`. Finish each page's rows before
reading the next, and keep only ledger rows, not the text of earlier pages. For each scene, record:

- **Facts** introduced: things that matter to the plot and that some characters know and others don't.
  Secrets, identities, plans, betrayals, deaths, locations of people or objects, who did what. Give each
  a short id and one-line statement: `F7: The letter was forged by Ada.` Reuse ids when a fact comes up
  again; don't create near-duplicates. Skip trivia and anything every character would know.
- **Learns:** a character comes to know a fact. Note how: witnessed, told by whom, overheard, read,
  deduced. Include the POV character, and anyone present when it's said aloud.
- **Uses:** a character acts on, mentions, or plans around a fact, or reacts as if they know it.
- **Shows ignorance:** a character asks about, is surprised by, or acts against a fact.

Every row carries an exact quote and the scene id. Row shape:

```
S<scene id> | <character> | learns|uses|ignorant | F<n> | <how, for learns> | "<exact quote>"
```

A character present in a scene only learns what they could hear or see. When unsure whether someone
was in the room, note it in the row rather than deciding.

## Pass 2: check

Using story-world order, flag:

- **Knows too early:** a `uses` row with no earlier `learns` row for that character and fact.
- **Never learns:** the same, with no `learns` row anywhere. Softer: they may have learned it offstage.
- **Forgets:** an `ignorant` row after that character's `learns` row for the fact.
- **Leak:** a fact spreading to a character with no plausible path from anyone who knew it.

Before flagging, check the scene's `meta.outline` and `meta.notes`: the writer may have noted an
offstage reveal. Mention it instead of flagging.

## Check your quotes

Confirm each quote in a finding appears verbatim in that scene's `markdown`. Drop any finding whose
quote you can't find.

## Report

Most serious first:

```
### <character>: <kind> (F<n>: <fact>)
- Uses it in <scene name> (id <id>): "<exact quote>"
- Learns it in <scene name> (id <id>): "<exact quote>"  (or: never on the page)
<One line on why this looks like a hole. Clear | Could be offstage>
```

Then a short **Ledger** section: each fact with who learns it and where, in order, one line per
learning. This lets the writer spot a wrong row and ask you to rerun from there. End with totals:
scenes read, facts tracked, findings by kind.

If the book was long enough that you summarized as you went, say so: findings in later scenes rest on
your ledger, not a reread.

## Saving the report

Only if the writer asks: `note_create` saves it as a Hammer note. That tool exists only when the MCP
plugin's "Let AI agents make changes" setting is on; if it's missing, say so rather than asking them to
change it.
