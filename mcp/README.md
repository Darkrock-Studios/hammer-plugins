# MCP server

Lets AI agents such as Claude work with your Hammer projects over the
[Model Context Protocol](https://modelcontextprotocol.io). An agent starts `hammer mcp`, and the plugin
offers Hammer's operations to it as tools. Desktop only, since it runs as a `hammer` command.

## What it can do

Every operation the plugin is granted (`content:read`, `content:write`, and deletes) is a tool, named
with underscores (`scene_read` for `scene.read`):

- **Projects:** list, show, create, rename, import, and export them in any export format.
- **Scenes:** read the scene tree, and read, write, create, move, rename, archive, and restore scenes and
  their metadata.
- **Drafts:** list, read, and create them.
- **Notes, encyclopedia entries, ideas, and timeline events:** list, read, add, and change them. Entries
  can have images, ideas can be archived, and events can be reordered.
- **Everything else:** search, tags, writing stats, and backups.

Two settings, both off by default, keep an agent from losing your work:

- **Let AI agents change scenes directly.** While off, scene edits are saved as drafts for you to review.
- **Let AI agents delete things.** While off, agents can't delete anything. When on, they can delete
  scenes, drafts, notes, encyclopedia entries, timeline events, and ideas, marked as destructive so your
  client can ask first. Whole projects are never deletable.

Turning a setting off applies right away. Turning one on adds tools, which an agent sees once it reconnects.

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
