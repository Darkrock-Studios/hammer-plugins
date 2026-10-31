// Markdown export: the story as one Markdown document, under the project's name as its title, with
// each chapter a heading and each scene's Markdown as Hammer stores it (`input = "markdown"`).
//
// An image stays a `hammer://image/<id>` reference: Markdown input carries no image names, and the
// manifest does not ask for the images' files, which this plugin could not use.
import hammer.ExportRequest
import hammer.settings
import kotlinx.serialization.Serializable

@Serializable
class MarkdownSettings(val numberChapters: Boolean = false)

fun markdownExport(request: ExportRequest): String {
	val numberChapters = request.settings<MarkdownSettings>().numberChapters
	return buildString {
		append("# ").append(request.projectName).append("\n\n")
		request.chapters.forEachIndexed { i, chapter ->
			if (request.topLevelAsChapters) {
				append("\n## ")
				if (numberChapters) append(i + 1).append(". ")
				append(chapter.name).append("\n\n")
			} else if (i > 0) {
				append("\n\n")
			}
			append(chapter.scenes.joinToString("\n\n")).append('\n')
		}
	}
}
