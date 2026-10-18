// Reading level: a report of the level each scene reads at, and the whole story, as a document. Run
// from a project's or a scene's menu, through the `action` export. Each scene's level comes from its
// sentence and word length by its language's formula (see Languages.kt), and its rare words are those
// its language's list of common words lacks.
import hammer.ActionRequest
import hammer.call
import hammer.input
import hammer.progress
import kotlin.math.abs
import kotlin.math.floor
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Fewer words than this get no level. */
const val MIN_WORDS = 30

/** Fewer words than this get a level marked rough, and are left out of the story's comparisons. */
const val STEADY_WORDS = 150

/** How many grades from the story's median a scene is before it stands out. */
const val STAND_OUT_GRADES = 2.0

const val SCENE_RARE_WORDS = 8
const val STORY_RARE_WORDS = 15
const val STAND_OUTS_SHOWN = 10

@Serializable
class ReportInput(val scenes: List<Int> = emptyList())

@Serializable
private class Info(val language: String? = null)

@Serializable
private class Node(val id: Int, val name: String, val kind: String, val children: List<Node> = emptyList())

@Serializable
private class Tree(val nodes: List<Node>)

@Serializable
private class SceneText(val markdown: String)

@Serializable
private class Entries(val entries: List<Entry>) {
	@Serializable
	class Entry(val name: String, val aliases: List<String> = emptyList())
}

/** The story's scenes in order, groups opened up. */
private fun scenesOf(nodes: List<Node>): List<Node> = nodes.flatMap { if (it.kind == "scene") listOf(it) else scenesOf(it.children) }

// ---------------------------------------------------------------------------------------------
// Numbers

/** Java's Math.round: the nearest integer, halves up. */
private fun roundHalfUp(value: Double): Long {
	val floor = floor(value)
	return floor.toLong() + if (value - floor >= 0.5) 1 else 0
}

/** [value] to one decimal place. */
fun tenths(value: Double): String {
	val rounded = roundHalfUp(value * 10)
	val magnitude = abs(rounded)
	return "${if (rounded < 0) "-" else ""}${magnitude / 10}.${magnitude % 10}"
}

/** [value] with commas between thousands. */
fun thousands(value: Long): String {
	val digits = abs(value).toString()
	val grouped = digits.reversed().chunked(3).joinToString(",").reversed()
	return if (value < 0) "-$grouped" else grouped
}

private fun percent(part: Long, whole: Long): String = "${if (whole > 0) roundHalfUp(part * 100.0 / whole) else 0}%"

private fun plural(count: Long, one: String, many: String) = "${thousands(count)} ${if (count == 1L) one else many}"

/** [text] as it should read, with the characters that would format it escaped. */
private fun markdownText(text: String): String = buildString {
	for (c in text) {
		if (c in "\\*_`[]<>#") append('\\')
		append(c)
	}
}

// ---------------------------------------------------------------------------------------------
// The report

/** A scene's name and counts, and its grade when it has enough words for one. */
private class Scene(val name: String, val counts: Counts, language: Language) {
	val grade: Double? = if (counts.words >= MIN_WORDS && counts.hasProse) language.grade(counts) else null
	val steady get() = grade != null && counts.words >= STEADY_WORDS
}

private fun rareList(words: Map<String, Int>, size: Int): String =
	words.entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
		.take(size).joinToString(", ") { if (it.value > 1) "${markdownText(it.key)} (${it.value})" else markdownText(it.key) }

/** "**Middle grade**: grade 5.2 (Flesch–Kincaid)" */
private fun levelText(grade: Double, counts: Counts, language: Language): String =
	"**${Level.of(grade).label}**" + (language.score(counts)?.let { ": $it" } ?: "")

private fun StringBuilder.sceneSection(scene: Scene, language: Language, lists: Lists) {
	val counts = scene.counts
	append("### ${markdownText(scene.name)}\n\n")
	when {
		scene.grade != null -> {
			append(levelText(scene.grade, counts, language))
			append(if (scene.steady) ". " else ", roughly, from a short scene. ")
		}
		language.basis != null -> append("Too short for a level. ")
	}
	append(plural(counts.words, "word", "words"))
	if (counts.sentences > 0) append(", ${tenths(counts.words.toDouble() / counts.sentences)} per sentence")
	append(".")
	if (lists.common != null && counts.measured > 0) append(" ${percent(counts.rare, counts.measured)} rare words.")
	append(" ${percent(counts.dialogueWords, counts.words)} dialogue.")
	if (counts.rareWords.isNotEmpty()) append("\n\nRare words: ${rareList(counts.rareWords, SCENE_RARE_WORDS)}")
	append("\n\n")
}

private fun median(values: List<Double>): Double {
	val sorted = values.sorted()
	val middle = sorted.size / 2
	return if (sorted.size % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle]) / 2
}

private fun StringBuilder.storySection(scenes: List<Scene>, total: Counts, language: Language, lists: Lists) {
	append("## Whole story\n\n")
	val grade = if (total.hasProse) language.grade(total) else null
	if (grade != null) append("${levelText(grade, total, language)}\n\n")
	append("- **Words:** ${thousands(total.words)} in ${plural(total.sentences, "sentence", "sentences")}")
	if (total.sentences > 0) append(", ${tenths(total.words.toDouble() / total.sentences)} per sentence")
	append("\n")
	if (lists.common != null && total.measured > 0) {
		append("- **Rare words:** ${percent(total.rare, total.measured)}")
		if (total.rareWords.isNotEmpty()) append("; the most used: ${rareList(total.rareWords, STORY_RARE_WORDS)}")
		append("\n")
	}
	append("- **Dialogue:** ${percent(total.dialogueWords, total.words)}\n")

	val leveled = scenes.mapNotNull { it.grade }
	if (leveled.isNotEmpty()) {
		val byLevel = leveled.groupingBy { Level.of(it) }.eachCount()
		val spread = Level.entries.filter { it in byLevel }.joinToString(", ") { "${byLevel.getValue(it)} ${it.label.lowercase()}" }
		append("- **Scenes by level:** $spread\n")
	}

	val steady = scenes.filter { it.steady }
	if (steady.size >= 3) {
		val middle = median(steady.map { it.grade!! })
		val standOuts = steady.filter { abs(it.grade!! - middle) >= STAND_OUT_GRADES }
			.sortedByDescending { abs(it.grade!! - middle) }.take(STAND_OUTS_SHOWN)
		if (standOuts.isNotEmpty()) {
			append("- **Stand out** from the story’s middle, grade ${tenths(middle)}:\n")
			for (scene in standOuts) {
				val way = if (scene.grade!! > middle) "above" else "below"
				append("  - *${markdownText(scene.name)}*, ${Level.of(scene.grade).label.lowercase()} at grade ${tenths(scene.grade)}, $way\n")
			}
		}
	}
	append("\n")
}

private fun StringBuilder.note(language: Language) {
	if (language.basis == null) {
		append(
			"Reading levels need an English, French, German, Spanish, Italian, or Ukrainian project; this one " +
				"gets its words, sentences, and dialogue.\n\n",
		)
		return
	}
	append(
		"Levels go by sentence and word length, as US school grades: ${Level.EarlyReader.label.lowercase()} below " +
			"2, ${Level.ChapterBook.label.lowercase()} 2 to 4, ${Level.MiddleGrade.label.lowercase()} 4 to 7, " +
			"${Level.TeenAndUp.label.lowercase()} 7 to 12, and ${Level.Dense.label.lowercase()} from 12. They measure " +
			"how the text reads, not what it is about. ${language.basis} Rare words are those outside the " +
			"${language.name} words most used in film and TV subtitles. Names count toward a sentence’s length but " +
			"not a word’s.\n\n",
	)
}

fun report(request: ActionRequest): String {
	val project = request.project
	fun projectInput() = buildJsonObject { put("project", project) }
	val language = languageOf(call<Info>("project.info", projectInput()).language)
	val lists = listsFor(language)
	val names = nameWords(call<Entries>("entry.list", projectInput()).entries.flatMap { listOf(it.name) + it.aliases }, language, lists)
	val nameHash = namesHash(names)

	val here = request.context.id.takeIf { request.context.place == "scene" }
	val chosen = request.input<ReportInput>().scenes.ifEmpty { listOfNotNull(here) }
	val nodes = scenesOf(call<Tree>("scene.tree", projectInput()).nodes).filter { chosen.isEmpty() || it.id in chosen }
	if (nodes.isEmpty()) return "# Reading level\n\nThere are no scenes to read."

	val total = Counts()
	val scenes = nodes.mapIndexed { i, node ->
		progress(i.toDouble() / nodes.size, "Reading ${node.name}")
		val markdown = call<SceneText>("scene.read", buildJsonObject {
			put("project", project)
			put("id", node.id)
		}).markdown
		val key = cacheKey(markdown, language, nameHash)
		val counts = loadCounts(key) ?: countScene(markdown, language, lists, names).also { saveCounts(key, it) }
		total.add(counts)
		Scene(node.name, counts, language)
	}

	return buildString {
		append("# Reading level\n\n")
		if (scenes.size > 1) storySection(scenes, total, language, lists)
		note(language)
		if (scenes.size > 1) append("## Scenes\n\n")
		for (scene in scenes) sceneSection(scene, language, lists)
	}.trimEnd()
}
