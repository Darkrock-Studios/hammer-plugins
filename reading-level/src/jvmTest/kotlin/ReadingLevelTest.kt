import hammer.ActionDriver
import hammer.ActionRequest
import hammer.FakeHammerHost
import hammer.host
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

private val SIMPLE = "The cat sat on the mat. The dog ran to the cat. It was a good day. We had fun in the sun. " +
	"Mom made a cake. We ate it all. The cat sat with us. Then we went home. "

private val DENSE = "Notwithstanding the considerable administrative complications inherent in such undertakings, " +
	"the commission deliberately postponed its evaluation of the institutional consequences, preferring instead " +
	"to accumulate additional documentation regarding the organization's contemporaneous obligations. "

class ReadingLevelTest {
	private val english = languageOf("en-US")
	private val englishLists = listsFor(english)
	private var scenes = mapOf<Int, String>()
	private var language = "en"
	private var entries = listOf<String>()
	private val fake = FakeHammerHost(
		mapOf(
			"project.info" to { _ -> buildJsonObject { put("language", language) } },
			"entry.list" to { _ ->
				buildJsonObject {
					putJsonArray("entries") { entries.forEach { add(buildJsonObject { put("name", it) }) } }
				}
			},
			"scene.tree" to { _ ->
				buildJsonObject {
					putJsonArray("nodes") {
						add(buildJsonObject {
							put("id", 100)
							put("name", "Part one")
							put("kind", "group")
							put("children", JsonArray(scenes.keys.map { id ->
								buildJsonObject {
									put("id", id)
									put("name", "Scene $id")
									put("kind", "scene")
								}
							}))
						})
					}
				}
			},
			"scene.read" to { input -> buildJsonObject { put("markdown", scenes.getValue(input["id"]!!.jsonPrimitive.int)) } },
		),
	)

	init {
		host = fake
	}

	private fun count(text: String, language: Language = english, names: Set<String> = emptySet()) =
		countScene(text, language, listsFor(language), names)

	/** The report's markdown, from a page with nothing else on it. */
	private fun report(place: String = "project", id: Int? = null, chosen: List<Int>? = null): String {
		val driver = ActionDriver("report", ::report, "Storm", context = ActionRequest.Context(place, id)).open()
		chosen?.let { driver.fill("scenes", it) }
		val page = driver.press("run").page!!
		assertTrue(page.fields.isEmpty() && page.buttons.isEmpty() && page.state == null && driver.messages.isEmpty())
		return page.markdown!!
	}

	@Test
	fun `syllables are vowel groups less a silent e`() {
		assertEquals(1, syllables("made", english))
		assertEquals(2, syllables("able", english))
		assertEquals(2, syllables("little", english))
		assertEquals(5, syllables("considerable", english))
		assertEquals(1, syllables("table", languageOf("fr")))
		assertEquals(3, syllables("tavolo", languageOf("it")))
		assertEquals(3, syllables("молоко", languageOf("uk")))
		assertEquals(0, syllables("1984", english))
	}

	@Test
	fun `sentences end at a stop, but not after a title`() {
		val counts = count("Mr. Smith ran home. He fell! Did he? \"Yes.\" Then nothing")
		assertEquals(5, counts.sentences)
		assertEquals(11, counts.words)
	}

	@Test
	fun `headings, list marks, and emphasis are not prose`() {
		val counts = count("# Chapter One\n\n- *Run*, she said.\n\n[The end](https://example.com).")
		assertEquals(5, counts.words)
		assertEquals(2, counts.sentences)
	}

	@Test
	fun `rare words are those the list lacks, and contractions are common`() {
		val counts = count("The susurrus of the leaves didn't stop. The susurrus went on.")
		assertEquals(mapOf("susurrus" to 2), counts.rareWords)
		assertEquals(2, counts.rare)
		assertTrue(isCommon("don't", englishLists.common!!))
	}

	@Test
	fun `names count toward sentence length but not word length or rarity`() {
		val names = nameWords(listOf("Aelindrathor the Red", "Castle Vharnoth"), english, englishLists)
		assertEquals(setOf("aelindrathor", "vharnoth"), names)
		val counts = count("Aelindrathor rode to Vharnoth.", names = names)
		assertEquals(4, counts.words)
		assertEquals(2, counts.measured)
		assertEquals(0, counts.rare)
	}

	@Test
	fun `a name splits into words as the prose does`() {
		val names = nameWords(listOf("O'Brien"), english, englishLists)
		assertEquals(setOf("o'brien"), names)
		assertEquals(1, count("O'Brien left.", names = names).measured)
		// French splits at the apostrophe, in names as in prose.
		assertEquals(setOf("vharnoth"), nameWords(listOf("D'Vharnoth"), languageOf("fr"), listsFor(languageOf("fr"))))
	}

	@Test
	fun `lines run on into one paragraph until a blank line, a heading, or a list item`() {
		val wrapped = count("The cat sat\non the mat. It ran\naway.")
		assertEquals(2, wrapped.sentences)
		assertEquals(9, wrapped.words)
		assertEquals(3, count("One\n\nTwo\n# Heading\nThree").sentences)
		assertEquals(2, count("- one\n- two").sentences)
		assertEquals(2, count("\u2014Ven \u2014dijo\n\u2014Ya", languageOf("es")).sentences)
		assertEquals(2, count("A b c.\r\nD e f.").sentences)
	}

	@Test
	fun `a capitalized word opening a quotation is not taken for a name`() {
		assertEquals(mapOf("susurrus" to 1), count("She said, \u201CSusurrus is a word.\u201D").rareWords)
		assertEquals(mapOf("susurrus" to 1), count("She said, \"Susurrus is a word.\"").rareWords)
		assertEquals(0, count("Then Qyburn spoke, \u201CQyburn knows.\u201D").rare)
	}

	@Test
	fun `a capitalized word the list lacks is a name, but not in German`() {
		val counts = count("Then Qyburn spoke. Qyburn smiled.")
		assertEquals(5, counts.words)
		assertEquals(3, counts.measured)
		assertEquals(0, counts.rare)

		val german = count("Dann sprach der Zauberlehrling.", languageOf("de"))
		assertEquals(4, german.measured)
	}

	@Test
	fun `dialogue follows each language's quotation marks`() {
		assertEquals(2, count("“Run now,” she said.").dialogueWords)
		assertEquals(2, count("„Komm her“, sagte er.", languageOf("de")).dialogueWords)
		assertEquals(3, count("—Ven aquí —dijo ella—. Ya.", languageOf("es")).dialogueWords)
		assertEquals(2, count("« Viens ici », dit-elle.", languageOf("fr")).dialogueWords)
	}

	@Test
	fun `French and Italian split words at an apostrophe`() {
		val counts = count("L'homme arrive.", languageOf("fr"))
		assertEquals(3, counts.words)
	}

	@Test
	fun `levels follow the grade`() {
		assertEquals(Level.EarlyReader, Level.of(1.2))
		assertEquals(Level.ChapterBook, Level.of(2.0))
		assertEquals(Level.MiddleGrade, Level.of(6.9))
		assertEquals(Level.TeenAndUp, Level.of(7.0))
		assertEquals(Level.Dense, Level.of(15.0))
		assertEquals(Level.EarlyReader, Level.of(english.grade(count(SIMPLE))!!))
		assertEquals(Level.Dense, Level.of(english.grade(count(DENSE))!!))
	}

	@Test
	fun `Ukrainian's grade is Oborneva's Russian Flesch-Kincaid`() {
		val counts = Counts().apply {
			words = 120
			sentences = 10
			measured = 120
			syllables = 300
		}
		val oborneva = 0.5 * 12 + 8.4 * 2.5 - 15.59
		assertTrue(abs(languageOf("uk").grade(counts)!! - oborneva) < 0.05)
	}

	@Test
	fun `every language with lists has a grade, and others have none`() {
		for (language in LANGUAGES) {
			assertNotNull(listsFor(language).common, language.code)
			assertNotNull(language.grade(count(SIMPLE + DENSE, language)), language.code)
		}
		assertNull(languageOf("pt-BR").grade(count(SIMPLE)))
	}

	@Test
	fun `the report gives each scene a level and names the ones that stand out`() {
		val steady = SIMPLE.repeat(6) + "Ma grew a few odd plants in the garden. "
		scenes = mapOf(1 to steady, 2 to steady, 3 to steady, 4 to DENSE.repeat(5), 5 to "Too short.")
		val text = report()
		assertTrue(text.startsWith("# Reading level\n\n## Whole story"), text)
		assertTrue("| Early reader | below 2 | 3 |\n" in text, text)
		assertTrue("| Middle grade | 4 to 7 | 0 |\n" in text, text)
		assertTrue("| Dense | 12 and up | 1 |\n" in text, text)
		assertTrue("### Stand out" in text && "| Scene 4 | Dense | " in text, text)
		assertTrue("| Scene 1 | Early reader | " in text, text)
		assertTrue("| Scene 5 | *Too short* |  | 2 | " in text, text)
		assertTrue("| Scene 4 | accumulate (5), contemporaneous (5), " in text, text)
		assertFalse("≈" in text, text)
		// Scenes with the same text share their counts.
		assertEquals(3, fake.cache.size)

		// A second run reads its counts from the cache.
		assertEquals(text, report())
		fake.cache[cacheKey("Too short.", english, namesHash(emptySet()))] = "31 3 31 31 100 31 0 0 0 0".encodeToByteArray()
		val rough = report()
		assertTrue("| Scene 5 | Early reader | ≈" in rough && "≈ rough" in rough, rough)
	}

	@Test
	fun `run from a scene, it reports on that scene alone`() {
		scenes = mapOf(1 to SIMPLE, 2 to DENSE)
		val text = report("scene", 2)
		assertFalse("Whole story" in text)
		assertTrue("## Scene 2\n\n> **Dense** · grade" in text && "Scene 1" !in text, text)
		assertTrue("| Dense | 12 and up |" in text, text)
		assertTrue("## Scene 1" in report(chosen = listOf(1)))
		assertTrue("Whole story" in report("scene", 2, chosen = emptyList()))
	}

	@Test
	fun `the picker starts with the scene run from`() {
		val fromScene = ActionDriver("report", ::report, "Storm", context = ActionRequest.Context("scene", 2)).open()
		assertEquals(JsonArray(listOf(JsonPrimitive(2))), fromScene.values["scenes"])
		val fromProject = ActionDriver("report", ::report, "Storm", context = ActionRequest.Context("project", null)).open()
		assertEquals(JsonArray(emptyList()), fromProject.values["scenes"])
	}

	@Test
	fun `encyclopedia names are left out of rare words`() {
		scenes = mapOf(1 to "aelindrathor rode on and on. ".repeat(10))
		assertTrue("aelindrathor" in report())
		entries = listOf("Aelindrathor")
		assertFalse("aelindrathor" in report())
	}

	@Test
	fun `a language without lists gets words and dialogue`() {
		language = "pt"
		scenes = mapOf(1 to SIMPLE)
		val text = report()
		assertTrue("Reading levels need" in text, text)
		assertFalse("**Early reader**" in text, text)
		assertTrue("| 40 | 8 | 5.0 | 0% |" in text, text)
	}

	@Test
	fun `numbers read as the report writes them`() {
		assertEquals("1,234,567", thousands(1_234_567))
		assertEquals("999", thousands(999))
		assertEquals("5.3", tenths(5.25))
		assertEquals("-0.5", tenths(-0.46))
	}
}
