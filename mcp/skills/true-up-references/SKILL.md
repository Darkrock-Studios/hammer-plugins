---
name: true-up-references
description: Check which encyclopedia entries a Hammer scene references through the Hammer MCP server, and fix them with the writer's approval - drop entries only mentioned, add ones really in the scene, clear ones cut from it. Use when the writer asks to check, fix, or true up a scene's references or connections, for one scene, a chapter, or the whole book. Never writes prose.
---

# True up references

Each Hammer scene lists the encyclopedia entries in it: its references. Hammer adds them by matching
names when a scene is saved, and never removes one, so the list drifts. A character only thought of or
talked about gets added ("she thought of her mother"), and one cut from the scene stays. You read each
scene, decide which entries are really in it, and propose changes for the writer to approve.

## Never write prose

Do not suggest rewrites, alternative sentences, or "you could say instead". Not even when asked in
passing; if the writer explicitly asks for a rewrite, say this skill doesn't do that. Hammer's AI is a
tool for the writer, not a replacement. You change only which entries a scene references.

## Gather

1. If the project isn't named, call `project_list` and ask which one.
2. Call `entry_list` for every entry's id, name, type, and aliases.
3. Call `scene_tree` for the scenes and groups in story order. Resolve what the writer asked for to a
   group id, a list of scene ids, or everything.
4. Read with `scene_read_many`, passing `group` or `ids` (or neither, for the whole book). Each scene's
   `meta` has `references`, the ids of entries confirmed as in it, and `dismissedReferences`, the ids
   the writer dismissed. When the result has a non-null `next`, call again with `after: next` for the
   following page. For a single scene, `scene_read` is enough: its `meta` has the same lists.

Work a page at a time: decide its scenes before reading the next, and keep only your proposals.

## In the scene, or only mentioned

An entry is **in the scene** when it is part of what happens:

- **A character** appears: speaks, acts, is seen or heard, or is the point-of-view character. A
  character present but unnamed counts when the scene makes plain who it is ("the old keeper" in a
  scene where only Alice keeps the light).
- **A place** is where the scene happens, or where characters go or are during it.
- **Anything else** is there to be seen or used in the scene.

An entry is **only mentioned** when the scene talks or thinks about it without it being there:
remembered, discussed, planned, wished for, or named in passing. "She thought of her mother." "They
spoke of the war in the north." A letter from someone is the letter in the scene, not its writer.

If you can't tell, don't propose a change for that entry; list it as unsure with your reason.

## Decide, scene by scene

Look at every entry the scene confirms, dismisses, or names by name or alias:

- **Confirmed, or named but not yet confirmed, and only mentioned:** propose **dismiss**. Hammer adds
  every name it finds the next time the scene is saved; dismissing stops that.
- **Confirmed, no longer in the text at all:** no name, alias, or plain reference left. Propose
  **clear**, which forgets it, so a later mention can be matched again.
- **Dismissed, now in the scene:** propose **confirm**. This also takes it off the dismissed list.
- **Neither, but in the scene:** usually present under an alias the entry lacks, or unnamed but plain.
  Propose **confirm**.

Leave everything else alone.

## Check your quotes

Every confirm and dismiss gives a short quote from the scene as evidence. Before proposing, confirm each
quote appears verbatim in the scene's `markdown`. Drop any proposal whose quote you can't find. A
clear needs no quote: say the entry no longer appears.

## Propose

Per scene with changes:

```
### <scene name> (id <id>)
- Dismiss <entry name>: "<exact quote>" (<only mentioned: remembered | discussed | ...>)
- Confirm <entry name>: "<exact quote>"
- Clear <entry name>: no longer appears
- Unsure <entry name>: <why>
```

Then list the scenes checked with nothing to change on one line, and end with totals of each kind.
Ask the writer which to apply: all of them, a scene's, or single changes.

## Apply

Only what the writer approves. Call `scene_references_set` once per scene, with the scene's `id` and
the entry ids in `confirm`, `dismiss`, and `clear`. Each entry goes in one list only. Report what
changed.

That tool exists only when the MCP plugin's "Let AI agents make changes" setting is on. If it's
missing, say so rather than asking the writer to change it, and give the approved changes so they can
make them under References in the scene's Metadata panel.
