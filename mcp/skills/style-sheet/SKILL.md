---
name: style-sheet
description: Build a copyeditor's style sheet for a Hammer project through the Hammer MCP server - how the manuscript spells its names and invented terms, capitalises, hyphenates, writes numbers, and punctuates - and flag every place that departs from it. Use when the writer asks for a style sheet, a consistency check on spelling, capitalisation, or hyphenation, or to check scenes against the style sheet. Reports quoted evidence only; never rewrites prose.
---
# Style sheet

A style sheet records the choices a manuscript makes where more than one would be correct: grey or
gray, the King or the king, farmhouse or farm-house. You build it from what the book already does, and
point at the places that do something else. The choices are the writer's; you only find them.

## Never write prose

Do not suggest rewrites. You may name the form a passage departs from; you never decide which form is
right. Where the book is split, report the split and let the writer rule. Hammer's AI is a tool for
the writer, not a replacement.

## Which job

- **Build:** read the whole book and draw up the sheet. Needed once, and again after large changes.
- **Check:** read one scene or chapter against a sheet already saved. Cheap, and the usual job once a
  sheet exists.

To decide:

1. If the project isn't named, call `project_list` and ask which one.
2. Call `note_list` with `tag: "style-sheet"`. If there is a saved sheet, `note_read` it.
3. Call `scene_tree` for the scenes and groups in story order, with word counts.
4. Decide the job. With a saved sheet and a scene or chapter named: check. With no sheet: say so, and
   that building one means reading the whole book, about how many words, and wait for a go-ahead. If
   the writer would rather not, build from the scenes they name and say the sheet covers only those.

## Build

Call `entry_list` first. An entry's name and aliases are the writer's own spelling of them, and the
sheet takes the spelling as given. They don't settle capitals or a leading "the": an entry headed "The
Harbor Seal" says nothing against "the harbor seal" in a sentence. For those, go by the scenes. An
entry name the scenes never use isn't on the sheet.

Read with `scene_read_many`, passing neither `group` nor `ids` for the whole book, a page at a time;
when `next` is non-null, call again with `after: next`. Finish each page's rows before reading the
next, and keep only rows, not the text of earlier pages. The sheet covers each scene's `markdown`
only, not the outline or notes in its `meta`.

Note every form the book could have written another way, under these headings:

- **Names and terms:** people, places, and invented words, as spelled. Watch for one name spelled two
  ways, and for accents, apostrophes, and internal capitals ("MacRae", "Mac Rae").
- **Capitals:** titles and ranks, with and without a name ("the Captain", "Captain Reyes", "a
  captain"); institutions, peoples, and eras of the story's world; compass regions ("the North");
  forms of address.
- **Hyphens and compounds:** one word, two, or hyphenated ("farmhouse", "half-light", "grey-eyed");
  prefixes ("re-enter", "cooperate").
- **Spelling:** where regional spellings differ ("colour", "realise", "travelled", "grey"), which the
  book uses; "toward" or "towards"; "OK", "okay".
- **Numbers:** spelled out or in figures, and up to where; ages, times, dates, money, measures.
- **Punctuation:** double or single quotation marks; the comma before "and" in a list; how a dash is
  written and spaced; how an ellipsis is written; how a broken-off line of dialogue ends.
- **Emphasis and marking:** what gets italics: thoughts, foreign or invented words, ship and book
  names, letters and signs.

One row per form:

```
<heading> | <form> | <scene name>: "<exact quote>" (its first use)
```

### Counting

Note forms as you read; don't keep a tally. Then count with `text_count`, which counts every
occurrence in the scenes' text: all the forms of one choice as `terms` in one call, up to 50. Count a
form where you saw a rival or one is likely ("today" beside "to-day"); a compound with no likely rival
needs no count. It matches whole words, so "till" doesn't count "still", and ignores capitals unless
`caseSensitive` is true. Set that when the forms differ only by a capital. A hyphen or space inside a
term is matched as written, so give each spelling.

A mark counts exactly with `wholeWord: false` when it needs no spaces to tell it by: a backtick, "--",
"...", a figure. Spaces at a term's ends are ignored, so a spaced hyphen matches every hyphen, and
neither tool sees italics or tells curly quotes from straight ones: take spacing, quotation marks, and
emphasis from reading, and say in the sheet that they are by eye.

`text_find` lists every occurrence of one form with its scene and the text around it. Use it to see
which uses are sentence starts, to find each departure's scenes, and to take exact quotes from.

`search` can't stand in for either: it gives one hit per scene however often a form appears there,
ignores capitals, matches inside longer words, and matches scene titles. If you don't have
`text_count`, count as you read instead, and say in the sheet that the counts are by eye.

For each choice:

- **Usual:** one form has at least three uses and at least twice as many as any other. The others are
  **departures**.
- **Undecided:** anything else where there is more than one form. Say so, with the counts.

A rule that covers several words is one choice, counted in distinct words, not uses: "colour",
"favour", and "honoured" are three words on the -our side, and "harbor" is one on the -or side,
however many times it appears. A word's other endings and its compounds count with it: "coloured" with
"colour", "harbormaster" with "harbor". Give both sides with their words. A word the book itself writes both ways ("to-day" and
"today") is its own choice, counted in uses, and counts on neither side of the rule.

A form with no rival in the book is not a departure by count, however it sits with a rule. If it
looks like a slip ("looking- glass", with a stray space), list it by eye.

Words that merely resemble each other are not rival forms: "Master" and "Mr.", "until" and "till",
"Midsummer" and "midwinter" are different words. Note each under its heading and leave it at that.

A word that starts a sentence says nothing about its capital. Leave it out of the count. That takes
`text_find` on the capitalised form, so do it only where the book also has the lower-case one.

Not every difference is an inconsistency. "The king" and "King Aldric" follow one rule; a character
who speaks differently from the narration is voice; a letter or sign may be spelled its own way; the
same words may name two things ("the Drowned Lantern", a tavern, and "the drowned lantern", the lamp
over its bar). Look at the quotes before calling something a departure, and where it may be
deliberate, keep it and say so.

## The sheet

```
# Style sheet: <project name>
Built from: <the whole book | scenes named>, <date>

## Names and terms
- <form> (<n>). Also seen: <other form> (<n>)

## Capitals
- <rule as the book applies it>. <scene name>: "<exact quote>"

## Hyphens and compounds
## Spelling
## Numbers
## Punctuation
## Emphasis and marking
```

All seven headings, every time. One line per choice, usual form first, with counts where there is more
than one form. For a rule, give it in a few plain words with one example from the book. Mark undecided
items as undecided. Where the book has no case of something the heading covers, say "none in the
book", so the writer knows it was looked for. The date is today's.

## Check

Read only the scenes named, with `scene_read_many` or `scene_read`, and compare against the saved
sheet. Flag a form the sheet lists another way. A name or term the sheet doesn't have is not a
departure: list it as new.

## Check your quotes

Confirm every quote appears in its scene's `markdown`. Drop any finding whose quote you can't find.
Write a quote as a reader sees the text: without the `*`, `_`, `~~`, or `==` around styled words, the
`#` or `>` that starts a line, or the backslash before an escaped character. Every other character
stays as the scene has it, a backtick or apostrophe used as a quotation mark included, though curly
quotes may be written straight and a run of spaces as one. `text_find` checks one: the quote as
`term`, the scene's id in `ids`, `wholeWord: false`, and `caseSensitive: true`. It never matches
across a paragraph break: check a quote that runs over one a paragraph at a time, and see in the text
you read that those paragraphs follow one another in that order, with nothing left out between them.
After no match, find a few plain words of the passage with `text_find` and take the quote from the
`context` it returns. If none holds the passage, the quote isn't there. Never quote from a `search`
snippet: it is not the scene's exact text.

## Report

After building, give the sheet, then:

```
## Departures
- <form> (<n>), usual form <form> (<n>): <scene name> (id <id>): "<exact quote>"; <scene name> ...
  <Possibly deliberate: why>

## Undecided
- <form> (<n>) and <form> (<n>): <scene name>: "<exact quote>" / <scene name>: "<exact quote>"

## By eye
- <what, in a few plain words>: <scene name> (id <id>): "<exact quote>"
```

One line per departing form, with every scene it appears in. By eye holds what reading found and no
count backs: spacing, quotation marks, emphasis. A quote can't show italics, so say in words what is
marked. A form that is in a letter, a sign, or
one character's speech is possibly deliberate: list it, with that note.

After checking, give only the departures and the new names and terms, grouped by scene, and end with
totals of each.

A ruling from the writer ("it's grey, always") settles an item: record it in the sheet as their
decision, and treat every other form as a departure from then on.

## Saving the sheet

Saving needs `note_create`, which exists only when the MCP plugin's "Let AI agents make changes"
setting is on. If you have it, offer once, after building, to save the sheet: checking a scene later
depends on it. If the writer agrees, `note_create` with the sheet as `content` and `tags:
["style-sheet"]`. To record a ruling or a rebuild in a saved sheet, `note_update` it; keep every
ruling the writer made.

If you don't have it, say the setting is off rather than asking the writer to change it, and that the
sheet above is theirs to keep.