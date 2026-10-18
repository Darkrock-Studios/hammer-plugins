// Science fiction names: actions whose pages make up names of a kind, characters, worlds, or things,
// each name with a button that adds it to the project's encyclopedia. The page's state holds the
// list's seed and options, so pressing a name rebuilds the same list, marking the names already
// added.
//
// Each action is a kind, and each kind a directory of styles, resources/<kind>/<style>/. A style's
// format.txt holds the patterns its names follow, one on each line, picked from as often as each is
// there; without one a name is "{names}". In a pattern, {list} is a made-up name that sounds like
// those of <list>.txt, [list] one of its lines as it is, # a digit, and @ a capital letter. A list is
// the style's, or else the kind's, for all its styles. A style's long.txt holds the patterns of its
// names' long form, in which * is the name. Adding a style is a directory and an option in the
// manifest's style field for the action.
import hammer.ActionReply
import hammer.ActionReply.Button
import hammer.ActionReply.Page
import hammer.ActionRequest
import hammer.OperationException
import hammer.callJson
import hammer.input
import hammer.resourceText
import hammer.state
import hammer.stateOf
import kotlin.random.Random
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

const val MAX_NAMES = 20

/** The encyclopedia entry type of each kind's names. */
val KINDS = mapOf("characters" to "person", "worlds" to "place", "things" to "thing")

@Serializable
class NamesInput(
	val style: String = "",
	val count: Int = 8,
	/** Whether names are in the style's long form, if it has one: an alien's two names, or a ship's after its prefix. */
	val long: Boolean = true,
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
		lengths = sorted[sorted.size / 10]..sorted[sorted.size * 9 / 10]
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

/** The lines of a style's `<list>.txt`, or of its kind's, or null when neither has it. */
fun lines(kind: String, style: String, list: String): List<String>? =
	(resourceText("$kind/$style/$list.txt") ?: resourceText("$kind/$list.txt"))?.lines()?.filter { it.isNotBlank() }?.takeUnless { it.isEmpty() }

/** The names [seed] gives for [input] of a [kind]: no two alike, and none made up that is in its list. */
fun generate(seed: Long, kind: String, input: NamesInput): List<String> {
	val style = input.style
	// A style has a format or a list of names, if no more.
	val patterns = lines(kind, style, "format") ?: lines(kind, style, "names")?.let { listOf("{names}") }
	if (kind !in KINDS || !style.all { it.isLetterOrDigit() || it == '-' } || patterns == null) error("There are no names in that style")
	val long = if (input.long) resourceText("$kind/$style/long.txt")?.lines()?.filter { it.isNotBlank() }.orEmpty() else emptyList()
	val random = Random(seed)
	val taken = HashSet<String>()
	val lists = HashMap<String, List<String>>()
	val makers = HashMap<String, NameMaker>()
	fun list(name: String) = lists.getOrPut(name) {
		lines(kind, style, name) ?: error("The style's format asks for a list it has none of")
	}

	fun fill(pattern: String): String {
		val name = StringBuilder()
		var end = 0
		for (part in PART.findAll(pattern)) {
			name.append(pattern, end, part.range.first)
			end = part.range.last + 1
			val (made, picked) = part.destructured
			when {
				made.isNotEmpty() -> {
					val maker = makers.getOrPut(made) { NameMaker(list(made).onEach { taken += it.lowercase() }) }
					name.append(maker.make(random, taken::contains).also { taken += it.lowercase() })
				}
				picked.isNotEmpty() -> name.append(list(picked).random(random))
				part.value == "@" -> name.append('A' + random.nextInt(26))
				// A number does not start with a zero.
				else -> name.append(if (name.lastOrNull()?.isDigit() == true || name.endsWith('.')) random.nextInt(10) else random.nextInt(1, 10))
			}
		}
		return name.append(pattern, end, pattern.length).toString()
	}

	val names = LinkedHashSet<String>()
	val count = input.count.coerceIn(1, MAX_NAMES)
	// A style of few words may not have that many names: it gives what it finds.
	repeat(count * 20) {
		if (names.size == count) return@repeat
		val pattern = patterns.random(random)
		names += fill(if (long.isEmpty()) pattern else long.random(random).replace("*", pattern))
	}
	return names.toList()
}

private val PART = Regex("\\{([a-z]+)\\}|\\[([a-z]+)\\]|#|@")

/** What a page of names is made from: the seed and options that give its names, and those added, as bits. */
@Serializable
class NamesState(val seed: Long, val options: NamesInput, val added: Int = 0) {
	fun isAdded(index: Int) = added and (1 shl index) != 0
}

/** The page listing [names] from [state], those added marked, with a button to add each of the rest as a [type]. */
fun page(state: NamesState, type: String, names: List<String>, message: String? = null): ActionReply {
	val list = names.withIndex().joinToString("\n") { (i, name) -> if (state.isAdded(i)) "- $name (added)" else "- $name" }
	val buttons = names.withIndex().filterNot { state.isAdded(it.index) }.map { (i, name) -> Button("add:$i", name, inline = true) }
	return ActionReply(
		Page(
			markdown = "Press a name to add it to the encyclopedia as a $type.\n\n$list\n",
			buttons = buttons + Button("more", "More names"),
			state = stateOf(state),
		),
		message,
	)
}

fun names(request: ActionRequest): ActionReply {
	val kind = request.action
	val type = KINDS[kind] ?: error("There is no such action")
	fun fresh(options: NamesInput) = NamesState(Random.nextLong(), options).let { page(it, type, generate(it.seed, kind, options)) }
	val button = request.button ?: return fresh(request.input())
	// Only the manifest's page has the options, which Hammer remembers for running the action again.
	val state = request.state<NamesState>() ?: return ActionReply.message("That list of names is out of date; run the action again")
	// A button's id is "add:<index>", or "more".
	if (!button.startsWith("add:")) return fresh(state.options)
	val names = generate(state.seed, kind, state.options)
	val index = button.removePrefix("add:").toIntOrNull()?.takeIf { it in names.indices } ?: error("That name is no longer in the list")
	val name = names[index]
	return try {
		callJson("entry.create", buildJsonObject {
			put("project", request.project)
			put("type", type)
			put("text", "")
			put("name", name)
		})
		page(NamesState(state.seed, state.options, state.added or (1 shl index)), type, names, "Added $name to the encyclopedia")
	} catch (e: OperationException) {
		ActionReply(errors = mapOf("" to e.message.orEmpty()))
	}
}
