---
name: build-timeline
description: Build a Hammer project's timeline from what the manuscript says about when things happen, through the Hammer MCP server - scenes, encyclopedia events, and dated backstory, in lanes, with durations, each placement backed by a quote and applied with the writer's approval. Use when the writer asks to build, fill in, or generate a timeline, or to check that the story's dates add up. Never writes prose, and never guesses a date the page doesn't give.
---
 # Build timeline

Read the book for every statement of when something happens, work out what those statements fix, and
lay the result out on Hammer's timeline: scenes, events from the encyclopedia, and things that happen
offstage or before the story, arranged in lanes. You propose a plan with the evidence; the writer
approves; then you build it.

The timeline records what the page says. A date the text doesn't give is a story decision, and it
belongs to the writer.

## Never write or invent

- Never write or rewrite prose, and never suggest changes to the story.
- Never place anything by guesswork: not by splitting the difference between two scenes, not by how
  long things usually take. Unplaced is a fine result.
- An event's title and text are plain labels for what the page already says, in the page's own words
  where they fit. No interpretation, no significance, nothing the quote doesn't support.

## Before reading

1. If the project isn't named, call `project_list` and ask which one.
2. Call `timeline_settings_read`, then `timeline_list` for the main timeline. This skill builds from
   empty. If the timeline already has events, say how many and ask: build on a new timeline beside it,
   and under what name, or stop. Never add to or rearrange events the writer placed.
3. Call `scene_tree` for the scenes and groups in story order, with word counts. This skill reads the
   whole book, since a date in one chapter often rests on a line in another. Say how much that is ("62
   scenes, about 91,000 words") and, over about 30,000 words, wait for a go-ahead.
4. Call `entry_list` for every entry: event entries go on the timeline, and the rest are what lanes
   are named after and what events refer to. Read each event entry with `entry_read`: its text may
   date it.

## Pass 1: collect time evidence

Read with `scene_read_many`, passing neither `group` nor `ids`, a page at a time; when `next` is
non-null, call again with `after: next`. Finish each page's rows before reading the next, and keep
only rows, not the text of earlier pages. Check each scene's `meta.outline`, `meta.notes`, and tags
too: the writer may have noted the date.

Record a row for every statement that fixes when something happens or how long it lasts:

- **Scene:** when the scene itself takes place, and how long it runs. "The clock struck seven as they
  sat down." "By the time the port came round it was half past eight."
- **Gap:** time between this scene and another. "Three days later." "The next morning."
- **Happening:** something that happens outside the scenes, at a time or over a span the text gives.
  Backstory ("the war ended twenty years ago"), things offstage ("while they dined, Pike was in the
  cellar"), and states with a start or an end ("four years in France", "ill since the spring"). Note
  who it concerns.
- **Measure:** a fact other dates can be worked out from, that is not itself an event: ages,
  birthdays, seasons, days of the week, how long a journey takes, how long someone has done a thing
  ("a merchant of thirty years").

Row shape, with `E<entry id>` in place of the scene for evidence from an entry's text:

```
S<scene id> | scene|gap|happening|measure | <what, in a few plain words> | <when, as the text puts it> | <who> | "<exact quote>"
```

Mark a row instead of using it when:

- **Claimed:** a character says it and the story shows it to be false or doubts it: a lie, a forged
  date. Never place by it.
- **Hedged:** the text itself is unsure. "A fortnight, perhaps." Never place by it.
- **Loose:** a length with nothing plain to count from. "He had not looked at it for a week." Use it
  only if the same passage makes plain what it counts from.

## Pass 2: resolve

1. **Anchors:** statements that fix a time outright: a date, a clock time, a named day.
2. **Chains:** follow gaps and measures out from the anchors. "Three days later" after an anchored
   scene fixes the next one. Keep each step, so the chain can be shown.
3. Mark every scene and happening:
   - **Stated:** the text gives its time.
   - **Derived:** worked out from an anchor, with the chain.
   - **Ordered:** the text fixes what it comes before or after, and no more.
   - **Unplaced:** nothing fixes it.
4. **Contradictions:** two chains that give one thing different times, an age that doesn't fit the
   years, a Tuesday that can't be one. Keep both sides with their quotes. A contradicted item is not
   placed until the writer rules.
5. **Precision:** no finer than the text. "In 1893" is a year, not a day; "that spring" is not a date
   at all.
6. **Durations:** give something an end only when the text gives a length or an end, or when a later
   stated time in the same scene bounds it. Otherwise it is a single moment. Don't pad a scene to look
   like a span. Where the text gives them, spans are worth getting right: two things running at once
   in different lanes show the writer who was where while something else happened.
7. **Missing anchors:** note any single fact that would place several things at once if the writer
   supplied it ("how many days between the funeral and the hearing").

## Design the timeline

Work out the following from the evidence and put it in the plan for the writer to approve. These are
arrangements, not story content, but they are still the writer's to change.

### Mode

A timeline is **dated** or **manual**. On a dated one every event needs a start on its calendar, and
events sort by it. On a manual one events have no dates: they sit in the order given, each with an
optional `label` of free text for the when ("Long ago", "that winter").

- **Dated,** when more than half the scenes have a stated or derived time that one calendar can hold.
  A time of day alone ("at dawn") doesn't count: it needs a day to go with it.
- **Manual,** when they don't. A manual timeline takes everything stated, derived, or ordered, in
  order, with the when as its label, in the text's own words, a span included ("noon to evening").
  Scenes with nothing against it go in story order, with "story order" as their basis; a scene the
  text places earlier (a flashback) goes where the text puts it. A happening goes in only where the
  text fixes what it comes before or after.

Don't mix: on a dated timeline, ordered and unplaced things stay off and are listed.

### Calendar, for a dated timeline

- **Real dates** (the text gives years or calendar dates): `{"preset": "gregorian"}`. A start is
  `{"year": 1923, "month": 11, "day": 14, "hour": 19, "minute": 0}`, cut off after the last part the
  text gives: `{"year": 1893}`, `{"year": 1902, "month": 3}`.
- **Counted from one event** ("day three", clock times across one night): the story calendar,
  `{"preset": "story", "epochName": "<name>", "coarsestUnit": "day"}` or `"hour"`. Name the epoch
  after the scene or event everything is counted from, using its existing name.
  - Counting in days, a start is `{"day": 3}`. Day 0 is the epoch, and days before it are negative:
    `{"day": -12}`.
  - Counting in hours, a start is `{"hour": 19, "minute": 0}`, with both parts, counted on from the
    epoch past midnight: `{"hour": 26, "minute": 30}` is half past two the next morning.
  - Use hours when the story's times are clock times within a day or two, days otherwise. A day count
    holds no time of day.

An `end` has the same shape as a start.

If the story runs on an invented calendar, don't force its dates onto the gregorian one: count from an
epoch if the text supports that, otherwise go manual with the dates as labels.

### More than one timeline

Propose a second timeline when the evidence falls into two parts that don't fit one: decades of
backstory beside one night of story, or a dated story with backstory the text only orders. Call it
"Backstory" unless the writer names it. The same scene or entry can be placed once on each.

### Lanes

A lane is a row; things in different lanes can run side by side. Propose lanes only where the evidence
fills them:

- one per character, when the text tracks what several people were doing over the same stretch;
- one per storyline, when the book follows separate threads in parallel, named for the groups or POV
  characters that already carry them (`pov-` tags, group names);
- one per subject on a backstory timeline, when it gathers several people's or places' histories.

Name lanes after entries, tags, or groups that already exist. A lane needs at least two events, and
there need to be at least two lanes. If the evidence supports no split, use no lanes and say so.

### What goes on it

- **Scenes** with a place on the timeline: `kind: "scene"`. No lane: a scene sits above the lanes, and
  the lanes show what each person or thread was doing around it.
- **Encyclopedia event entries:** `kind: "entry"`. Always prefer an existing entry to a new plain
  event for the same thing. In a lane only if it belongs to one.
- **Happenings** with no entry: a plain event, in the lane of the character or thread it concerns, or
  no lane if it concerns everyone or more than one lane. Its `references` are the ids of the entries
  its quote names or plainly means, the speaker included when they speak of themselves. Never create
  an encyclopedia entry for it.
- **Milestones:** the epoch, and any event other time statements are counted from ("since the fire",
  "the night of the murder"). Don't pick milestones by how important they feel.

## Check your quotes

Before showing the plan, confirm every quote appears verbatim where it came from: the scene's
`markdown`, its `meta.outline` or `meta.notes`, or the entry's text. Drop any placement whose quote
you can't find. A quote may leave out markdown marks such as `*`, and nothing else. `text_find` checks
one: the quote as `term`, the scene's id in `ids`, `wholeWord: false`, and `caseSensitive: true`. It
reads curly and straight quotes as the same, and any run of spaces as one. No match means the quote
isn't there. It doesn't search outlines or notes: check a quote from one against the `meta` you read.

## The plan

Show it before writing anything, one block per timeline:

```
## Timeline: <name> (<dated, gregorian | dated, counted in <days|hours> from "<epoch>" | manual>)
Lanes: <lane>, <lane>, ... | none

| When | Until | Lane | What | Kind | Milestone | Basis |
|---|---|---|---|---|---|---|
| <start> | <end or blank> | <lane or blank> | <scene name | entry name | event title> | scene|entry|event | <yes or blank> | Stated: "<exact quote>" (<scene name>) |
| ... | | | | | | Derived: <anchor> + "<exact quote>" (<scene name>) |
| ... | | | | | | Ordered: after <what>, "<exact quote>" (<scene name>) |
| ... | | | | | | Story order |
```

Evidence from an entry is cited as (entry: <entry name>), and from a scene's outline or notes as
(<scene name>, outline) or (<scene name>, notes). Write When as a reader would ("14 Nov 1923, 7:00
pm", "Day 3", "9:30 pm"); on a manual timeline it is the label, and the rows are in timeline order.

For each plain event, also give the title and text you would record and the entries it would refer to.
Then, across all timelines:

- **Contradictions:** each with both quotes and their scenes, and what each side implies.
- **Unplaced:** scenes, event entries, and happenings left off, one line each, with the nearest thing
  the text does say.
- **Set aside:** claimed, hedged, and loose statements you didn't place by, with their quotes.
- **Missing anchors:** what the writer could supply, and what each would place.
- Totals: scenes placed and unplaced, entries placed, plain events, contradictions.

Ask the writer to approve it, change it, or rule on the contradictions. An anchor the writer supplies
("the first scene is 3 March 1923") is theirs to give: recompute the chains from it and show the plan
again.

Order of events that isn't about time (a character surprised by what they already knew) is outside
this skill. If you notice it, name the scenes in one closing line.

## Build

Only the approved plan, in this order:

1. **The timeline.** For the main one, `timeline_settings_write` with `mode` (`"dated"` or
   `"manual"`), `calendar` for a dated one, and `laneOrder` as the lane names in order; leave out what
   the plan doesn't have, and skip the call if the settings already match. For a second one,
   `timeline_def_create` with `name` and the same fields; it returns an id to pass as `timeline` in
   the calls below. If the main timeline already had events, it gets no calls at all, settings
   included: every timeline in the plan is made with `timeline_def_create`, and every event call
   carries its `timeline`.
2. **Events,** in timeline order, one call each:
   - `timeline_place` for a scene or event entry: `kind`, `subject` (the scene's or entry's id), and
     `start`, `end`, `lane`, `milestone` where the plan has them.
   - `timeline_create` for a plain event: `title`, `content` (its text), `references`, and `start`,
     `end`, `lane`, `milestone` where the plan has them.
3. **On a manual timeline** there is no `start` or `end`. Events land in the order you make the calls,
   so keep to the plan's order. Give a plain event its when as `label` in `timeline_create`. A placed
   scene or entry takes its label straight afterwards: `timeline_set` with the event id that
   `timeline_place` returned, and `label`.
4. `timeline_list` to confirm what landed, and report it: counts by kind, lanes, and anything a tool
   refused. If a tool refuses a date, give its message; don't try other dates.

These tools exist only when the MCP plugin's "Let AI agents make changes" setting is on. If they're
missing, the plan is the result: give it, say the setting is off, and don't ask the writer to change
it. The contradictions and unplaced lists are worth having on their own.

## Saving the evidence

Only if the writer asks: `note_create` saves the plan, contradictions, and unplaced list as a Hammer
note.