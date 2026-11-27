# MCP server

Lets AI agents such as Claude work with your Hammer projects over the
[Model Context Protocol](https://modelcontextprotocol.io). An agent starts `hammer mcp`, and the plugin
offers Hammer's operations to it as tools. Desktop only, since it runs as a `hammer` command.

## What it can do

It helps around your writing, and never writes it: no tool changes a scene's text, beyond taking out
an image you let it delete. Hammer's stance is AI as a tool for the writer, not a replacement.

The server's instructions tell agents not to ideate for you, down to naming a character or offering a
list of names, and to read the hammer skill before using the tools. Some clients, such as Jan, never show the model those instructions, so each tool that
makes changes repeats in its description to record only what you've decided, never inventing or
suggesting it. In testing, that line is what held. These steer a model; only the missing prose tools are
a hard limit.

Every operation the plugin is granted is a tool, named with underscores (`scene_read` for `scene.read`).
It can always read:

- **Projects:** list and show them, and export them in any export format.
- **Scenes:** the scene tree, scene text, metadata, and drafts.
- **Notes, encyclopedia entries and their images, ideas, and timeline events.**
- **Everything else:** search, exact counts of words and phrases, tags, writing stats, and which plugins
  are installed.

`search` finds a scene once however often a word is in it, ignores case, and matches inside longer words
and in titles, so there are two tools for exact answers on a long book:

- `text_count` counts each of up to 50 words or phrases, giving each one's total and the scenes it's in.
- `text_find` lists every place one appears, in story order, with about 80 characters either side, a
  page at a time, and always the full total.

Both read only the scenes' text, match whole words unless `wholeWord` is false, and ignore case unless
`caseSensitive` is true. They read past styling marks, backslash escapes, and curly quotes, so `E.V.`
matches `*E.V.*` and `Teo's` matches `Teo’s`. `group` or `ids` narrows them to part of the book.

Two settings, both off by default, let it do more:

- **Let AI agents make changes:** create, rename, move, and archive scenes; edit scene outlines, tags,
  and References; snapshot drafts; add and edit notes, encyclopedia entries, timeline events, and ideas;
  add, replace, and rename a project's images; attach images to an entry, detach them, choose its
  primary one, and import a new one for it; create and rename projects; and make backups.
- **Let AI agents delete things:** scenes, drafts, notes, encyclopedia entries, images (deleting one
  first takes it out of the entries, texts, and cover that use it), timeline events, whole timelines
  with their events, and ideas, marked as destructive so your client can ask first. Whole projects are
  never deletable.

A project can hold several timelines. `timeline_settings_read` lists them, and `timeline_list` reads
one: main unless `timeline` names another, or with `orphaned` the events left on timelines deleted
since. With changes allowed, these tools arrange them:

- `timeline_place` places a scene or an encyclopedia EVENT entry on a timeline, once on each.
- `timeline_set` sets or clears single fields of any timeline event: label, lane, milestone, start,
  end, and title for plain events. Naming another `timeline` moves the event there.
- `timeline_settings_write` changes one timeline's mode, calendar, lane order, and name.
- `timeline_def_create` adds a timeline.

Turning a setting off applies right away. Turning one on adds tools, which an agent sees once it
reconnects.

With a small local model, leave both settings off unless you need them. The client sends the whole tool
list with every message: about 3,500 tokens with only the read tools, and nearly 10,000 with all of them.

It can't install or remove plugins: Hammer allows no plugin to, since an installed plugin gets its
permissions without Hammer asking you. An agent that needs to can run `hammer plugin install` or
`hammer plugin uninstall` itself.

It works whether Hammer is open or not: with the app running, calls go through it, so an agent's changes
show up in the open project.

## Installing

1. Build it: `./build.sh mcp`, which writes `mcp/build/mcp.hammerplugin`.
2. In Hammer: Settings, Plugins, Install plugin, pick `mcp.hammerplugin`, and approve its permissions.
3. In Settings, Command line, add the `hammer` command to your PATH. The section also shows the exact
   command for MCP clients.

## Connecting an agent

Claude Code: install the `hammer` plugin, which connects the server and adds the [skills](#skills).
In Claude Code, run:

```
/plugin marketplace add Darkrock-Studios/hammer-plugins
/plugin install hammer@hammer-plugins
```

If you added the server by hand before, remove it with `claude mcp remove hammer` so it isn't connected
twice. For just the server without the skills, or for development data, add it by hand instead:

```sh
claude mcp add --scope user hammer -- hammer mcp
```

Claude Desktop: add this to `claude_desktop_config.json` (Settings, Developer, Edit Config), then restart
the app:

```json
"mcpServers": {
  "hammer": {
    "command": "hammer",
    "args": ["mcp"]
  }
}
```

If the app can't find `hammer`, use its full path, such as `/home/you/.local/bin/hammer`. Other clients
take the same command and arguments.

For Hammer's development data, use `hammer --dev mcp` instead.

## Skills

`skills/` holds [Agent Skills](https://agentskills.io) for agents using this server. They report what
they find with quotes from your scenes, and never suggest rewrites.

- **hammer:** the general one. Answers questions about your story and its world from the project
  itself, citing the scenes and entries it draws on, and records only what you decide. It repeats
  Hammer's stance in its description, which clients that list skills put in front of the model, so it
  holds even in a client that drops the server's instructions. Install it alongside the others.

- **pov-check:** point-of-view slips (head-hopping) in a scene, a chapter, or the whole book. Tag
  scenes `pov-name` for the most reliable results.
- **pov-set:** works out whose point of view each scene is told from and tags it `pov-name`, one tag
  per character where a scene switches at a break. It tags only the scenes you approve, and needs
  "Let AI agents make changes" on to tag them.
- **tense-check:** tense slips in a scene or a chapter: narration that drifts from past to present or
  back, leaving dialogue, thoughts, and flashbacks alone.
- **who-knows-what:** tracks what each character learns and when, and flags characters acting on
  things they haven't learned yet, or forgetting what they have.
- **true-up-references:** checks which encyclopedia entries each scene references, proposing to
  dismiss ones only mentioned, confirm ones really in the scene, and clear ones cut from it. It applies
  only the changes you approve, and needs "Let AI agents make changes" on to apply them.
- **true-up-encyclopedia:** checks the encyclopedia against the scenes: people, places, and things on
  the page with no entry, names an entry lacks as an alias, entries nothing mentions, and entries the
  page contradicts. It adds only the entries and aliases you approve, as a name and a type; it never
  writes an entry's text.
- **outline-check:** compares each scene's outline with what the scene does, and reports what is
  missing, different, or not in the outline. If you ask, it brings an outline up to date with the
  page, showing you the change first.
- **style-sheet:** builds a copyeditor's style sheet from the book: how it spells names and invented
  terms, capitalises, hyphenates, writes numbers, and punctuates, and where it departs from its own
  usual form. Saved as a note, it lets later checks read only the scenes you name.
- **build-timeline:** reads the book for every statement of when something happens and builds the
  timeline from it: scenes, encyclopedia events, and dated backstory, in lanes, with durations where
  the text gives them. It shows the plan and the quotes behind it first, reports dates that
  contradict each other, and never guesses a date the page doesn't give. It needs "Let AI agents make
  changes" on to build; without it, it stops at the plan.

The server serves them itself through the MCP
[Skills extension](https://modelcontextprotocol.io/extensions/skills/overview), so a client that
supports it gets them on connecting, with nothing to install: Claude Code from 2.1.331 (as
`/hammer:pov-check` and so on, approved once per version) and VS Code from 1.147, both in beta. They're
built into the plugin from `skills/`, so rebuild it after changing one.

The Claude Code plugin includes them all as well, named `hammer:pov-check` and so on. For other
clients, install the folders. For an older Claude Code without the plugin, link each into
`~/.claude/skills/`, from this directory:

```sh
ln -s "$PWD"/skills/* ~/.claude/skills/
```

For other agents that support Agent Skills, such as Codex, Cursor, Gemini CLI, and GitHub Copilot, the
[skills CLI](https://github.com/vercel-labs/skills) installs them, asking which agents to add them to:

```sh
npx skills add Darkrock-Studios/hammer-plugins
```

It installs only the skills, so connect the server to each agent too, as above. Clients it doesn't
cover take the same folders; see their documentation for where.

## How it works

Written in Kotlin. The manifest asks for `content:read`, `plugins:read`, and by name the writes that
organize a project and the deletes of everything inside one, and declares the `mcp` command. It asks
for no operation that writes a scene's prose (`scene.write`, `scene.append`, `draft.apply`,
`project.import`); Hammer treats those as Prose, which no scope grant covers.

An MCP client starts `hammer mcp` and speaks JSON-RPC over standard input and output, one message a
line; Hammer hands each line to the plugin and writes back its reply. The plugin answers `initialize`,
`ping`, `tools/list`, and `tools/call`, and ignores notifications. For the Skills extension it answers
`skills/list`, `skills/get`, `resources/list`, and `resources/read` of `skill://<name>/SKILL.md`, each
listed with its SHA-256 digest and size, computed when the plugin is built. It speaks protocol revisions
from 2024-11-05 to 2026-07-28: clients of the newer revision skip `initialize`, ask `server/discover`,
and name their version on every request, so each request is answered by the revision it names. Its tools
are what `ops.list` returns it, less `ops.list` itself and whatever its settings leave out; settings are
read for every message. `text_count` and `text_find` are Hammer's `text.count` and `text.find` where it
has them. On a Hammer without, the plugin works them out itself, reading `scene.read.many` a page at a
time and keeping only counts and the matches asked for; it reads past `*`, `~~`, `==`, and styling tags,
but takes other markdown as text. Each call reads every scene in its scope, since `text_find`'s total is
exact. A failed
call, including one refused because Hammer is busy, is a tool error the agent can read. Calls go through
the running app when it is open, and straight to the project files when it is not, with no network port
and no token.
