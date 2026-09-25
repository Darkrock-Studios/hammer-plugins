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
- **Everything else:** search, tags, and writing stats.

Two settings, both off by default, let it do more:

- **Let AI agents make changes:** create, rename, move, and archive scenes; edit scene outlines and tags;
  snapshot drafts; add and edit notes, encyclopedia entries and their images, timeline events, and ideas;
  create and rename projects; and make backups.
- **Let AI agents delete things:** scenes, drafts, notes, encyclopedia entries, timeline events, and
  ideas, marked as destructive so your client can ask first. Whole projects are never deletable.

Turning a setting off applies right away. Turning one on adds tools, which an agent sees once it
reconnects.

It works whether Hammer is open or not: with the app running, calls go through it, so an agent's changes
show up in the open project.

## Installing

1. Build it: `gradle :mcp:hammerPlugin` in `kotlin/`, which writes `mcp/build/mcp.hammerplugin`.
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
