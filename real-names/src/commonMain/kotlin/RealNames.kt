// Real-world names: an interactive action that lists names real people of a culture have, each with a
// button that adds it to the project's encyclopedia as a person. The list comes from a seed carried in
// the buttons' ids, so pressing one rebuilds the same list, marking the names already added.
//
// Each culture is a directory, resources/names/<culture>/, of lists with a name and how many people
// have it on each line, a tab between them: feminine.txt and masculine.txt, optionally
// feminine-<decade>.txt and masculine-<decade>.txt for those born in a decade, such as
// feminine-1950.txt, and optionally surnames.txt. A name is picked as often as it is common. Adding a
// culture is a directory and an option in the manifest's culture field.
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
	val culture: String = "american",
	/** The decade of birth, such as `1950`; one the culture has no lists for, as `any` is, gives names of all years. */
	val era: String = "any",
	val gender: String = "any",
	val count: Int = 8,
	val surnames: Boolean = true,
)

/** Names from [lines], each `name<tab>count`, picked as often as their counts say. */
class NameList(lines: List<String>) {
	private val names = ArrayList<String>()
	private val totals = ArrayList<Long>()

	init {
		for (line in lines) {
			if (line.isBlank()) continue
			val count = line.substringAfter('\t', "").trim().toLongOrNull() ?: 0
			if (count <= 0 || line.startsWith('\t')) error("A name list has a name without a count")
			names += line.substringBefore('\t')
			totals += (totals.lastOrNull() ?: 0) + count
		}
	}

	fun isEmpty() = names.isEmpty()

	operator fun contains(name: String) = name in names

	fun pick(random: Random): String {
		val at = random.nextLong(totals.last())
		return names[totals.indexOfFirst { it > at }]
	}
}

/** The culture's list in `<file>.txt`, or null when it has none. */
fun nameList(culture: String, file: String): NameList? =
	resourceText("names/$culture/$file.txt")?.let { NameList(it.lines()) }?.takeUnless { it.isEmpty() }

/** The names [seed] gives for [input]. */
fun generate(seed: Long, input: NamesInput): List<String> {
	val kinds = listOf("feminine", "masculine").filter { input.gender == "any" || input.gender == it }.ifEmpty { listOf("feminine", "masculine") }
	val given = kinds.map {
		nameList(input.culture, "$it-${input.era}") ?: nameList(input.culture, it) ?: error("There are no names for that culture")
	}
	val surnames = if (input.surnames) nameList(input.culture, "surnames") else null
	val random = Random(seed)
	return List(input.count.coerceIn(1, MAX_NAMES)) {
		val name = given.random(random).pick(random)
		if (surnames == null) name else "$name ${surnames.pick(random)}"
	}
}

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
