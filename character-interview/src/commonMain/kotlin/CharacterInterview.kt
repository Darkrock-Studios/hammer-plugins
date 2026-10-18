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
import hammer.TextFormat
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
val SETS = linkedMapOf("quick" to "Quick", "full" to "Full", "proust" to "Proust questionnaire")

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
		return ActionReply(namePage(recover(request.state)), "That interview was out of date, so it starts again here")
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

/**
 * Why Hammer would refuse [name], already trimmed, as an entry's name, or null. Letters outside the Basic
 * Multilingual Plane are surrogate pairs here, judged one char at a time, so they are let through for
 * Hammer to judge when the entry is created.
 */
fun nameProblem(name: String): String? {
	val refused = name.filterNot { it.isLetter() || it in '0'..'9' || it in NAME_MARKS || it.isSurrogate() }.toSet()
	return when {
		name.isEmpty() -> "Enter a name"
		name.length > MAX_NAME -> "A name is at most $MAX_NAME characters"
		refused.isNotEmpty() -> "A name cannot have ${refused.joinToString(" ")}"
		name.endsWith('.') -> "A name cannot end with a dot"
		else -> null
	}
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
			Field.text("q${it.number}", it.text, default = interview.answer(it), format = TextFormat.Rich)
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
		for ((question, answer) in answered) append("**${question.text}**\n\n$answer\n\n")
	}
}.trimEnd()

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
