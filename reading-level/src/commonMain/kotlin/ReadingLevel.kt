// Reading level: a report of the level each scene reads at, and the whole story, as a document. Run
// from a project's or a scene's menu, through the `action` export. Each scene's level comes from its
// sentence and word length by its language's formula (see Languages.kt), and its rare words are those
// its language's list of common words lacks.
import hammer.ActionReply
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

/** [text] as it should read, with the characters that would format it, or end a table cell, escaped. */
private fun markdownText(text: String): String = buildString {
	for (c in text) {
		if (c in "\\*_`[]<>#|~=") append('\\')
		append(c)
	}
}

/** A table, its columns aligned by [align]: a character each, '<' left and '>' right. */
private fun StringBuilder.table(header: List<String>, align: String, rows: List<List<String>>) {
	append(header.joinToString(" | ", "| ", " |\n"))
	append(align.map { if (it == '>') "---:" else ":---" }.joinToString(" | ", "| ", " |\n"))
	for (row in rows) append(row.joinToString(" | ", "| ", " |\n"))
	append("\n")
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

private fun perSentence(counts: Counts) = if (counts.sentences > 0) tenths(counts.words.toDouble() / counts.sentences) else ""

private fun rarePercent(counts: Counts) = if (counts.measured > 0) percent(counts.rare, counts.measured) else ""

/** "below 2", "2 to 4", "12 and up" */
private fun grades(level: Level): String {
	val from = Level.entries.getOrNull(level.ordinal - 1)?.below?.toInt()
	return when {
		from == null -> "below ${level.below.toInt()}"
		level.below.isInfinite() -> "$from and up"
		else -> "$from to ${level.below.toInt()}"
	}
}

/** The level as a quote, then the counts as a table, for the whole story or a scene reported alone. */
private fun StringBuilder.summary(grade: Double?, steady: Boolean, counts: Counts, language: Language, lists: Lists) {
	if (grade != null) {
		append("> **${Level.of(grade).label}** · grade ${tenths(grade)}")
		language.score(counts)?.let { append(" · $it") }
		if (!steady) append(" · rough, from fewer than $STEADY_WORDS words")
		append("\n\n")
	} else if (language.basis != null) {
		append("> Too short for a level\n\n")
	}
	val header = mutableListOf("Words", "Sentences", "Words per sentence")
	val row = mutableListOf(thousands(counts.words), thousands(counts.sentences), perSentence(counts))
	if (lists.common != null) {
		header += "Rare words"
		row += rarePercent(counts)
	}
	header += "Dialogue"
	row += percent(counts.dialogueWords, counts.words)
	table(header, ">".repeat(header.size), listOf(row))
}

private fun median(values: List<Double>): Double {
	val sorted = values.sorted()
	val middle = sorted.size / 2
	return if (sorted.size % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle]) / 2
}

private fun StringBuilder.storySection(scenes: List<Scene>, total: Counts, language: Language, lists: Lists) {
	append("## Whole story\n\n")
	summary(if (total.hasProse) language.grade(total) else null, true, total, language, lists)

	val leveled = scenes.mapNotNull { it.grade }
	if (leveled.isNotEmpty()) {
		val byLevel = leveled.groupingBy { Level.of(it) }.eachCount()
		append("### Scenes by level\n\n")
		table(listOf("Level", "Grades", "Scenes"), "<<>", Level.entries.map { listOf(it.label, grades(it), "${byLevel[it] ?: 0}") })
	}

	val steady = scenes.filter { it.steady }
	if (steady.size >= 3) {
		val middle = median(steady.map { it.grade!! })
		val standOuts = steady.filter { abs(it.grade!! - middle) >= STAND_OUT_GRADES }
			.sortedByDescending { abs(it.grade!! - middle) }.take(STAND_OUTS_SHOWN)
		if (standOuts.isNotEmpty()) {
			append("### Stand out\n\n")
			append("Scenes ${STAND_OUT_GRADES.toInt()} grades or more from the story’s middle, grade ${tenths(middle)}.\n\n")
			table(listOf("Scene", "Level", "Grade", "From the middle"), "<<>>", standOuts.map {
				val off = it.grade!! - middle
				listOf(markdownText(it.name), Level.of(it.grade).label, tenths(it.grade), "${if (off > 0) "+" else ""}${tenths(off)}")
			})
		}
	}

	if (total.rareWords.isNotEmpty()) append("### Most used rare words\n\n${rareList(total.rareWords, STORY_RARE_WORDS)}\n\n")
}

private fun StringBuilder.scenesSection(scenes: List<Scene>, language: Language, lists: Lists) {
	append("## Scenes\n\n")
	val leveled = language.basis != null
	val columns = buildList {
		add("Scene" to '<')
		if (leveled) addAll(listOf("Level" to '<', "Grade" to '>'))
		addAll(listOf("Words" to '>', "Words per sentence" to '>'))
		if (lists.common != null) add("Rare words" to '>')
		add("Dialogue" to '>')
	}
	table(columns.map { it.first }, columns.map { it.second }.joinToString(""), scenes.map { scene ->
		val counts = scene.counts
		buildList {
			add(markdownText(scene.name))
			if (leveled) {
				add(scene.grade?.let { Level.of(it).label } ?: "*Too short*")
				add(scene.grade?.let { (if (scene.steady) "" else "≈") + tenths(it) } ?: "")
			}
			add(thousands(counts.words))
			add(perSentence(counts))
			if (lists.common != null) add(rarePercent(counts))
			add(percent(counts.dialogueWords, counts.words))
		}
	})
	if (scenes.any { it.grade != null && !it.steady }) append("≈ rough, from a scene of fewer than $STEADY_WORDS words\n\n")

	val withRare = scenes.filter { it.counts.rareWords.isNotEmpty() }
	if (withRare.isNotEmpty()) {
		append("### Rare words\n\n")
		table(listOf("Scene", "Most used first"), "<<", withRare.map { listOf(markdownText(it.name), rareList(it.counts.rareWords, SCENE_RARE_WORDS)) })
	}
}

/** How levels are worked out, and their grades when [showGrades]. */
private fun StringBuilder.about(language: Language, showGrades: Boolean) {
	append("---\n\n## About these levels\n\n")
	if (language.basis == null) {
		append(
			"Reading levels need an English, French, German, Spanish, Italian, or Ukrainian project; this one " +
				"gets its words, sentences, and dialogue.\n\n",
		)
		return
	}
	append(
		"Levels go by sentence and word length, as US school grades. They measure how the text reads, not what " +
			"it is about. ${language.basis}\n\n" +
			"Rare words are those outside the ${language.name} words most used in film and TV subtitles. Names " +
			"count toward a sentence’s length but not a word’s.\n\n",
	)
	if (showGrades) table(listOf("Level", "Grades"), "<<", Level.entries.map { listOf(it.label, grades(it)) })
}

fun report(request: ActionRequest): ActionReply {
	val project = request.project
	fun projectInput() = buildJsonObject { put("project", project) }
	val language = languageOf(call<Info>("project.info", projectInput()).language)
	val lists = listsFor(language)
	val names = nameWords(call<Entries>("entry.list", projectInput()).entries.flatMap { listOf(it.name) + it.aliases }, language, lists)
	val nameHash = namesHash(names)

	val here = request.context.id.takeIf { request.context.place == "scene" }
	val chosen = request.input<ReportInput>().scenes.ifEmpty { listOfNotNull(here) }
	val nodes = scenesOf(call<Tree>("scene.tree", projectInput()).nodes).filter { chosen.isEmpty() || it.id in chosen }
	if (nodes.isEmpty()) return ActionReply.document("# Reading level\n\nThere are no scenes to read.")

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

	val markdown = buildString {
		append("# Reading level\n\n")
		val story = scenes.size > 1
		if (story) {
			storySection(scenes, total, language, lists)
			scenesSection(scenes, language, lists)
		} else {
			val scene = scenes.single()
			append("## ${markdownText(scene.name)}\n\n")
			summary(scene.grade, scene.steady, scene.counts, language, lists)
			if (scene.counts.rareWords.isNotEmpty()) append("### Rare words\n\n${rareList(scene.counts.rareWords, SCENE_RARE_WORDS)}\n\n")
		}
		about(language, showGrades = !story || scenes.none { it.grade != null })
	}
	return ActionReply.document(markdown.trimEnd())
}
