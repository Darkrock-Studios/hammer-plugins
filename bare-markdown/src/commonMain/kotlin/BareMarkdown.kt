// Bare Markdown export: the story as one Markdown document, under the project's name as its title, with
// each chapter a heading and each scene's Markdown as Hammer stores it (`input = "markdown"`), less
// Hammer's images.
//
// Hammer's images are `hammer://image/<id>` references, which nothing outside Hammer can show, so they
// are left out: Markdown input carries no image names, and the manifest does not ask for the images'
// files, which this plugin could not use.
import hammer.ExportRequest
import hammer.IMAGE_REFERENCE_PREFIX
import hammer.settings
import kotlinx.serialization.Serializable

@Serializable
class BareMarkdownSettings(val numberChapters: Boolean = false)

fun bareMarkdownExport(request: ExportRequest): String {
	val numberChapters = request.settings<BareMarkdownSettings>().numberChapters
	return buildString {
		append("# ").append(request.projectName).append("\n\n")
		var wroteItem = false
		request.chapters.forEachIndexed { i, chapter ->
			// A scene left blank only by its images goes; one empty as stored is kept, as Hammer's export does.
			val scenes = chapter.scenes.mapNotNull { scene ->
				stripHammerImages(scene).takeUnless { it.isBlank() && !scene.isBlank() }
			}
			if (request.topLevelAsChapters) {
				append("\n## ")
				if (numberChapters) append(i + 1).append(". ")
				append(chapter.name).append("\n\n")
			} else {
				if (scenes.isEmpty() && chapter.scenes.isNotEmpty()) return@forEachIndexed
				if (wroteItem) append("\n\n")
			}
			wroteItem = true
			append(scenes.joinToString("\n\n")).append('\n')
		}
	}
}

private val hammerImage = Regex.escape(IMAGE_REFERENCE_PREFIX).let { prefix ->
	Regex("""!\[[^\]\r\n]*\]\([ \t]*(?:<$prefix[0-9]+>|$prefix[0-9]+)[ \t]*\)""")
}

private fun Char?.isSpace() = this == ' ' || this == '\t'

// Removes each image written as `![alt](hammer://image/<id>)`, the form Hammer writes. A line left blank
// by it goes, and when the image was a paragraph of its own, so does one blank line beside it, keeping
// paragraphs one blank line apart.
internal fun stripHammerImages(markdown: String): String {
	if (IMAGE_REFERENCE_PREFIX !in markdown) return markdown
	val lines = markdown.split('\n')
	val stripped = lines.map(::stripLine)
	val gone = BooleanArray(lines.size) { stripped[it] != lines[it] && stripped[it].isBlank() }
	val kept = ArrayList<String>(lines.size)
	var i = 0
	while (i < lines.size) {
		if (!gone[i]) {
			kept += stripped[i]
			i++
			continue
		}
		var next = i + 1
		while (next < lines.size && gone[next]) next++
		val blankBefore = kept.isEmpty() || kept.last().isBlank()
		val blankAfter = next == lines.size || lines[next].isBlank()
		if (blankBefore && blankAfter) {
			if (next < lines.size) next++ else if (kept.isNotEmpty()) kept.removeAt(kept.lastIndex)
		}
		i = next
	}
	return kept.joinToString("\n")
}

// Closes up the space an image leaves, so none is left doubled, leading, or trailing (a trailing double
// space would be a hard break). The `\r` of a CRLF line is kept as the line's end.
private fun stripLine(line: String): String {
	if (IMAGE_REFERENCE_PREFIX !in line) return line
	return buildString {
		var from = 0
		for (match in hammerImage.findAll(line)) {
			append(line, from, match.range.first)
			from = match.range.last + 1
			if ((isEmpty() || last().isSpace()) && line.getOrNull(from).isSpace()) from++
		}
		val end = if (line.endsWith('\r')) line.lastIndex else line.length
		if (from == end) while (lastOrNull().isSpace()) deleteAt(lastIndex)
		append(line, from, line.length)
	}
}
