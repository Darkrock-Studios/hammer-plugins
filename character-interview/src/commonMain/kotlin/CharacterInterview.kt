// Character interview: an action that puts one of the sets of questions in resources/sets/ to a
// character, a page at a time, then writes the answers into a new encyclopedia entry for them. Every
// page's state is the whole run, so each call starts from the request alone, and Hammer can keep a run
// the user leaves (`keep` in the manifest) and pick it up later.
import hammer.ActionReply
import hammer.ActionReply.Button
import hammer.ActionReply.Field
import hammer.ActionReply.Open
import hammer.ActionReply.Page
import hammer.ActionRequest
import hammer.OperationException
import hammer.call
import hammer.input
import hammer.state
import hammer.stateOf
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/** The interviews by id, each resources/sets/<id>.md, in the order the manifest's set field offers them. */
val SETS = mapOf("quick" to "Quick", "full" to "Full", "proust" to "Proust questionnaire")

const val DEFAULT_SET = "quick"

/** The longest name Hammer takes for an entry. */
const val MAX_NAME = 64

/**
 * A run of the interview. [step] is the page showing: 0 for the name and set, 1 to the set's page count
 * for its questions, and one more for the review. [answers] are by `<set>/<question number>`, so each set
 * keeps its own when the user switches.
 */
@Serializable
data class Interview(val name: String, val set: String, val step: Int, val answers: Map<String, String> = emptyMap()) {
	fun answer(question: Question): String? = answers["$set/${question.number}"]?.trim()?.ifEmpty { null }
}

@Serializable
private class Start(val name: String = "", val set: String = DEFAULT_SET)

@Serializable
private class Created(val id: Int)

fun interview(request: ActionRequest): ActionReply {
	// No button: Run on the manifest's page.
	val button = request.button ?: return begin(request, Interview("", DEFAULT_SET, 0))
	val saved = request.state<Interview>()
	val set = saved?.set?.takeIf { it in SETS }?.let(::questionSet)
	if (saved == null || set == null || saved.step !in 0..set.pages.size + 1) {
		return ActionReply(namePage(recover(request.state)), "This interview could not carry on from where it was left, so it starts again here")
	}
	val interview = saved.answering(set, request.input)
	val review = set.pages.size + 1
	return when {
		button == "back" -> show(interview.copy(step = maxOf(interview.step - 1, 0)), set)
		button == "next" && interview.step == 0 -> begin(request, interview)
		button == "next" -> show(interview.copy(step = minOf(interview.step + 1, review)), set)
		button == "create" && interview.step == review -> create(request.project, interview, set)
		else -> show(interview, set)
	}
}

/** The first page of questions, for the name and set in [request]'s input; or what is wrong with them. */
private fun begin(request: ActionRequest, interview: Interview): ActionReply {
	val start = request.input<Start>()
	val name = start.name.trim()
	val errors = buildMap {
		nameProblem(name)?.let { put("name", it) }
		if (start.set !in SETS) put("set", "Pick one of the interviews")
	}
	if (errors.isNotEmpty()) return ActionReply(errors = errors)
	return show(interview.copy(name = name, set = start.set, step = 1), questionSet(start.set))
}

/** Why Hammer would refuse [name], already trimmed, as an entry's name, or null. */
fun nameProblem(name: String): String? {
	val refused = LinkedHashSet<String>()
	var at = 0
	while (at < name.length) {
		val size = if (name[at].isHighSurrogate() && name.getOrNull(at + 1)?.isLowSurrogate() == true) 2 else 1
		val character = name.substring(at, at + size)
		if (!allowedInName(character)) refused += character
		at += size
	}
	return when {
		name.isEmpty() -> "Enter a name"
		name.length > MAX_NAME -> "A name is at most $MAX_NAME characters"
		refused.isNotEmpty() -> "A name cannot have ${refused.joinToString(" ")}"
		name.endsWith('.') -> "A name cannot end with a dot"
		else -> null
	}
}

/** Whether Hammer takes [character], one char or a surrogate pair, in a name. */
private fun allowedInName(character: String): Boolean {
	val first = character[0]
	if (character.length == 1) return first.isLetter() || first in '0'..'9' || first in NAME_MARKS
	// Past U+FFFF Hammer takes only letters, which Kotlin cannot pick out here, so this takes the ranges they
	// lie in: below U+1F000 (old scripts and mathematical letters, among a few symbols Hammer refuses) and
	// planes 2 and 3 (ideographs). Emoji and other symbols fill U+1F000 to U+1FFFF.
	val code = 0x10000 + ((first.code - 0xD800) shl 10) + (character[1].code - 0xDC00)
	return code < 0x1F000 || code in 0x20000..0x3FFFF
}

/** What an entry's name may have besides letters and digits; `~` is not among them. */
private const val NAME_MARKS = " +_'-.,!?:()&\"/\\*|<>’“”"

/** This run with the answers on its page of questions taken from [input], those left blank removed. */
private fun Interview.answering(questions: QuestionSet, input: JsonObject): Interview {
	val page = questions.pages.getOrNull(step - 1) ?: return this
	val answers = answers.toMutableMap()
	for (question in page.questions) {
		val value = (input["q${question.number}"] as? JsonPrimitive)?.contentOrNull?.trim() ?: continue
		val key = "$set/${question.number}"
		if (value.isEmpty()) answers.remove(key) else answers[key] = value
	}
	return copy(answers = answers)
}

/**
 * What a state that is not a run of a set this plugin has still says, back on the first page: the name,
 * the set if it still exists, and the answers.
 */
private fun recover(state: JsonElement?): Interview {
	val fields = state as? JsonObject
	fun text(value: JsonElement?) = (value as? JsonPrimitive)?.takeIf { it.isString }?.content
	val answers = (fields?.get("answers") as? JsonObject)?.entries?.mapNotNull { (key, value) -> text(value)?.let { key to it } }
	return Interview(
		text(fields?.get("name")).orEmpty().trim(),
		text(fields?.get("set"))?.takeIf { it in SETS } ?: DEFAULT_SET,
		0,
		answers.orEmpty().toMap(),
	)
}

private fun show(interview: Interview, set: QuestionSet): ActionReply = ActionReply(
	when (interview.step) {
		0 -> namePage(interview)
		in 1..set.pages.size -> questionPage(interview, set)
		else -> reviewPage(interview, set)
	},
)

/** The manifest's page again, filled in from [interview]; titled as Hammer titles that page. */
private fun namePage(interview: Interview) = Page(
	fields = listOf(
		Field.text("name", "Name", required = true, default = interview.name),
		Field.choice("set", "Interview", setOptions(), default = interview.set),
	),
	buttons = listOf(Button("next", "Next", primary = true)),
	state = stateOf(interview.copy(step = 0)),
)

/** The set field's options, as the manifest has them. */
fun setOptions(): List<Field.Option> =
	SETS.map { (id, title) -> Field.Option(id, "$title (${questionSet(id).questions.size} questions)") }

private fun questionPage(interview: Interview, set: QuestionSet): Page {
	val page = set.pages[interview.step - 1]
	val last = interview.step == set.pages.size
	return Page(
		title = "${interview.name}: ${page.heading} (${interview.step} of ${set.pages.size})",
		markdown = page.intro,
		fields = page.questions.map {
			Field.text("q${it.number}", it.text, default = interview.answer(it), multiline = true)
		},
		buttons = listOf(
			Button("back", "Back", validate = false),
			Button("next", if (last) "Review" else "Next", primary = true),
		),
		state = stateOf(interview),
	)
}

/**
 * The entry as it will be written. Creating it with nothing answered is allowed: an entry with only the
 * name is still one the user may want, as the name generators add.
 */
private fun reviewPage(interview: Interview, set: QuestionSet) = Page(
	title = "${interview.name}: Review",
	markdown = entryText(interview, set).ifEmpty { "No question has an answer, so the entry for ${interview.name} will have no text." },
	buttons = listOf(
		Button("back", "Back", validate = false),
		Button("create", "Create entry", primary = true),
	),
	state = stateOf(interview),
)

/** The entry's text: for each page with answers its heading, then each answered question in bold and its answer. */
fun entryText(interview: Interview, set: QuestionSet): String = buildString {
	for (page in set.pages) {
		val answered = page.questions.mapNotNull { question -> interview.answer(question)?.let { question to it } }
		if (answered.isEmpty()) continue
		append("## ${page.heading}\n\n")
		for ((question, answer) in answered) append("**${question.text}**\n\n${answerMarkdown(answer)}\n\n")
	}
}.trimEnd()

/**
 * [answer] as it goes under its question: as typed, line for line, but with its headings moved down, in step,
 * until the highest is `###`, so that under the page's `##` a heading in an answer cannot start a section of the
 * entry; and a code fence it leaves open closed, so that it cannot take in the questions after it. Text over
 * a line of `=` is a heading in Hammer, so it is written with `#` to be moved down with the rest. Lines in a
 * fenced code block are left alone.
 */
private fun answerMarkdown(answer: String): String {
	val lines = answer.lines()
	val fenced = BooleanArray(lines.size)
	val levels = IntArray(lines.size)
	// The lines of each heading over a line of `=`, and those lines of `=`.
	val setext = BooleanArray(lines.size)
	val underlines = BooleanArray(lines.size)
	var fence: String? = null
	for ((index, line) in lines.withIndex()) {
		val open = fence
		fence = if (open == null) fenceMarker(line) else open.takeUnless { closesFence(line, it) }
		if (open != null || fence != null) {
			fenced[index] = true
			continue
		}
		levels[index] = headingLevel(line)
		if (!SETEXT_EQUALS.matches(line)) continue
		var first = index
		while (first > 0 && !fenced[first - 1] && !underlines[first - 1] && isParagraphText(lines[first - 1])) first--
		// Text right after a list item's line is the item's, which no underline makes a heading.
		if (first == index || first > 0 && !fenced[first - 1] && LIST_ITEM_START.containsMatchIn(lines[first - 1])) continue
		for (heading in first until index) {
			levels[heading] = 1
			setext[heading] = true
		}
		underlines[index] = true
	}
	val shift = 3 - (levels.filter { it > 0 }.minOrNull() ?: 3)
	val nested = if (shift <= 0) lines else lines.indices.mapNotNull { index ->
		val line = lines[index]
		val level = levels[index]
		when {
			underlines[index] -> null
			setext[index] -> "#".repeat(1 + shift) + " " + line.trim().let { text ->
				ATX_CLOSING.find(text)?.let { text.substring(0, it.range.first) + it.value.replaceFirst("#", "\\#") } ?: text
			}
			level == 0 -> line
			else -> {
				val indent = line.length - line.trimStart(' ').length
				line.substring(0, indent) + "#".repeat(minOf(shift, 6 - level)) + line.substring(indent)
			}
		}
	}
	return nested.joinToString("\n") + if (fence != null) "\n$fence" else ""
}

/** The level of the ATX heading [line] is, or 0 when it is not one: up to three spaces, `#` to `######`, then a space or nothing. */
private fun headingLevel(line: String): Int {
	val text = line.trimStart(' ')
	if (line.length - text.length > 3) return 0
	val level = text.takeWhile { it == '#' }.length
	val after = text.getOrNull(level)
	return if (level in 1..6 && (after == null || after == ' ' || after == '\t')) level else 0
}

/** The run of three or more backticks or tildes that [line] opens a code fence with, after up to three spaces, or null. */
private fun fenceMarker(line: String): String? {
	val text = line.trimStart(' ')
	if (line.length - text.length > 3 || text.isEmpty() || text[0] != '`' && text[0] != '~') return null
	val run = text.takeWhile { it == text[0] }
	// A backtick fence's info string cannot have a backtick: such a line is a code span.
	return run.takeIf { it.length >= 3 && !(run[0] == '`' && text.indexOf('`', run.length) >= 0) }
}

/** Whether [line] closes the fence [open] opened: as long a run of its character or longer, and nothing else. */
private fun closesFence(line: String, open: String): Boolean {
	val marker = fenceMarker(line) ?: return false
	return marker[0] == open[0] && marker.length >= open.length && line.trimStart(' ').substring(marker.length).isBlank()
}

/** Whether [line] is a paragraph's text in Hammer, not the start of another block. */
private fun isParagraphText(line: String): Boolean =
	line.isNotBlank() && !line.startsWith("    ") && !line.startsWith("\t") && !line.trimStart().startsWith(">") &&
		!LIST_ITEM_START.containsMatchIn(line) && headingLevel(line) == 0 && !THEMATIC_BREAK.matches(line)

private val SETEXT_EQUALS = Regex("""^ {0,3}=+[ \t]*$""")
private val LIST_ITEM_START = Regex("""^[ \t]*(?:[-*+]|\d{1,9}[.)])(?:[ \t]|$)""")
private val THEMATIC_BREAK = Regex("""^ {0,3}([-*_])(?:[ \t]*\1){2,}[ \t]*$""")

/** A closing run of `#`, which a heading written with `#` would drop as markup. */
private val ATX_CLOSING = Regex("""(?:^|[ \t]+)#+[ \t]*$""")

private fun create(project: String, interview: Interview, set: QuestionSet): ActionReply = try {
	val entry = call<Created>("entry.create", buildJsonObject {
		put("project", project)
		put("name", interview.name)
		put("type", "person")
		put("text", entryText(interview, set))
	})
	ActionReply(message = "Added ${interview.name} to the encyclopedia", open = Open("entry", entry.id))
} catch (e: OperationException) {
	ActionReply(errors = mapOf("" to (e.message ?: "Hammer could not add ${interview.name}")))
}
