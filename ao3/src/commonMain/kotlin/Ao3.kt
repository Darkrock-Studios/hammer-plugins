// AO3 export: each chapter as the HTML Archive of Our Own's work text box takes, under a marker line
// naming it, since AO3 posts a work a chapter at a time. Reads the host's parse of each scene
// (`input = "prose"`), and writes only tags AO3's sanitizer keeps.
//
// AO3 also turns the newlines in what is pasted into paragraphs and breaks, except next to block tags,
// so every block is a tag of its own and text never carries a raw newline.
import hammer.ExportRequest
import hammer.settings
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

@Serializable
class Ao3Settings(val sceneBreak: SceneBreak = SceneBreak.Rule)

enum class SceneBreak(val html: String) {
	Rule("<hr />"),
	Asterisks("<p align=\"center\">* * *</p>"),
	Blank("<p>&nbsp;</p>"),
}

/** One of the blocks Hammer parses a scene into, keyed by `type`. */
@Serializable
sealed interface Block {
	@Serializable
	@SerialName("paragraph")
	class Paragraph(val spans: List<Span>) : Block

	@Serializable
	@SerialName("blank")
	data object Blank : Block

	@Serializable
	@SerialName("heading")
	class Heading(val level: Int, val spans: List<Span>) : Block

	/** Nested items come flattened in reading order, each with its own depth. */
	@Serializable
	@SerialName("list")
	class Listing(val ordered: Boolean, val items: List<ListItem>) : Block

	@Serializable
	class ListItem(val level: Int, val ordered: Boolean, val spans: List<Span>)

	@Serializable
	@SerialName("quote")
	class Quote(val paragraphs: List<List<Span>>) : Block

	@Serializable
	@SerialName("code")
	class Code(val code: String) : Block

	/** The author's own scene break. */
	@Serializable
	@SerialName("rule")
	data object Rule : Block

	@Serializable
	@SerialName("table")
	class Table(val header: List<List<Span>>, val rows: List<List<List<Span>>>) : Block

	/** A line holding only a project image, named by [name]; its file is not used. */
	@Serializable
	@SerialName("image")
	class Image(val name: String) : Block
}

@Serializable
class Span(
	val text: String,
	val bold: Boolean = false,
	val italic: Boolean = false,
	val strikethrough: Boolean = false,
	val code: Boolean = false,
	val link: String? = null,
)

private val json = Json { ignoreUnknownKeys = true }

fun ao3Export(request: ExportRequest): String {
	if (request.chapters.all { it.prose.isEmpty() } && request.chapters.any { it.scenes.isNotEmpty() }) {
		error("This plugin needs a newer version of Hammer")
	}
	val sceneBreak = request.settings<Ao3Settings>().sceneBreak.html
	val chapters = request.chapters
		.map { it.name to chapterHtml(it.prose, sceneBreak) }
		.filter { (_, html) -> html.isNotEmpty() }
	val text = if (request.topLevelAsChapters) {
		chapters.mapIndexed { i, (name, html) ->
			"===== Chapter ${i + 1} of ${chapters.size}: ${name.trim()} =====\n\n$html"
		}.joinToString("\n\n\n")
	} else {
		chapters.joinToString("\n$sceneBreak\n") { it.second }
	}
	return if (text.isEmpty()) "" else "$text\n"
}

/** A chapter's scenes, each a list of blocks, with a scene break between passages. */
private fun chapterHtml(scenes: List<List<JsonElement>>, sceneBreak: String): String {
	val passages = mutableListOf<MutableList<String>>()
	for (scene in scenes) {
		var passage = mutableListOf<String>().also { passages += it }
		for (element in scene) {
			when (val block = decode(element)) {
				null, Block.Blank -> {}
				Block.Rule -> passage = mutableListOf<String>().also { passages += it }
				else -> blockHtml(block)?.let { passage += it }
			}
		}
	}
	return passages.filter { it.isNotEmpty() }.joinToString("\n$sceneBreak\n") { it.joinToString("\n") }
}

/** Null for a block of a type this plugin does not know, which a newer Hammer may send. */
private fun decode(element: JsonElement): Block? = try {
	json.decodeFromJsonElement(Block.serializer(), element)
} catch (e: IllegalArgumentException) {
	null
}

/** Null when the block holds nothing to show. */
internal fun blockHtml(block: Block): String? = when (block) {
	is Block.Paragraph -> block.spans.takeUnless { it.isBlank() }?.let { "<p>${spansHtml(it)}</p>" }
	is Block.Heading -> block.spans.takeUnless { it.isBlank() }?.let {
		// AO3 shows the work's title as h2 and a chapter's as h3, so a scene's own headings sit below them.
		val tag = "h${(block.level + 3).coerceIn(4, 6)}"
		"<$tag>${spansHtml(it)}</$tag>"
	}
	is Block.Listing -> block.items.takeUnless { items -> items.all { it.spans.isBlank() } }?.let(::listHtml)
	is Block.Quote -> block.paragraphs.filterNot { it.isBlank() }.takeUnless { it.isEmpty() }?.let { paragraphs ->
		paragraphs.joinToString("\n", "<blockquote>\n", "\n</blockquote>") { "<p>${spansHtml(it)}</p>" }
	}
	is Block.Code -> block.code.trimEnd().takeUnless { it.isBlank() }?.let { "<pre>${escape(it, breaks = false)}</pre>" }
	is Block.Table -> tableHtml(block)
	// AO3 hosts no images, so `src` is left for the author to fill in with the image's URL.
	is Block.Image -> "<img alt=\"${attribute(block.name)}\" src=\"\">"
	Block.Blank, Block.Rule -> null
}

private fun List<Span>.isBlank() = all { it.text.isBlank() }

/** Nests the flattened items, a level deeper at most than the item before. */
private fun listHtml(items: List<Block.ListItem>): String = buildString {
	val open = ArrayDeque<String>()
	for (item in items) {
		val depth = item.level.coerceIn(0, open.size) + 1
		if (depth > open.size) {
			val tag = if (item.ordered) "ol" else "ul"
			if (open.isNotEmpty()) append('\n')
			append("<$tag>\n")
			open.addLast(tag)
		} else {
			while (open.size > depth) append("</li>\n</${open.removeLast()}>")
			append("</li>\n")
		}
		append("<li>").append(spansHtml(item.spans))
	}
	while (open.isNotEmpty()) append("</li>\n</${open.removeLast()}>")
}

private fun tableHtml(table: Block.Table): String? {
	if (table.header.cellsBlank() && table.rows.all { it.cellsBlank() }) return null
	return buildString {
		append("<table>\n")
		if (table.header.isNotEmpty()) {
			append("<thead>\n<tr>")
			table.header.forEach { append("<th>").append(spansHtml(it)).append("</th>") }
			append("</tr>\n</thead>\n")
		}
		if (table.rows.isNotEmpty()) {
			append("<tbody>\n")
			table.rows.forEach { row ->
				append("<tr>")
				row.forEach { append("<td>").append(spansHtml(it)).append("</td>") }
				append("</tr>\n")
			}
			append("</tbody>\n")
		}
		append("</table>")
	}
}

private fun List<List<Span>>.cellsBlank() = all { it.isBlank() }

/**
 * The spans with their styles as tags, nested link, italic, bold, strikethrough, code from the outside
 * in. A tag stays open across spans that keep it, so bold inside an italic run does not split the run.
 */
internal fun spansHtml(spans: List<Span>): String = buildString {
	val open = mutableListOf<Pair<String, String>>()
	for (span in spans) {
		if (span.text.isEmpty()) continue
		val wanted = buildList {
			span.link?.let(::linkTag)?.let { add(it to "a") }
			if (span.italic) add("<em>" to "em")
			if (span.bold) add("<strong>" to "strong")
			if (span.strikethrough) add("<s>" to "s")
			if (span.code) add("<code>" to "code")
		}
		var kept = 0
		while (kept < open.size && kept < wanted.size && open[kept] == wanted[kept]) kept++
		while (open.size > kept) append("</${open.removeLast().second}>")
		for (tag in wanted.subList(kept, wanted.size)) {
			append(tag.first)
			open += tag
		}
		append(escape(span.text, breaks = true))
	}
	while (open.isNotEmpty()) append("</${open.removeLast().second}>")
}

/** AO3 keeps links to http, https, and mailto only; a link to anything else is left as its text. */
private fun linkTag(url: String): String? {
	val scheme = url.substringBefore(':', "").lowercase()
	if (scheme != "http" && scheme != "https" && scheme != "mailto") return null
	return "<a href=\"${attribute(url)}\">"
}

/** A double-quoted attribute's value, with a newline as a space. */
private fun attribute(value: String): String =
	escape(value, breaks = false).replace("\"", "&quot;").replace('\n', ' ')

/**
 * Escapes what AO3 would read as markup. With [breaks], a newline is a `<br />`, never followed by a
 * newline, which AO3 would read as a second break and split the paragraph at.
 */
internal fun escape(text: String, breaks: Boolean): String = buildString(text.length) {
	for (c in text) {
		when (c) {
			'&' -> append("&amp;")
			'<' -> append("&lt;")
			'>' -> append("&gt;")
			'\r' -> {}
			'\n' -> append(if (breaks) "<br />" else "\n")
			else -> append(c)
		}
	}
}
