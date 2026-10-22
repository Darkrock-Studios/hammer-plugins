// Fantasy names: an action whose page makes up names in a style, each with a button that adds it to
// the project's encyclopedia as a person. The page's state holds the list's seed and options, so
// pressing a name rebuilds the same list, marking the names already added.
//
// Each style is a directory, resources/styles/<style>/, of lists of real names for its made-up ones to
// sound like, a name on each line: feminine.txt and masculine.txt, and any others its format names.
// A full name follows format-feminine.txt or format-masculine.txt, or format.txt for both, such as
// "{given} ap {masculine}" or "{given} of {places}", where {given} is a name of the kind asked for,
// and any other part one like those of the list of that name. resources/styles/everyday.txt holds
// today's names, in lower case, which are never made up. Adding a style is a directory and an option
// in the manifest's style field.
import hammer.ActionReply
import hammer.ActionReply.Button
import hammer.ActionReply.Page
import hammer.ActionRequest
import hammer.OperationException
import hammer.input
import hammer.ops.EntryKind
import hammer.ops.Hammer
import hammer.resourceText
import hammer.state
import hammer.stateOf
import kotlin.random.Random
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.put

const val MAX_NAMES = 20

@Serializable
class NamesInput(
	val style: String = "elvish",
	val gender: String = "any",
	val count: Int = 8,
	val bynames: Boolean = true,
)

/**
 * Makes up names that sound like [names]: each letter is one that follows the three before it
 * somewhere in them, as often as it does there.
 */
class NameMaker(names: List<String>) {
	private val following = HashMap<String, StringBuilder>()
	private val lengths: IntRange

	init {
		if (names.isEmpty()) error("A style has an empty list of names")
		for (name in names) {
			val letters = START + name.lowercase() + END
			for (i in START.length until letters.length) {
				following.getOrPut(letters.substring(i - START.length, i), ::StringBuilder).append(letters[i])
			}
		}
		// As long as all but the shortest and longest tenths of the names.
		val sorted = names.map { it.length }.sorted()
		lengths = maxOf(4, sorted[sorted.size / 10])..maxOf(4, sorted[sorted.size * 9 / 10])
	}

	/** A name of a usual length that [taken] does not hold, or after many tries, any name. */
	fun make(random: Random, taken: (String) -> Boolean = { false }): String {
		var name = ""
		repeat(200) {
			val letters = StringBuilder(START)
			while (letters.last() != END && letters.length < 40) {
				letters.append(following.getValue(letters.substring(letters.length - START.length)).random(random))
			}
			name = letters.trim { it == '^' || it == END }.toString()
			if (name.length in lengths && !taken(name)) return name.capital()
		}
		return name.capital()
	}

	private fun String.capital() = replaceFirstChar { it.uppercase() }

	private companion object {
		const val START = "^^^"
		const val END = '$'
	}
}

/** The style's names in `<file>.txt`, or null when it has none. */
fun realNames(style: String, file: String): List<String>? =
	resourceText("styles/$style/$file.txt")?.lines()?.filter { it.isNotBlank() }?.takeUnless { it.isEmpty() }

/** The names [seed] gives for [input]: none of them real or everyday, and no part of them twice. */
fun generate(seed: Long, input: NamesInput): List<String> {
	val style = input.style
	val taken = HashSet<String>()
	resourceText("styles/everyday.txt")?.lineSequence()?.let(taken::addAll)
	// Each list's maker, or null for a list the style lacks.
	val makers = HashMap<String, NameMaker?>()
	fun maker(list: String) = makers.getOrPut(list) {
		realNames(style, list)?.let { names ->
			names.mapTo(taken) { it.lowercase() }
			NameMaker(names)
		}
	}
	if (maker("feminine") == null || maker("masculine") == null) error("There are no names in that style")
	val random = Random(seed)
	fun make(list: String): String {
		val maker = maker(list) ?: error("The style's format asks for names it has none of")
		return maker.make(random, taken::contains).also { taken += it.lowercase() }
	}

	val kinds = listOf("feminine", "masculine").filter { input.gender == "any" || input.gender == it }.ifEmpty { listOf("feminine", "masculine") }
	val formats = kinds.associateWith { kind ->
		val format = if (input.bynames) resourceText("styles/$style/format-$kind.txt") ?: resourceText("styles/$style/format.txt") else null
		format?.trim() ?: "{given}"
	}
	return List(input.count.coerceIn(1, MAX_NAMES)) {
		val kind = kinds.random(random)
		val name = PART.replace(formats.getValue(kind)) { part ->
			make(part.groupValues[1].let { if (it == "given") kind else it })
		}
		// An ending may add a third of a name's last letter, as "sson" does to Magnus.
		TRIPLED.replace(name) { it.value.take(2) }
	}
}

private val PART = Regex("\\{([a-z]+)\\}")
private val TRIPLED = Regex("(\\p{L})\\1\\1")

/** What a page of names is made from: the seed and options that give its names, and those added, as bits. */
@Serializable
class NamesState(val seed: Long, val options: NamesInput, val added: Int = 0) {
	fun isAdded(index: Int) = added and (1 shl index) != 0
}

/** The page listing [names] from [state], those added marked, with a button to add each of the rest. */
fun page(state: NamesState, names: List<String> = generate(state.seed, state.options), message: String? = null): ActionReply {
	val list = names.withIndex().joinToString("\n") { (i, name) -> if (state.isAdded(i)) "- $name (added)" else "- $name" }
	val buttons = names.withIndex().filterNot { state.isAdded(it.index) }.map { (i, name) -> Button("add:$i", name, inline = true) }
	return ActionReply(
		Page(
			markdown = "Press a name to add it to the encyclopedia as a person.\n\n$list\n",
			buttons = buttons + Button("more", "More names"),
			state = stateOf(state),
		),
		message,
	)
}

fun names(request: ActionRequest): ActionReply {
	val button = request.button ?: return page(NamesState(Random.nextLong(), request.input()))
	// Only the manifest's page has the options, which Hammer remembers for running the action again.
	val state = request.state<NamesState>() ?: return ActionReply.message("That list of names is out of date; run the action again")
	// A button's id is "add:<index>", or "more".
	if (!button.startsWith("add:")) return page(NamesState(Random.nextLong(), state.options))
	val names = generate(state.seed, state.options)
	val index = button.removePrefix("add:").toIntOrNull()?.takeIf { it in names.indices } ?: error("That name is no longer in the list")
	val name = names[index]
	return try {
		Hammer.entry.create(request.project, name, EntryKind.Person, text = "")
		page(NamesState(state.seed, state.options, state.added or (1 shl index)), names, "Added $name to the encyclopedia")
	} catch (e: OperationException) {
		ActionReply(errors = mapOf("" to e.message.orEmpty()))
	}
}
