# AO3

Exports a story for [Archive of Our Own](https://archiveofourown.org), as the HTML its work text box
takes. It adds an AO3 (TXT) format to Hammer's export.

AO3 posts a work a chapter at a time, so each chapter comes under a marker line such as
`===== Chapter 2 of 12: The Pool of Tears =====`. Paste what is under it into the chapter's text, in
AO3's HTML editor, and the name into its chapter title. With top-level scenes not exported as chapters,
the story is one text, with no markers.

## Settings

- **Scene break:** a horizontal line (the default), a centered `* * *`, or a blank line. A horizontal
  rule in a scene counts as a scene break too.

## What it writes

Every paragraph is a `<p>`. Italics are `<em>`, bold `<strong>`, strikethrough `<s>`, and inline code
`<code>`; links to http, https, and mailto are kept, and others left as their text. Headings in a scene
are `<h4>` to `<h6>`, below AO3's own chapter title. Lists, quotations, code blocks, and tables are
`<ul>`/`<ol>`, `<blockquote>`, `<pre>`, and `<table>`. `<`, `>`, and `&` in the text are escaped, so
nothing in the prose is read as markup.

AO3 hosts no images, so an image on a line of its own is an `<img>` with the image's name as its `alt`
and an empty `src`, for you to fill in with the URL of wherever you host the image. An image inside a
line stays as its name, and so does one whose file Hammer could not read.

AO3 turns the newlines in what is pasted into paragraphs and breaks, except next to block tags. Every
block is a tag of its own and the text holds no raw newlines, so AO3 keeps the HTML as it is. AO3 takes
up to 500,000 characters a chapter; the plugin does not check.

The format is `ao3.txt`, so `hammer project export --format ao3.txt` and export through the
[MCP server](../mcp/README.md) work with no code in the plugin.

## Development

Written in Kotlin. The manifest declares one exporter with `input = "prose"`, so Hammer sends each scene
already parsed and the plugin needs no markdown parser. It also sets `images = true`, so a line holding
only an image comes as an image block; the plugin writes its name and leaves the file Hammer sends
unused. Tags and attributes are those AO3's sanitizer allows
([sanitizer_config.rb](https://github.com/otwcode/otwarchive/blob/master/config/initializers/gem-plugin_config/sanitizer_config.rb)),
and newlines are kept away from where AO3's
[paragraph_maker.rb](https://github.com/otwcode/otwarchive/blob/master/lib/paragraph_maker.rb) would
turn them into breaks.

It needs a Hammer with prose input; an older one gets an error asking for a newer version.
