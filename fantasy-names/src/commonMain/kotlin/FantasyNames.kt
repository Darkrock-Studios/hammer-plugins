// Fantasy names: an interactive action that makes up names in a style, each with a button that adds it
// to the project's encyclopedia as a person. The list comes from a seed carried in the buttons' ids,
// so pressing one rebuilds the same list, marking the names already added.
//
// Each style is a directory, resources/styles/<style>/, of real names for its made-up ones to sound
// like, a name on each line: feminine.txt and masculine.txt, and optionally bynames.txt. A full name
// follows format-feminine.txt or format-masculine.txt, or format.txt for both, such as
// "{given} ap {masculine}", where {given} is a name of the kind asked for, {feminine} and {masculine}
// one of that kind, and {byname} one like the bynames. resources/styles/everyday.txt holds today's
// names, in lower case, which are never made up. Adding a style is a directory and an option in the
// manifest's style field.
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
	val real = listOf("feminine", "masculine", "bynames").associateWith { realNames(style, it) }
	if (real["feminine"] == null || real["masculine"] == null) error("There are no names in that style")
	val taken = real.values.filterNotNull().flatten().mapTo(HashSet()) { it.lowercase() }
	resourceText("styles/everyday.txt")?.lineSequence()?.let(taken::addAll)
	val makers = HashMap<String, NameMaker>()
	val random = Random(seed)
	fun make(list: String): String {
		val maker = makers.getOrPut(list) { NameMaker(real[list] ?: error("The style's format asks for names it has none of")) }
		return maker.make(random, taken::contains).also { taken += it.lowercase() }
	}

	val kinds = listOf("feminine", "masculine").filter { input.gender == "any" || input.gender == it }.ifEmpty { listOf("feminine", "masculine") }
	val formats = kinds.associateWith { kind ->
		val format = if (input.bynames) resourceText("styles/$style/format-$kind.txt") ?: resourceText("styles/$style/format.txt") else null
		format?.trim() ?: "{given}"
	}
	return List(input.count.coerceIn(1, MAX_NAMES)) {
		val kind = kinds.random(random)
		val name = PART.replace(formats.getValue(kind)) {
			when (it.value) {
				"{given}" -> make(kind)
				"{byname}" -> make("bynames")
				else -> make(it.value.trim('{', '}'))
			}
		}
		// An ending may add a third of a name's last letter, as "sson" does to Magnus.
		TRIPLED.replace(name) { it.value.take(2) }
	}
}

private val PART = Regex("\\{given\\}|\\{feminine\\}|\\{masculine\\}|\\{byname\\}")
private val TRIPLED = Regex("(\\p{L})\\1\\1")

/** The reply for [names] from [seed], those in [added]'s bits marked, with a button to add each of the rest. */
fun reply(seed: Long, names: List<String>, added: Int, message: String? = null): ActionReply {
	fun isAdded(index: Int) = added and (1 shl index) != 0
	val list = names.withIndex().joinToString("\n") { (i, name) -> if (isAdded(i)) "- $name (added)" else "- $name" }
	val buttons = names.withIndex().filterNot { isAdded(it.index) }.map { (i, name) -> ActionReply.Button("add:$seed:$i:$added", name) }
	return ActionReply(
		"Press a name to add it to the encyclopedia as a person.\n\n$list\n",
		buttons + ActionReply.Button("more", "More names", footer = true),
		message,
	)
}

fun names(request: ActionRequest): ActionReply {
	val input = request.input<NamesInput>()
	// A button's id is "add:<seed>:<index>:<names added so far, as bits>", or "more".
	val button = request.button?.split(':')?.takeIf { it.size == 4 && it[0] == "add" }
	val seed = button?.get(1)?.toLongOrNull() ?: return Random.nextLong().let { reply(it, generate(it, input), 0) }
	val index = button[2].toIntOrNull()
	val added = button[3].toIntOrNull() ?: 0
	val names = generate(seed, input)
	val name = index?.let(names::getOrNull) ?: error("That name is no longer in the list")
	return try {
		callJson("entry.create", buildJsonObject {
			put("project", request.project)
			put("type", "person")
			put("text", "")
			put("name", name)
		})
		reply(seed, names, added or (1 shl index), "Added $name to the encyclopedia")
	} catch (e: OperationException) {
		reply(seed, names, added, e.message)
	}
}
