import hammer.ActionDriver
import hammer.ActionReply
import hammer.ActionRequest
import hammer.FakeHammerHost
import hammer.OperationException
import hammer.TextFormat
import hammer.host
import hammer.stateOf
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.Json
import hammer.ops.Entry

class CharacterInterviewTest {
	private val created = mutableListOf<JsonObject>()

	init {
		host = FakeHammerHost(mapOf("entry.create" to ::createEntry))
	}

	private fun createEntry(input: JsonObject): JsonElement {
		if (input["project"]!!.jsonPrimitive.content == "Locked") throw OperationException("PermissionDenied", "Not allowed")
		created += input
		return Json.encodeToJsonElement(Entry(id = 12, name = input["name"]!!.jsonPrimitive.content))
	}

	private fun driver(project: String = "Storm") = ActionDriver("interview", ::interview, project)

	/** The interview started for Mara with the quick set, on its first page of questions. */
	private fun mara(project: String = "Storm") = driver(project).open().fill("name", "Mara").run()

	private val ActionDriver.title get() = page!!.title
	private val ActionDriver.markdown get() = page!!.markdown

	private fun ActionDriver.value(key: String) = values.getValue(key).jsonPrimitive.content

	private fun ActionReply.Field.initial() = default?.jsonPrimitive?.content

	@Test
	fun `each set has its pages and questions, numbered through the set`() {
		val sizes = SETS.keys.associateWith { questionSet(it).let { set -> set.pages.size to set.questions.size } }
		assertEquals(mapOf("quick" to (4 to 12), "full" to (8 to 33), "proust" to (5 to 31)), sizes)
		val quick = questionSet("quick")
		assertEquals(listOf("Who they are", "Wants and fears", "Wounds and secrets", "Voice"), quick.pages.map { it.heading })
		assertEquals("Picture them sitting across from you. Answer as they would, or in your own words about them.", quick.pages[0].intro)
		assertNull(quick.pages[1].intro)
		assertEquals((1..12).toList(), quick.questions.map { it.number })
		assertEquals("What's your motto, spoken or not?", quick.questions.last().text)
	}

	@Test
	fun `the manifest's first page offers the sets the plugin has`() {
		val fields = ActionDriver.manifestFields("interview")
		assertEquals(listOf("name", "set"), fields.map { it.key })
		assertTrue(fields[0].required)
		assertEquals(TextFormat.Plain, fields[0].format)
		assertEquals(setOptions().map { it.value to it.label }, fields[1].options.map { it.value to it.label })
		assertEquals(DEFAULT_SET, fields[1].initial())
	}

	@Test
	fun `a set is read whatever its spacing, leaving out what is not a page of questions`() {
		val set = parseQuestionSet(
			"# Title\nBefore any page.\n\n##  First  \r\nAn intro  \nover two lines.\n\n\nAnd a second.\n\n" +
				"-   One?  \n- Two,\n  continued?\n\n\n## Only an intro\nNo questions.\n## Second\n-\n- Three?",
		)
		assertEquals(listOf("First", "Second"), set.pages.map { it.heading })
		assertEquals("An intro\nover two lines.\n\nAnd a second.", set.pages[0].intro)
		assertNull(set.pages[1].intro)
		assertEquals(listOf(1 to "One?", 2 to "Two, continued?", 3 to "Three?"), set.questions.map { it.number to it.text })
		assertTrue(parseQuestionSet("Nothing here\n- Nor here?").pages.isEmpty())
	}

	@Test
	fun `a set that is missing or has no questions fails`() {
		val resources = createTempDirectory().toFile()
		File(resources, "sets").mkdirs()
		File(resources, "sets/empty.md").writeText("## Nothing\nNo questions here.\n")
		host = FakeHammerHost(resourceDirectory = resources)
		assertEquals("The interview empty has no questions", assertFailsWith<IllegalStateException> { questionSet("empty") }.message)
		assertEquals("There is no interview named gone", assertFailsWith<IllegalStateException> { questionSet("gone") }.message)
		resources.deleteRecursively()
	}

	@Test
	fun `each page of questions is titled with the name and where it is, and the last leads to the review`() {
		val driver = mara()
		assertEquals(Interview("Mara", "quick", 1), driver.state<Interview>())
		assertEquals("""{"name":"Mara","set":"quick","step":1}""", driver.page!!.state.toString())

		val first = driver.page!!
		assertEquals("Picture them sitting across from you. Answer as they would, or in your own words about them.", first.markdown)
		assertEquals(listOf("q1", "q2", "q3"), first.fields.map { it.key })
		assertEquals("What do people call you, and what do you wish they called you?", first.fields[0].label)
		assertTrue(first.fields.all { it.type == "text" && it.format == TextFormat.Plain && it.multiline && !it.required })
		assertEquals(listOf(Triple("back", "Back", false), Triple("next", "Next", true)), first.buttons.map { Triple(it.id, it.label, it.validate) })
		assertTrue(first.buttons.single { it.id == "next" }.primary)

		val titles = (1..4).map { driver.title.also { driver.press("next") } }
		assertEquals(
			listOf("Mara: Who they are (1 of 4)", "Mara: Wants and fears (2 of 4)", "Mara: Wounds and secrets (3 of 4)", "Mara: Voice (4 of 4)"),
			titles,
		)
		assertEquals("Mara: Review", driver.title)
		assertEquals(listOf("back", "create"), driver.page!!.buttons.map { it.id })
		assertEquals("Create entry", driver.page!!.buttons.single { it.primary }.label)
		assertTrue(driver.page!!.fields.isEmpty())

		driver.press("back")
		assertEquals("Mara: Voice (4 of 4)", driver.title)
		assertNull(driver.markdown)
		assertEquals(listOf("q10", "q11", "q12"), driver.page!!.fields.map { it.key })
		assertEquals("Review", driver.page!!.buttons.single { it.id == "next" }.label)
	}

	@Test
	fun `back keeps what was typed, back to the first page and forward again`() {
		val driver = mara().fill("q1", "Mara, or *Captain*").press("next").fill("q4", "The sea").press("back")
		assertEquals("Mara, or *Captain*", driver.value("q1"))
		assertEquals("", driver.value("q2"))

		driver.press("back")
		val first = driver.page!!
		assertNull(first.title)
		assertEquals(listOf("name", "set"), first.fields.map { it.key })
		assertEquals(listOf("Mara", "quick"), first.fields.map { it.initial() })
		assertTrue(first.fields[0].required)
		assertEquals(setOptions().map { it.value }, first.fields[1].options.map { it.value })
		assertEquals(listOf("next"), first.buttons.map { it.id })
		assertEquals(0, driver.state<Interview>()!!.step)

		driver.fill("name", "Mara Vell").press("next")
		assertEquals("Mara Vell: Who they are (1 of 4)", driver.title)
		assertEquals("Mara, or *Captain*", driver.value("q1"))
		driver.press("next")
		assertEquals("The sea", driver.value("q4"))
	}

	@Test
	fun `switching sets keeps each set's answers`() {
		val driver = mara().fill("q1", "Mara").press("back").fill("set", "full").press("next")
		assertEquals("Mara: The basics (1 of 8)", driver.title)
		assertEquals("", driver.value("q1"))
		driver.fill("q1", "Mara Vell, Mars to her crew").press("back").fill("set", "quick").press("next")
		assertEquals("Mara", driver.value("q1"))
		assertEquals(mapOf("quick/1" to "Mara", "full/1" to "Mara Vell, Mars to her crew"), driver.state<Interview>()!!.answers)
		driver.press("back").fill("set", "proust").press("next")
		assertEquals("Mara: Character (1 of 5)", driver.title)
		assertEquals("Questions from the confession album Marcel Proust filled in around 1890, put to your character.", driver.markdown)
	}

	private val review = """
		## Who they are

		**What do people call you, and what do you wish they called you?**

		Mara. *Captain*, one day.

		**What do you do with your days?**

		Mends nets.

		## Wounds and secrets

		**What happened to you that still hurts?**

		The storm took her brother.

		She never found him.
	""".trimIndent()

	/** Mara's interview with answers on the first and third pages, at its review. */
	private fun reviewed(project: String = "Storm") = mara(project)
		.fill("q1", "Mara. *Captain*, one day.\n").fill("q2", "  Mends nets.").press("next")
		.press("next")
		.fill("q7", "The storm took her brother.\n\nShe never found him.").press("next")
		.press("next")

	@Test
	fun `the review shows the entry as it will be written`() {
		val driver = reviewed()
		assertEquals("Mara: Review", driver.title)
		assertEquals(review, driver.markdown)
	}

	@Test
	fun `headings in an answer sit under its page's heading`() {
		val driver = mara()
			.fill("q1", "# Names\n\nMara.\n\n## At sea\n\n*Captain*.\n\n```\n# kept\n```\n\n#1 on deck")
			.fill("q2", "### Days\n\nNets.")
			.press("next").press("next").press("next").press("next")
		val expected = """
			## Who they are

			**What do people call you, and what do you wish they called you?**

			### Names

			Mara.

			#### At sea

			*Captain*.

			```
			# kept
			```

			#1 on deck

			**What do you do with your days?**

			### Days

			Nets.
		""".trimIndent()
		assertEquals(expected, driver.markdown)
	}

	/** The entry written for Mara with [answer] to the first question and "Nets." to the second. */
	private fun entryWith(answer: String) = mara().fill("q1", answer).fill("q2", "Nets.")
		.press("next").press("next").press("next").press("next")
		.markdown!!
		.removePrefix("## Who they are\n\n**What do people call you, and what do you wish they called you?**\n\n")
		.removeSuffix("\n\n**What do you do with your days?**\n\nNets.")

	@Test
	fun `an answer goes in line for line`() {
		val answer = "Mara.\nCaptain, one day.\n- not yet\n> she says\n1. first mate\n    the old way\n---\nNets *and* rope"
		assertEquals(answer, entryWith(answer))
	}

	@Test
	fun `a code fence an answer leaves open is closed before the next question`() {
		assertEquals("```\n# kept\n```", entryWith("```\n# kept"))
		assertEquals("Code:\n~~~~\n~~~\n# kept\n~~~~", entryWith("Code:\n~~~~\n~~~\n# kept"))
		assertEquals("```kotlin\nval x = 1\n```\n\nDone.", entryWith("```kotlin\nval x = 1\n```\n\nDone."))
		assertEquals("A ```span``` here", entryWith("A ```span``` here"))
	}

	@Test
	fun `text over a line of equals signs is a heading under its page's`() {
		assertEquals("### Names\n\nMara.\n\n#### Later", entryWith("Names\n===\n\nMara.\n\n## Later"))
		assertEquals("### First\n### Second\nMara.", entryWith("First\nSecond\n=\nMara."))
		assertEquals("### Room \\#", entryWith("Room #\n==="))
		assertEquals("Mara.\n---", entryWith("Mara.\n---"))
		assertEquals("- nets\n===", entryWith("- nets\n==="))
		assertEquals("Mara.\n\n===", entryWith("Mara.\n\n==="))
		assertEquals("```\nMara.\n===\n```", entryWith("```\nMara.\n===\n```"))
	}

	@Test
	fun `create entry adds the person and opens them`() {
		val driver = reviewed().press("create")
		val input = created.single()
		assertEquals(listOf("Storm", "Mara", "person", review), listOf("project", "name", "type", "text").map { input[it]!!.jsonPrimitive.content })
		assertEquals("entry" to 12, driver.opened?.let { it.place to it.id })
		assertEquals(listOf("Added Mara to the encyclopedia"), driver.messages)
		assertTrue(driver.ended)
	}

	@Test
	fun `a refused entry says why and stays on the review`() {
		val driver = reviewed("Locked").press("create")
		assertEquals(mapOf("" to "Not allowed"), driver.errors)
		assertEquals("Mara: Review", driver.title)
		assertFalse(driver.ended)
		assertTrue(created.isEmpty() && driver.messages.isEmpty())
	}

	@Test
	fun `a name Hammer would refuse is pointed out on the page it was typed on`() {
		val driver = driver().open().fill("name", "bad~name").run()
		assertEquals(mapOf("name" to "A name cannot have ~"), driver.errors)
		assertEquals(listOf("name", "set"), driver.page!!.fields.map { it.key })
		assertNull(driver.page!!.state)

		driver.fill("name", "  Mara  ").run().press("back").fill("name", "Mara.").press("next")
		assertEquals(mapOf("name" to "A name cannot end with a dot"), driver.errors)
		assertEquals(0, driver.state<Interview>()!!.step)
		driver.fill("name", "Mara").press("next")
		assertEquals("Mara: Who they are (1 of 4)", driver.title)

		assertNull(nameProblem("Zoë O'Neil-Ruiz (the elder), 2nd"))
		assertNull(nameProblem("李小龙"))
		assertNull(nameProblem("x".repeat(MAX_NAME)))
		assertEquals("A name is at most 64 characters", nameProblem("x".repeat(MAX_NAME + 1)))
		assertEquals("A name cannot have # ~", nameProblem("a#b~c#"))
		assertEquals("Enter a name", nameProblem(""))
	}

	@Test
	fun `letters past U+FFFF are taken in a name, and emoji are not`() {
		assertNull(nameProblem("\uD842\uDFB7野"))
		assertNull(nameProblem("\uD835\uDC00da"))
		assertEquals("A name cannot have \uD83D\uDE00", nameProblem("Mara \uD83D\uDE00\uD83D\uDE00"))
		assertEquals("A name cannot have \uD800", nameProblem("Mara\uD800"))
	}

	@Test
	fun `blank answers are left out`() {
		val driver = mara().fill("q1", " \n ").fill("q2", "Fishing").press("next")
		assertEquals(mapOf("quick/2" to "Fishing"), driver.state<Interview>()!!.answers)
		driver.press("back").fill("q2", "").press("next")
		assertEquals(emptyMap<String, String>(), driver.state<Interview>()!!.answers)
		driver.press("next").press("next").press("next")
		assertEquals("No question has an answer, so the entry for Mara will have no text.", driver.markdown)
		driver.press("create")
		assertEquals("", created.single()["text"]!!.jsonPrimitive.content)
	}

	private fun next(state: JsonElement?, input: JsonObject = JsonObject(emptyMap())) =
		interview(ActionRequest("interview", "Storm", input = input, button = "next", state = state))

	@Test
	fun `a state it cannot read starts again on the first page with what it can`() {
		for (state in listOf(null, JsonPrimitive("nonsense"), buildJsonObject { put("name", 3) })) {
			val reply = next(state)
			assertEquals(listOf("", "quick"), reply.page!!.fields.map { it.initial() }, "$state")
			assertEquals("This interview could not carry on from where it was left, so it starts again here", reply.message)
		}

		val stale = next(buildJsonObject {
			put("name", "Mara")
			put("set", "custom")
			put("step", 2)
			putJsonObject("answers") {
				put("quick/1", "Kept")
				put("quick/2", 7)
			}
		})
		assertEquals(listOf("Mara", "quick"), stale.page!!.fields.map { it.initial() })
		val restarted = next(stale.page!!.state, buildJsonObject {
			put("name", "Mara")
			put("set", "quick")
		})
		assertEquals("Mara: Who they are (1 of 4)", restarted.page!!.title)
		assertEquals("Kept", restarted.page!!.fields[0].initial())

		val past = next(stateOf(Interview("Mara", "full", 42)))
		assertEquals(listOf("Mara", "full"), past.page!!.fields.map { it.initial() })
		assertTrue(created.isEmpty())
	}

	@Test
	fun `a resumed run carries on where it was left`() {
		val driver = mara().fill("q1", "Mara").press("next").fill("q4", "A boat").resume().press("next")
		assertTrue(driver.requests.last().resumed)
		assertEquals("Mara: Wounds and secrets (3 of 4)", driver.title)
		assertEquals(mapOf("quick/1" to "Mara", "quick/4" to "A boat"), driver.state<Interview>()!!.answers)
		assertTrue(driver.messages.isEmpty())
	}
}
