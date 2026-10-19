// Counting a scene: its words and sentences, and the length and rarity of its words, by its
// language's rules. Names count toward a sentence's length but not toward word length or rarity: a
// reader meets "Aelindrathor" once and knows it after. A name is a word of an encyclopedia entry's name
// or aliases, or, outside German, a word the language's list lacks that a scene writes with a capital
// somewhere other than the start of a sentence or a quotation. Kotlin/Wasm's Regex is slow in Hammer, so text is read by hand.
import hammer.cacheGet
import hammer.cacheSet
import hammer.resourceText

private const val MAX_CLOSERS = 8
private const val LONG_WORD = 6

/** What a scene, or the story, adds up to. */
class Counts {
	/** Every word, for sentence length. */
	var words = 0L
	var sentences = 0L
	/** Words with letters that are not names, for word length and rarity. */
	var measured = 0L
	var syllables = 0L
	var letters = 0L
	var monosyllables = 0L
	/** Words of three syllables or more. */
	var polysyllables = 0L
	/** Words of more than six letters. */
	var longWords = 0L
	var rare = 0L
	var dialogueWords = 0L
	val rareWords = HashMap<String, Int>()

	/** Whether there is enough to work out a level from. */
	val hasProse get() = sentences > 0 && measured > 0

	fun add(other: Counts) {
		words += other.words
		sentences += other.sentences
		measured += other.measured
		syllables += other.syllables
		letters += other.letters
		monosyllables += other.monosyllables
		polysyllables += other.polysyllables
		longWords += other.longWords
		rare += other.rare
		dialogueWords += other.dialogueWords
		for ((word, count) in other.rareWords) rareWords.addCount(word, count)
	}
}

private fun HashMap<String, Int>.addCount(key: String, count: Int = 1) {
	this[key] = (this[key] ?: 0) + count
}

/** A language's lists: its common words, null without one, and the titles a full stop does not end a sentence after. */
class Lists(val common: Set<String>?, val titles: Set<String>)

private fun listFile(name: String): Set<String>? = resourceText(name)?.lineSequence()
	?.map { it.trim() }?.filter { it.isNotEmpty() && !it.startsWith('#') }?.toHashSet()

fun listsFor(language: Language): Lists =
	if (language.code.isEmpty()) Lists(null, emptySet())
	else Lists(listFile("words/${language.code}.txt"), listFile("titles/${language.code}.txt").orEmpty())

/** Whether [word], lower case, is common: by its longest part between apostrophes, as the lists split "don't". */
fun isCommon(word: String, common: Set<String>): Boolean {
	if (word.indexOf('\'') < 0 && word.indexOf('’') < 0) return word in common
	return word.split('\'', '’').maxBy { it.length } in common
}

// ---------------------------------------------------------------------------------------------
// Characters

private fun isLetter(c: Char): Boolean = if (c < '\u0080') (c in 'a'..'z' || c in 'A'..'Z') else c.isLetter()

private fun isWordChar(c: Char): Boolean = isLetter(c) || c in '0'..'9' || (c >= '\u0080' && c.isDigit())

/** Java's \s: ASCII whitespace only. */
private fun isSpace(c: Char): Boolean = c == ' ' || c == '\t' || c == '\n' || c == '\u000B' || c == '\u000C' || c == '\r'

private fun isCloser(c: Char): Boolean = c == '"' || c == '”' || c == '’' || c == ')' || c == '“' || c == '»' || c == '«'

private fun isSentenceEnd(c: Char): Boolean = c == '.' || c == '!' || c == '?' || c == '…'

private fun isEmphasis(c: Char): Boolean = c == '*' || c == '_' || c == '~' || c == '`'

// Most words are already lower-case ASCII, and lowercase() would copy them anyway.
private fun lowerCase(word: String): String = if (word.all { it in 'a'..'z' || it == '\'' }) word else word.lowercase()

// ---------------------------------------------------------------------------------------------
// Reading the text

/** A word as written: [text] in lower case, and whether it had a capital, opened its sentence or quotation, and was spoken. */
private class Word(val text: String, val capital: Boolean, val first: Boolean, val dialogue: Boolean)

private class Scan {
	val words = ArrayList<Word>()
	var sentences = 0L
}

/** The run of word characters ending at [end] is a title such as "Mr", so the full stop after it does not end the sentence. */
private fun endsWithTitle(text: String, start: Int, end: Int, titles: Set<String>): Boolean {
	var at = end
	while (at > start && isWordChar(text[at - 1])) at--
	return at < end && text.substring(at, end).lowercase() in titles
}

/** Whether the whitespace at [space] ends a sentence: it follows a sentence end and up to eight closing marks, and no title. */
private fun endsSentence(text: String, sentenceStart: Int, space: Int, titles: Set<String>): Boolean {
	var at = space
	var closers = 0
	while (at > sentenceStart) {
		val c = text[at - 1]
		if (isSentenceEnd(c)) return !(c == '.' && endsWithTitle(text, sentenceStart, at - 1, titles))
		if (!isCloser(c) || ++closers > MAX_CLOSERS) return false
		at--
	}
	return false
}

private enum class Quote { None, Double, CurlyDouble, CurlySingle, Low, LeftAngle, RightAngle, Dash }

/** One paragraph's words and sentences; its end ends its last sentence. */
private fun scanParagraph(text: String, language: Language, titles: Set<String>, scan: Scan) {
	var opened = Quote.None
	// Whether the paragraph has had anything but spaces, and whether it opened with a dialogue dash.
	var started = false
	var dashLine = false
	// Whether a quotation has opened since the last word, so the next word starts one.
	var quotationOpened = false
	var sentenceStart = 0
	var sentenceWords = 0
	var at = 0
	while (true) {
		val here = at
		if (here >= text.length || (isSpace(text[here]) && endsSentence(text, sentenceStart, here, titles))) {
			if (sentenceWords > 0) scan.sentences++
			sentenceWords = 0
			if (here >= text.length) return
			at++
			while (at < text.length && isSpace(text[at])) at++
			sentenceStart = at
			continue
		}
		val c = text[at++]
		if (isWordChar(c)) {
			while (at < text.length) {
				val d = text[at]
				val apostrophe = (d == '\'' || d == '’') && !language.splitApostrophes && at + 1 < text.length && isWordChar(text[at + 1])
				if (!isWordChar(d) && !apostrophe) break
				at++
			}
			scan.words += Word(lowerCase(text.substring(here, at)), c.isUpperCase(), sentenceWords == 0 || quotationOpened, opened != Quote.None)
			sentenceWords++
			started = true
			quotationOpened = false
			continue
		}
		val before = opened
		if ((c == '—' || c == '–') && language.dashDialogue && (dashLine || !started)) {
			dashLine = true
			opened = if (opened == Quote.Dash) Quote.None else if (opened == Quote.None) Quote.Dash else opened
		}
		if (!isSpace(c)) started = true
		opened = when {
			// „ opens a German quotation that “ closes; guillemets face either way, « » and » «.
			c == '„' -> if (opened == Quote.None) Quote.Low else opened
			c == '“' && opened == Quote.Low -> Quote.None
			c == '«' -> if (opened == Quote.RightAngle) Quote.None else if (opened == Quote.None) Quote.LeftAngle else opened
			c == '»' -> if (opened == Quote.LeftAngle) Quote.None else if (opened == Quote.None) Quote.RightAngle else opened
			c == '“' -> Quote.CurlyDouble
			c == '‘' -> Quote.CurlySingle
			c == '”' -> if (opened == Quote.CurlyDouble) Quote.None else opened
			// A lone ’ is also a plural possessive, so it closes only a ‘ quotation.
			c == '’' && !(at < text.length && isLetter(text[at])) -> if (opened == Quote.CurlySingle) Quote.None else opened
			c == '"' -> if (opened == Quote.Double) Quote.None else if (opened == Quote.None) Quote.Double else opened
			else -> opened
		}
		if (before == Quote.None && opened != Quote.None) quotationOpened = true
	}
}

private fun isHeading(line: String): Boolean {
	val rest = line.trimStart { isSpace(it) }
	val hashes = rest.takeWhile { it == '#' }.length
	return hashes in 1..6 && hashes < rest.length && isSpace(rest[hashes])
}

/** Where a line's prose starts: past a leading list or quote marker, if it has one. */
private fun proseStart(text: String): Int {
	fun spaces(from: Int): Int {
		var at = from
		while (at < text.length && isSpace(text[at])) at++
		return at
	}
	val at = spaces(0)
	if (at >= text.length) return 0
	val after = at + 1
	val c = text[at]
	return when {
		(c == '-' || c == '*' || c == '+') && after < text.length && isSpace(text[after]) -> spaces(after)
		c == '>' -> spaces(after)
		c in '0'..'9' -> {
			var end = after
			while (end < text.length && text[end] in '0'..'9') end++
			if (end + 1 < text.length && text[end] == '.' && isSpace(text[end + 1])) spaces(end + 1) else 0
		}
		else -> 0
	}
}

/** The prose of a line: without a leading list or quote marker, with links as their text, and without emphasis marks. */
private fun stripMarkdown(text: String): String = buildString(text.length) {
	var at = proseStart(text)
	while (at < text.length) {
		if (text[at] == '[') {
			val close = text.indexOf(']', at + 1)
			if (close >= 0 && close + 1 < text.length && text[close + 1] == '(') {
				val end = text.indexOf(')', close + 2)
				if (end >= 0) {
					for (i in at + 1 until close) if (!isEmphasis(text[i])) append(text[i])
					at = end + 1
					continue
				}
			}
		}
		if (!isEmphasis(text[at])) append(text[at])
		at++
	}
}

// ---------------------------------------------------------------------------------------------
// Counting

/** Vowel groups, less a silent final e; 0 for a word without letters, and at least 1 for one with. */
fun syllables(word: String, language: Language): Int {
	var letters = 0
	var count = 0
	var inVowels = false
	var last = ' '
	var beforeLast = ' '
	var lastVowel = false
	var beforeLastVowel = false
	for (c in word) {
		if (!isLetter(c)) continue
		letters++
		beforeLast = last
		last = c
		val vowel = language.vowels.indexOf(c) >= 0
		beforeLastVowel = lastVowel
		lastVowel = vowel
		if (vowel && !inVowels) count++
		inVowels = vowel
	}
	if (letters == 0) return 0
	// English says the e of "able" and "little".
	val sounded = language.code == "en" && beforeLast == 'l'
	if (language.silentE && letters > 2 && last == 'e' && !sounded && !beforeLastVowel) count--
	return maxOf(count, 1)
}

/** The lower-case words of encyclopedia [names] and aliases that are not common, which count as names. */
fun nameWords(names: List<String>, language: Language, lists: Lists): Set<String> {
	// Read as prose is, so a name splits into words as it does in a scene.
	val scan = Scan()
	for (name in names) scanParagraph(name, language, emptySet(), scan)
	return scan.words.mapNotNullTo(HashSet()) { word ->
		word.text.takeIf { it.any(::isLetter) && (lists.common == null || !isCommon(it, lists.common)) }
	}
}

/** Whether [line] starts a paragraph of its own: a list item, a quotation, or, where dashes mark dialogue, a line of it. */
private fun startsParagraph(line: String, language: Language): Boolean {
	if (proseStart(line) > 0) return true
	val first = line.firstOrNull { !isSpace(it) }
	return language.dashDialogue && (first == '—' || first == '–')
}

/** [markdown]'s counts, with the words of [names] counted as names. */
fun countScene(markdown: String, language: Language, lists: Lists, names: Set<String>): Counts {
	val scan = Scan()
	// Lines run on into one paragraph until a blank line or a heading, as markdown reads them.
	val paragraph = StringBuilder()
	fun endParagraph() {
		if (paragraph.isNotEmpty()) scanParagraph(paragraph.toString(), language, lists.titles, scan)
		paragraph.clear()
	}
	for (line in markdown.lines()) {
		if (line.isBlank() || isHeading(line)) {
			endParagraph()
			continue
		}
		if (startsParagraph(line, language)) endParagraph()
		if (paragraph.isNotEmpty()) paragraph.append(' ')
		paragraph.append(stripMarkdown(line))
	}
	endParagraph()
	val common = lists.common
	val capitalized = if (common == null || language.capitalNouns) emptySet()
	else scan.words.filter { it.capital && !it.first && !isCommon(it.text, common) }.mapTo(HashSet()) { it.text }

	val counts = Counts()
	counts.sentences = scan.sentences
	for (word in scan.words) {
		counts.words++
		if (word.dialogue) counts.dialogueWords++
		if (word.text in names || word.text in capitalized) continue
		val syllables = syllables(word.text, language)
		if (syllables == 0) continue
		val letters = word.text.count(::isLetter)
		counts.measured++
		counts.syllables += syllables
		counts.letters += letters
		if (syllables == 1) counts.monosyllables++
		if (syllables >= 3) counts.polysyllables++
		if (letters > LONG_WORD) counts.longWords++
		if (common != null && word.text.all(::isLetter) && !isCommon(word.text, common)) {
			counts.rare++
			counts.rareWords.addCount(word.text)
		}
	}
	return counts
}

// ---------------------------------------------------------------------------------------------
// Cached counts: a scene's counts are kept under a hash of its text, its language, and the names it
// was counted with, so unchanged scenes are not counted again. The value is text: a line of the
// counts, then a line per rare word with its count.

private fun hash(text: String, from: Long = -3750763034362895579L /* FNV-1a's offset basis, as a signed Long */): Long {
	var hash = from
	for (c in text) hash = (hash xor c.code.toLong()) * 1099511628211L
	return hash
}

/** The hash of [names], for [cacheKey]. */
fun namesHash(names: Set<String>): Long = hash(names.sorted().joinToString("\n"))

fun cacheKey(markdown: String, language: Language, namesHash: Long): String =
	"counts/2/${language.code}/${hash(markdown, namesHash).toULong().toString(16).padStart(16, '0')}/${markdown.length}"

fun saveCounts(key: String, counts: Counts) {
	val value = buildString {
		append(
			listOf(
				counts.words, counts.sentences, counts.measured, counts.syllables, counts.letters, counts.monosyllables,
				counts.polysyllables, counts.longWords, counts.rare, counts.dialogueWords,
			).joinToString(" "),
		)
		for ((word, count) in counts.rareWords) append("\n$count $word")
	}
	cacheSet(key, value.encodeToByteArray())
}

/** The counts cached under [key]; null on a miss or a value it cannot read. */
fun loadCounts(key: String): Counts? {
	val lines = cacheGet(key)?.decodeToString()?.split('\n') ?: return null
	val numbers = lines.first().split(' ').map { it.toLongOrNull()?.takeIf { n -> n >= 0 } ?: return null }
	if (numbers.size != 10) return null
	val counts = Counts()
	counts.words = numbers[0]
	counts.sentences = numbers[1]
	counts.measured = numbers[2]
	counts.syllables = numbers[3]
	counts.letters = numbers[4]
	counts.monosyllables = numbers[5]
	counts.polysyllables = numbers[6]
	counts.longWords = numbers[7]
	counts.rare = numbers[8]
	counts.dialogueWords = numbers[9]
	for (line in lines.drop(1)) {
		val count = line.substringBefore(' ').toIntOrNull()?.takeIf { it > 0 } ?: return null
		val word = line.substringAfter(' ', "").ifEmpty { return null }
		counts.rareWords[word] = count
	}
	return counts
}
