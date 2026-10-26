# Plain text

Exports a story as plain text, for pasting into submission forms, which want text with their own
conventions for scene breaks and italics. It adds a Plain Text (TXT) format to Hammer's export.

## Settings

- **Scene break:** `#` (the default), `* * *`, or a blank line. A horizontal rule in a scene counts as
  a scene break too.
- **Italics:** `_underscores_` (the default), `*asterisks*`, or removed.
- **Paragraphs:** a blank line between them (the default), or each indented with a tab.
- **Chapter headings:** on by default, and only used when top-level scenes are exported as chapters.

Lists come out as `- ` or numbered lines, indented two spaces a level; tables as rows with cells split
by ` | `; quotations indented four spaces; code as it is written.

The format is `plaintext.txt`, so `hammer project export --format plaintext.txt` and export through the
[MCP server](../mcp/README.md) work with no code in the plugin.

## Development

Written in C, an 8 KB package. The manifest declares one exporter with `input = "prose"`, so Hammer
sends each scene already parsed and the plugin needs no markdown parser. It writes straight into one
buffer, and anything that turns out blank (a paragraph, a chapter) is rolled back to a mark taken
before it.

It needs a Hammer with prose input; an older one gets an error asking for a newer version.
