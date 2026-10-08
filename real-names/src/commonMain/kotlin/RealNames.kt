// Real-world names: an interactive action that lists names real people of a culture have, each with a
// button that adds it to the project's encyclopedia as a person. The list comes from a seed carried in
// the buttons' ids, so pressing one rebuilds the same list, marking the names already added.
//
// Each culture is a directory, resources/names/<culture>/, of lists with a name and how many people
// have it on each line, a tab between them: feminine.txt and masculine.txt, optionally
// feminine-<decade>.txt and masculine-<decade>.txt for those born in a decade, such as
// feminine-1950.txt, and optionally surnames.txt, or surnames-feminine.txt and surnames-masculine.txt
// where they differ. A name is picked as often as it is common. A full name is "{given} {surname}"
// unless format.txt says otherwise, such as "{surname} {given}". Adding a
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
	/** The decade of birth, such as `1950`; `any`, or one far from any the culture has lists for, gives names of all years. */
	val era: String = "any",
	val gender: String = "any",
	/** `common`, `typical`, or `rare`: see [NameList.pick]. */
	val rarity: String = "typical",
	val count: Int = 8,
	val surnames: Boolean = true,
)

/** Names from [lines], each `name<tab>count`, the most common first. */
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

	/**
	 * A name, as often as it is common. For a `common` [rarity], only from the most common names that
	 * half the people have between them; for a `rare` one, any of the rest, each as often as another.
	 */
	fun pick(random: Random, rarity: String = "typical"): String {
		val common = totals.indexOfFirst { it * 2 >= totals.last() } + 1
		if (rarity == "rare" && common < names.size) return names[random.nextInt(common, names.size)]
		val at = random.nextLong(if (rarity == "common") totals[common - 1] else totals.last())
		return names[totals.indexOfFirst { it > at }]
	}
}

/** The culture's list in `<file>.txt`, or null when it has none. */
fun nameList(culture: String, file: String): NameList? =
	resourceText("names/$culture/$file.txt")?.let { NameList(it.lines()) }?.takeUnless { it.isEmpty() }

/** The culture's given names of a kind for the decade nearest [era] that has a list, within 30 years, or for all years. */
private fun givenNames(culture: String, kind: String, era: String): NameList {
	val decades = era.toIntOrNull()?.let { decade -> listOf(0, -10, 10, -20, 20, -30, 30).map { decade + it } }.orEmpty()
	return decades.firstNotNullOfOrNull { nameList(culture, "$kind-$it") }
		?: nameList(culture, kind)
		?: error("There are no names for that culture")
}

/** The names [seed] gives for [input]. */
fun generate(seed: Long, input: NamesInput): List<String> {
	val culture = input.culture
	val kinds = listOf("feminine", "masculine").filter { input.gender == "any" || input.gender == it }.ifEmpty { listOf("feminine", "masculine") }
	// Each kind's given names with its surnames, which a culture may have a list of for each.
	val lists = kinds.map { kind ->
		givenNames(culture, kind, input.era) to
			if (input.surnames) nameList(culture, "surnames-$kind") ?: nameList(culture, "surnames") else null
	}
	val format = resourceText("names/$culture/format.txt")?.trim() ?: "{given} {surname}"
	val random = Random(seed)
	return List(input.count.coerceIn(1, MAX_NAMES)) {
		val (given, surnames) = lists.random(random)
		if (surnames == null) given.pick(random, input.rarity)
		else PART.replace(format) { (if (it.value == "{given}") given else surnames).pick(random, input.rarity) }
	}
}

private val PART = Regex("\\{given\\}|\\{surname\\}")

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
