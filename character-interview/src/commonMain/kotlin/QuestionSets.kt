// An interview's questions, written as markdown in resources/sets/<id>.md: each `## Heading` starts a
// page, a paragraph under it before its first question is the page's intro, and each `- ` bullet is a
// question.
import hammer.resourceText

/** An interview's pages in order. */
class QuestionSet(val pages: List<QuestionPage>) {
	val questions: List<Question> get() = pages.flatMap { it.questions }
}

class QuestionPage(val heading: String, val intro: String?, val questions: List<Question>)

/** A question and its [number] in the whole set, from 1. */
class Question(val number: Int, val text: String)

/** The set in resources/sets/[id].md; one that is missing or has no questions fails. */
fun questionSet(id: String): QuestionSet {
	val text = resourceText("sets/$id.md") ?: error("There is no interview named $id")
	return parseQuestionSet(text).takeIf { it.pages.isNotEmpty() } ?: error("The interview $id has no questions")
}

/**
 * The set written in [text]. A line of text after a question continues it; anything before the first
 * heading, and a page without questions, are left out.
 */
fun parseQuestionSet(text: String): QuestionSet {
	class Draft(val heading: String) {
		val intro = ArrayList<String>()
		val questions = ArrayList<String>()
	}

	val drafts = ArrayList<Draft>()
	for (raw in text.lines()) {
		val line = raw.trim()
		val page = drafts.lastOrNull()
		when {
			line.startsWith("## ") -> drafts += Draft(line.removePrefix("## ").trim())
			page == null -> continue
			line == "-" || line.startsWith("- ") -> page.questions += line.removePrefix("-").trim()
			page.questions.isNotEmpty() -> if (line.isNotEmpty()) page.questions[page.questions.lastIndex] += " $line"
			else -> page.intro += line
		}
	}

	var number = 0
	val pages = drafts.mapNotNull { draft ->
		val questions = draft.questions.filter { it.isNotEmpty() }.map { Question(++number, it) }
		if (questions.isEmpty()) null else QuestionPage(draft.heading, paragraphs(draft.intro), questions)
	}
	return QuestionSet(pages)
}

/** [lines] as markdown paragraphs, one blank line between each; null when there are none. */
private fun paragraphs(lines: List<String>): String? {
	val paragraphs = ArrayList<String>()
	var paragraph = ArrayList<String>()
	for (line in lines + "") {
		if (line.isNotEmpty()) {
			paragraph += line
		} else if (paragraph.isNotEmpty()) {
			paragraphs += paragraph.joinToString("\n")
			paragraph = ArrayList()
		}
	}
	return paragraphs.joinToString("\n\n").ifEmpty { null }
}
