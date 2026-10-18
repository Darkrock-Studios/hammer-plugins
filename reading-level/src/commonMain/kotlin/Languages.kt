// Each project language's counting rules and formulas. A level is a US school grade put in a band.
// English has Flesch–Kincaid's grade and German the Wiener Sachtextformel's. The others have
// Flesch–Kincaid with the weights their language's adaptation of Flesch's reading ease gives
// sentence and word length, which is how Oborneva made Russian's Flesch–Kincaid from her reading ease;
// Ukrainian, which has no adaptation of its own, uses hers.

/** A level, for grades below [below]. */
enum class Level(val label: String, val below: Double) {
	EarlyReader("Early reader", 2.0),
	ChapterBook("Chapter book", 4.0),
	MiddleGrade("Middle grade", 7.0),
	TeenAndUp("Teen and up", 12.0),
	Dense("Dense", Double.POSITIVE_INFINITY);

	companion object {
		fun of(grade: Double): Level = entries.first { grade < it.below }
	}
}

class Language(
	/** The primary subtag, which names its lists in resources/. */
	val code: String,
	val name: String,
	/** Its vowels, lower case, for counting syllables as vowel groups. */
	val vowels: String,
	/** Whether a final "e" after a consonant is silent, as in English "made" and French "table". */
	val silentE: Boolean = false,
	/** Whether an apostrophe ends a word, as French and Italian elide: "l'homme" counts "homme". */
	val splitApostrophes: Boolean = false,
	/** Whether a line opening with a dash is dialogue, each later dash moving between speech and narration. */
	val dashDialogue: Boolean = false,
	/** Whether every noun has a capital, as in German, so a capital does not mark a name. */
	val capitalNouns: Boolean = false,
	/** How its levels are worked out, for the report; null for a language without them. */
	val basis: String? = null,
	/** The text's US school grade, or null without a formula. */
	val grade: (Counts) -> Double? = { null },
	/** Its own formula's figure, shown beside the grade, when that is not the grade. */
	val score: (Counts) -> String? = { null },
)

private fun Counts.wordsPerSentence() = words.toDouble() / sentences

private fun Counts.syllablesPerWord() = syllables.toDouble() / measured

private fun Counts.percentOf(part: Long) = part * 100.0 / measured

/**
 * Flesch–Kincaid's grade, with sentence and word length weighted as a reading ease weighs them against
 * Flesch's own; at least 0, which the plainest text would go below.
 */
private fun fleschKincaid(sentenceWeight: Double, syllableWeight: Double): (Counts) -> Double = {
	(0.39 * sentenceWeight / 1.015 * it.wordsPerSentence() + 11.8 * syllableWeight / 84.6 * it.syllablesPerWord() - 15.59).coerceAtLeast(0.0)
}

private fun readingEase(start: Double, sentenceWeight: Double, syllableWeight: Double, formula: String): (Counts) -> String = {
	"reading ease ${tenths(start - sentenceWeight * it.wordsPerSentence() - syllableWeight * it.syllablesPerWord())} ($formula)"
}

/** The first Wiener Sachtextformel: a German school grade, from about 4 to 15, and at least 0. */
private fun wiener(counts: Counts): Double = (
	0.1935 * counts.percentOf(counts.polysyllables) + 0.1672 * counts.wordsPerSentence() +
		0.1297 * counts.percentOf(counts.longWords) - 0.0327 * counts.percentOf(counts.monosyllables) - 0.875
	).coerceAtLeast(0.0)

private fun gulpease(counts: Counts): Double = 89 + 300.0 * counts.sentences / counts.words - 10.0 * counts.letters / counts.measured

val LANGUAGES = listOf(
	Language(
		"en", "English", "aeiouy", silentE = true,
		basis = "English levels are Flesch–Kincaid grades.",
		grade = fleschKincaid(1.015, 84.6),
	),
	Language(
		"fr", "French", "aeiouyàâäéèêëîïôöùûüÿœæ", silentE = true, splitApostrophes = true, dashDialogue = true,
		basis = "French levels are Flesch–Kincaid grades with Kandel and Moles’s French weights; the reading ease is theirs.",
		grade = fleschKincaid(1.015, 73.6),
		score = readingEase(207.0, 1.015, 73.6, "Kandel–Moles"),
	),
	Language(
		"de", "German", "aeiouyäöü", capitalNouns = true,
		basis = "German levels are the Wiener Sachtextformel’s school grades.",
		grade = ::wiener,
	),
	Language(
		"es", "Spanish", "aeiouáéíóúü", dashDialogue = true,
		basis = "Spanish levels are Flesch–Kincaid grades with Szigriszt-Pazos’s Spanish weights; the reading ease is his.",
		grade = fleschKincaid(1.0, 62.3),
		score = readingEase(206.835, 1.0, 62.3, "Szigriszt-Pazos"),
	),
	Language(
		"it", "Italian", "aeiouàèéìíòóù", splitApostrophes = true, dashDialogue = true,
		basis = "Italian levels are Flesch–Kincaid grades with Franchina and Vacca’s Italian weights; the score is Gulpease.",
		grade = fleschKincaid(1.3, 60.0),
		score = { "Gulpease ${tenths(gulpease(it))}" },
	),
	Language(
		"uk", "Ukrainian", "аеєиіїоуюя", dashDialogue = true,
		basis = "Ukrainian levels are Flesch–Kincaid grades with Oborneva’s Russian weights, as Ukrainian has none of its own; so is the reading ease.",
		grade = fleschKincaid(1.3, 60.1),
		score = readingEase(206.835, 1.3, 60.1, "Oborneva"),
	),
)

/** A language without lists or formulas: counted as words, sentences, and quoted dialogue. */
val OTHER_LANGUAGE = Language("", "", "aeiouy")

/** The language of BCP 47 [tag]; English when Hammer does not say. */
fun languageOf(tag: String?): Language {
	val code = (tag ?: "en").substringBefore('-').substringBefore('_').lowercase()
	return LANGUAGES.firstOrNull { it.code == code } ?: OTHER_LANGUAGE
}
