// Real-world names: an action whose page lists names real people of a culture have, each with a button
// that adds it to the project's encyclopedia as a person. The page's state holds the list's seed and
// options, so pressing a name rebuilds the same list, marking the names already added.
//
// Each culture is a file, resources/names/<culture>.txt, of lists, each a line "[<list>]" and then a
// name and how many people have it on each line, a tab between them: feminine and masculine,
// optionally feminine-<decade> and masculine-<decade> for those born in a decade, such as
// feminine-1950, and optionally surnames, or surnames-feminine and surnames-masculine where they
// differ. A name is picked as often as it is common. A full name is "{given} {surname}" unless the
// format list's line says otherwise, such as "{surname} {given}". Adding a culture is a file and an
// option in the manifest's culture field.
import hammer.ActionReply
import hammer.ActionReply.Button
import hammer.ActionReply.Page
import hammer.ActionRequest
import hammer.OperationException
import hammer.input
import hammer.ops.EntryKind
import hammer.ops.Hammer
import hammer.resource
import hammer.state
import hammer.stateOf
import kotlin.random.Random
import kotlinx.serialization.Serializable
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
	private val totals: LongArray

	/** How many of the most common names half the people have between them. */
	private val common: Int

	init {
		val running = LongArray(lines.size)
		var total = 0L
		for (line in lines) {
			if (line.isBlank()) continue
			val count = line.substringAfter('\t', "").trim().toLongOrNull() ?: 0
			if (count <= 0 || line.startsWith('\t')) error("A name list has a name without a count")
			total += count
			running[names.size] = total
			names += line.substringBefore('\t')
		}
		totals = running.copyOf(names.size)
		common = totals.indexOfFirst { it * 2 >= total } + 1
	}

	fun isEmpty() = names.isEmpty()

	operator fun contains(name: String) = name in names

	/**
	 * A name, as often as it is common. For a `common` [rarity], only from the most common names that
	 * half the people have between them; for a `rare` one, any of the rest, each as often as another.
	 */
	fun pick(random: Random, rarity: String = "typical"): String {
		if (rarity == "rare" && common < names.size) return names[random.nextInt(common, names.size)]
		val at = random.nextLong(if (rarity == "common") totals[common - 1] else totals.last())
		// The first name whose running total passes [at]; the totals only grow.
		var low = 0
		var high = totals.size - 1
		while (low < high) {
			val mid = (low + high) ushr 1
			if (totals[mid] > at) high = mid else low = mid + 1
		}
		return names[low]
	}
}

/**
 * Where each list lies in a culture's file, each list a line `[<list>]` and then its lines. The
 * interpreter Hammer runs plugins in is slow at going through text, so the file is gone through once,
 * as bytes, to find its lists, and a list is decoded only when it is asked for.
 */
class CultureFile(bytes: ByteArray) {
	private val starts = HashMap<String, Int>()
	private val ends = HashMap<String, Int>()

	init {
		if (bytes.isNotEmpty() && bytes[0] != OPEN) error("A culture's file has lines before its first list")
		var list: String? = null
		var lineStart = 0
		for (i in 0..bytes.size) {
			if (i < bytes.size && bytes[i] != NEWLINE) continue
			if (i > lineStart && bytes[lineStart] == OPEN) {
				val end = if (bytes[i - 1] == RETURN) i - 1 else i
				if (end - lineStart > 1 && bytes[end - 1] == CLOSE) {
					list?.let { ends[it] = lineStart }
					list = bytes.decodeToString(lineStart + 1, end - 1)
					starts[list] = minOf(i + 1, bytes.size)
				}
			}
			lineStart = i + 1
		}
		list?.let { ends[it] = bytes.size }
	}

	operator fun contains(name: String) = name in starts

	/** The lines of the list [name] in the file's [bytes], or null when there is no such list. */
	fun list(bytes: ByteArray, name: String): List<String>? {
		val start = starts[name] ?: return null
		return bytes.decodeToString(start, ends.getValue(name)).lines().filter { it.isNotBlank() }
	}

	private companion object {
		const val OPEN = '['.code.toByte()
		const val CLOSE = ']'.code.toByte()
		const val NEWLINE = '\n'.code.toByte()
		const val RETURN = '\r'.code.toByte()
	}
}

/** A culture's file's lists, those built from it so far, and how it writes a full name. */
private class Culture(val file: CultureFile, val format: String) {
	val lists = HashMap<String, NameList?>()
}

// Hammer drops a plugin whose heap grows too large, and phones have little to spare, so only the
// cultures used last are kept, and a file's bytes only for the call that read them.
private const val KEPT_CULTURES = 3
private val cultures = HashMap<String, Culture>()
private val recent = ArrayList<String>()
private var read: Pair<String, ByteArray>? = null

private fun bytes(culture: String): ByteArray? =
	read?.takeIf { it.first == culture }?.second ?: resource("names/$culture.txt")?.also { read = culture to it }

private fun culture(culture: String): Culture? {
	val known = cultures[culture] ?: bytes(culture)?.let { bytes ->
		val file = CultureFile(bytes)
		Culture(file, file.list(bytes, "format")?.firstOrNull()?.trim() ?: "{given} {surname}")
	} ?: return null
	cultures[culture] = known
	recent.remove(culture)
	recent.add(0, culture)
	while (recent.size > KEPT_CULTURES) cultures.remove(recent.removeLast())
	return known
}

/** The culture's list [name], or null when it has none. */
fun nameList(culture: String, name: String): NameList? {
	val known = culture(culture) ?: return null
	if (name in known.lists) return known.lists[name]
	val list = if (name in known.file) known.file.list(bytes(culture)!!, name)?.let(::NameList)?.takeUnless { it.isEmpty() } else null
	return list.also { known.lists[name] = it }
}

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
	val lists = try {
		kinds.map { kind ->
			givenNames(culture, kind, input.era) to
				if (input.surnames) nameList(culture, "surnames-$kind") ?: nameList(culture, "surnames") else null
		}
	} finally {
		read = null
	}
	val format = culture(culture)!!.format
	val random = Random(seed)
	return List(input.count.coerceIn(1, MAX_NAMES)) {
		val (given, surnames) = lists.random(random)
		if (surnames == null) given.pick(random, input.rarity)
		else PART.replace(format) { (if (it.value == "{given}") given else surnames).pick(random, input.rarity) }
	}
}

private val PART = Regex("\\{given\\}|\\{surname\\}")

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
