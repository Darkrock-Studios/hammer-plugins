# MCP server

Lets AI agents such as Claude work with your Hammer projects over the
[Model Context Protocol](https://modelcontextprotocol.io). An agent starts `hammer mcp`, and the plugin
offers Hammer's operations to it as tools. Desktop only, since it runs as a `hammer` command.

## What it can do

It helps around your writing, and never writes it: no tool changes a scene's text. Hammer's stance is AI
as a tool for the writer, not a replacement.

Every operation the plugin is granted is a tool, named with underscores (`scene_read` for `scene.read`).
It can always read:

- **Projects:** list and show them, and export them in any export format.
- **Scenes:** the scene tree, scene text, metadata, and drafts.
- **Notes, encyclopedia entries, ideas, and timeline events.**
- **Everything else:** search, tags, writing stats, and which plugins are installed.

Two settings, both off by default, let it do more:

- **Let AI agents make changes:** create, rename, move, and archive scenes; edit scene outlines, tags,
  and References; snapshot drafts; add and edit notes, encyclopedia entries and their images, timeline
  events, and ideas; create and rename projects; and make backups.
- **Let AI agents delete things:** scenes, drafts, notes, encyclopedia entries, timeline events, and
  ideas, marked as destructive so your client can ask first. Whole projects are never deletable.

Turning a setting off applies right away. Turning one on adds tools, which an agent sees once it
reconnects.

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

Claude Code:

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

`skills/` holds [Agent Skills](https://agentskills.io) that teach an agent to analyze a manuscript
through this server. They report what they find with quotes from your scenes, and never suggest
rewrites.

- **pov-check:** point-of-view slips (head-hopping) in a scene, a chapter, or the whole book. Tag
  scenes `pov:Name` for the most reliable results.
- **who-knows-what:** tracks what each character learns and when, and flags characters acting on
  things they haven't learned yet, or forgetting what they have.
- **true-up-references:** checks which encyclopedia entries each scene references, proposing to
  dismiss ones only mentioned, confirm ones really in the scene, and clear ones cut from it. It applies
  only the changes you approve, and needs "Let AI agents make changes" on to apply them.

For Claude Code, link each folder into `~/.claude/skills/`, from this directory:

```sh
ln -s "$PWD"/skills/* ~/.claude/skills/
```

Other clients that support Agent Skills take the same folders; see their documentation for where.

## How it works

Written in Kotlin. The manifest asks for `content:read`, `plugins:read`, and by name the writes that
organize a project and the deletes of everything inside one, and declares the `mcp` command. It asks
for no operation that writes a scene's prose (`scene.write`, `scene.append`, `draft.apply`,
`project.import`); Hammer treats those as Prose, which no scope grant covers.

An MCP client starts `hammer mcp` and speaks JSON-RPC over standard input and output, one message a
line; Hammer hands each line to the plugin and writes back its reply. The plugin answers `initialize`,
`ping`, `tools/list`, and `tools/call`, and ignores notifications. Its tools are what `ops.list`
returns it, less `ops.list` itself and whatever its settings leave out; settings are read for every
message. A failed call, including one refused because Hammer is busy, is a tool error the agent can
read. Calls go through the running app when it is open, and straight to the project files when it is
not, with no network port and no token.
