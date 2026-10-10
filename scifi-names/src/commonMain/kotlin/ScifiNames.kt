// Science fiction names: interactive actions that make up names of a kind, aliens, worlds, or ships,
// each name with a button that adds it to the project's encyclopedia. The list comes from a seed
// carried in the buttons' ids, so pressing one rebuilds the same list, marking the names already added.
//
// Each action is a kind, and each kind a directory of styles, resources/<kind>/<style>/. A style's
// format.txt holds the patterns its names follow, one on each line, picked from as often as each is
// there; without one a name is "{names}", and an alien's second name another. In a pattern, {list} is a made-up name that sounds like
// those of <list>.txt, [list] one of its lines as it is, # a digit, and @ a capital letter. A list is
// the style's, or else the kind's, for all its styles. Adding a style is a directory and an option
// in the manifest's style field for the action.
import hammer.ActionReply
import hammer.ActionRequest
import hammer.OperationException
import hammer.callJson
import hammer.input
import hammer.resourceText
import kotlin.random.Random
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

const val MAX_NAMES = 20

/** The encyclopedia entry type of each kind's names. */
val KINDS = mapOf("aliens" to "person", "worlds" to "place", "ships" to "thing")

@Serializable
class NamesInput(
	val style: String = "",
	val count: Int = 8,
	/** Whether an alien has a second name. */
	val second: Boolean = true,
	/** Whether a ship's name follows a prefix, such as ISV. */
	val prefix: Boolean = true,
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
		val pattern = when {
			kind == "aliens" && !input.second -> "{names}"
			kind == "aliens" -> "{names} {names}"
			kind == "ships" && input.prefix -> "[prefixes] " + patterns.random(random)
			else -> patterns.random(random)
		}
		names += fill(pattern)
	}
	return names.toList()
}

private val PART = Regex("\\{([a-z]+)\\}|\\[([a-z]+)\\]|#|@")

/** The reply for [names] from [seed], those in [added]'s bits marked, with a button to add each of the rest as a [type]. */
fun reply(seed: Long, type: String, names: List<String>, added: Int, message: String? = null): ActionReply {
	fun isAdded(index: Int) = added and (1 shl index) != 0
	val list = names.withIndex().joinToString("\n") { (i, name) -> if (isAdded(i)) "- $name (added)" else "- $name" }
	val buttons = names.withIndex().filterNot { isAdded(it.index) }.map { (i, name) -> ActionReply.Button("add:$seed:$i:$added", name) }
	return ActionReply(
		"Press a name to add it to the encyclopedia as a $type.\n\n$list\n",
		buttons + ActionReply.Button("more", "More names", footer = true),
		message,
	)
}

fun names(request: ActionRequest): ActionReply {
	val kind = request.action
	val type = KINDS[kind] ?: error("There is no such action")
	val input = request.input<NamesInput>()
	// A button's id is "add:<seed>:<index>:<names added so far, as bits>", or "more".
	val button = request.button?.split(':')?.takeIf { it.size == 4 && it[0] == "add" }
	val seed = button?.get(1)?.toLongOrNull() ?: return Random.nextLong().let { reply(it, type, generate(it, kind, input), 0) }
	val index = button[2].toIntOrNull()
	val added = button[3].toIntOrNull() ?: 0
	val names = generate(seed, kind, input)
	val name = index?.let(names::getOrNull) ?: error("That name is no longer in the list")
	return try {
		callJson("entry.create", buildJsonObject {
			put("project", request.project)
			put("type", type)
			put("text", "")
			put("name", name)
		})
		reply(seed, type, names, added or (1 shl index), "Added $name to the encyclopedia")
	} catch (e: OperationException) {
		reply(seed, type, names, added, e.message)
	}
}
