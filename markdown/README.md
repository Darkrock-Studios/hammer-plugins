# Markdown

Exports a story as one Markdown document, laid out as the document in Hammer's own Markdown export. It
adds a Markdown (MD) format to Hammer's export.

## Settings

- **Number chapters:** off by default. When on, each chapter heading starts with its number, as in
  `## 2. The Pool of Tears`. Only used when top-level scenes are exported as chapters.

## What it writes

The project's name is the title, `# Wonderland`. With top-level scenes exported as chapters, each
chapter is a `##` heading of its name; without, the top-level scenes follow one another, two blank
lines apart. A chapter's scenes are joined by a blank line, each as Hammer stores it, so the Markdown is the
author's own, untouched. Names and scenes are written as they are, not trimmed.

Images are left as Hammer writes them, `![image](hammer://image/5)`, which nothing outside Hammer can
show. The Markdown Hammer sends a plugin names no images, and this plugin does not ask for their files,
which it could not use. For a story with its images, use the [Markdown + images](../markdown-zip/README.md)
plugin, which exports a ZIP holding them.

The file's type is `text/plain`, not `text/markdown`, which is missing from Android's list of types; the
`.md` extension is what marks it as Markdown.

The format is `markdown.md`, so `hammer project export --format markdown.md` and export through the
[MCP server](../mcp/README.md) work with no code in the plugin.

## Development

Written in Kotlin. The manifest declares one exporter with `input = "markdown"`, so Hammer sends each
scene's Markdown as it is stored.
