# Bare Markdown

Exports a story as one bare Markdown document, laid out as the document in Hammer's own Markdown export
but without its images. It adds a Bare Markdown (MD) format to Hammer's export.

## Settings

- **Number chapters:** off by default. When on, each chapter heading starts with its number, as in
  `## 2. The Pool of Tears`. Only used when top-level scenes are exported as chapters.

## What it writes

The project's name is the title, `# Wonderland`. With top-level scenes exported as chapters, each
chapter is a `##` heading of its name; without, the top-level scenes follow one another, two blank
lines apart. A chapter's scenes are joined by a blank line, each as Hammer stores it, so the Markdown is the
author's own, untouched. Names and scenes are written as they are, not trimmed.

Hammer's images are left out. Hammer writes one as `![image](hammer://image/5)`, which nothing outside
Hammer can show, so each such reference is removed: an image on a line of its own goes with that line,
keeping the paragraphs around it one blank line apart, and one in a sentence goes with the space beside
it, so no space is left doubled or at either end of the line. A scene that was only images goes, and the
scenes around it stay one blank line apart. Only images written as `![alt](hammer://image/<id>)`, the form
Hammer itself writes, go; an image with a title, a reference-style image, or one inside a code block stays
as written, as do other images, such as one from an https link. For a story with its images, use Hammer's
own Markdown export (from Hammer 4.0), which writes a ZIP of the story's Markdown with its images.

The file's type is `text/plain`, not `text/markdown`, which is missing from Android's list of types; the
`.md` extension is what marks it as Markdown.

The format is `bare-markdown.md`, so `hammer project export --format bare-markdown.md` and export through
the [MCP server](../mcp/README.md) work with no code in the plugin.

## Development

Written in Kotlin. The manifest declares one exporter with `input = "markdown"`, so Hammer sends each
scene's Markdown as it is stored. That Markdown carries no image names (each image's alt text is always
`image`), and the plugin does not ask for the images' files, which it could not use.
