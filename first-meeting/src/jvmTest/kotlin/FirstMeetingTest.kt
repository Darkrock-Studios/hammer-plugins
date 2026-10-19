import hammer.ActionDriver
import hammer.ActionReply
import hammer.ActionRequest
import hammer.EntityRef
import hammer.FakeHammerHost
import hammer.OperationException
import hammer.host
import hammer.stateOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class FirstMeetingTest {
	private class Character(val name: String, val referencedBy: List<Int>)

	// Scene 9 is archived, so it is in no tree, and 42 is no scene at all.
	private val characters = mapOf(
		1 to Character("Alice", listOf(1, 2, 3, 5, 7, 9)),
		2 to Character("the White Rabbit", listOf(9, 1, 42, 3, 7)),
		4 to Character("Cheshire Cat", listOf(2)),
		6 to Character("Mock Turtle", listOf(9, 42)),
		8 to Character("Dodo", emptyList()),
	)

	/**
	 * Part One (Chapter 1: scenes 7 and 3; Chapter 2: scene 5), the top-level scene 2, then Part Two (scene 1):
	 * story order is not id order.
	 */
	private val tree = buildJsonObject {
		put("nodes", buildJsonArray {
			add(group(100, "Part One", group(101, "Chapter 1", scene(7, "Down the Rabbit-Hole"), scene(3, "The Pool of Tears")), group(102, "Chapter 2", scene(5, "A Caucus-Race"))))
			add(scene(2, "Interlude #1"))
			add(group(103, "Part Two", scene(1, "The Trial")))
		})
	}

	private val reads = mutableListOf<Int>()
	private var trees = 0

	init {
		host = FakeHammerHost(mapOf("entry.read" to ::readEntry, "scene.tree" to ::sceneTree))
	}

	private fun readEntry(input: JsonObject): JsonElement {
		val id = input["id"]!!.jsonPrimitive.int
		reads += id
		val character = characters[id] ?: throw OperationException("NotFound", "No entry $id")
		return buildJsonObject {
			put("id", id)
			put("name", character.name)
			put("type", "person")
			put("text", "")
			put("tags", JsonArray(emptyList()))
			put("aliases", JsonArray(emptyList()))
			put("hasImage", false)
			put("referencedBy", JsonArray(character.referencedBy.sorted().map(::JsonPrimitive)))
		}
	}

	private fun sceneTree(input: JsonObject): JsonElement {
		if (input["project"]!!.jsonPrimitive.content == "Locked") throw OperationException("PermissionDenied", "Not allowed to read the scenes")
		trees++
		return tree
	}

	private fun scene(id: Int, name: String) = buildJsonObject {
		put("id", id)
		put("name", name)
		put("kind", "scene")
		put("wordCount", 100)
		put("children", JsonArray(emptyList()))
	}

	private fun group(id: Int, name: String, vararg children: JsonObject) = buildJsonObject {
		put("id", id)
		put("name", name)
		put("kind", "group")
		put("wordCount", 100 * children.size)
		put("children", JsonArray(children.toList()))
	}

	private fun entry(id: Int) = EntityRef("entry", id)

	private fun driver(project: String = "Wonderland", context: ActionRequest.Context = ActionRequest.Context()) =
		ActionDriver("meet", ::meet, project, context = context)

	/** The pair [a] and [b] picked from Project Home, and Find pressed. */
	private fun find(a: Int, b: Int, project: String = "Wonderland") = driver(project).open().fill("a", entry(a)).fill("b", entry(b)).press("find")

	private val ActionDriver.buttons get() = page!!.buttons.map { Triple(it.id, it.label, it.inline) }

	private fun ActionDriver.pick(key: String): EntityRef? = values.getValue(key).takeUnless { it == JsonNull }?.let {
		val pick = it as JsonObject
		EntityRef(pick["kind"]!!.jsonPrimitive.content, pick["id"]!!.jsonPrimitive.int)
	}

	@Test
	fun `the action has no manifest page, and opens on its own, picking two people`() {
		assertTrue(ActionDriver.manifestFields("meet").isEmpty())
		val driver = driver().open()
		assertEquals(1, driver.requests.size)
		assertNull(driver.requests.single().button)
		val page = driver.page!!
		assertNull(page.title)
		assertEquals(listOf("a" to "First character", "b" to "Second character"), page.fields.map { it.key to it.label })
		for (field in page.fields) {
			assertEquals("entity", field.type)
			assertTrue(field.required)
			assertEquals(listOf("entry"), field.kinds)
			assertEquals(listOf("person"), field.entryTypes)
			assertEquals(false, field.multiple)
			assertNull(field.default)
		}
		assertEquals(listOf("find"), page.buttons.map { it.id })
		assertTrue(page.buttons.single().primary)
		assertNull(driver.pick("a"))
	}

	@Test
	fun `run from an entry, the first character starts as that entry`() {
		val driver = driver(context = ActionRequest.Context("entry", 4)).open()
		assertEquals(entry(4), driver.pick("a"))
		assertNull(driver.pick("b"))

		// Only an entry's id is a character's: from a scene, nothing is picked.
		assertNull(driver(context = ActionRequest.Context("scene", 4)).open().pick("a"))
	}

	@Test
	fun `both characters are needed, and Hammer asks before the plugin is called`() {
		val driver = driver(context = ActionRequest.Context("entry", 1)).open().press("find")
		assertEquals(mapOf("b" to "Required"), driver.errors)
		assertEquals(1, driver.requests.size)
	}

	@Test
	fun `the same character twice is refused under the second`() {
		val driver = find(1, 1)
		assertEquals(mapOf("b" to "Pick two different characters."), driver.errors)
		assertEquals(listOf("a", "b"), driver.page!!.fields.map { it.key })
		assertTrue(reads.isEmpty())
	}

	@Test
	fun `the first scene they share is first in story order, in its groups, then the rest in order`() {
		val driver = find(1, 2)
		val page = driver.page!!
		assertEquals("Alice and the White Rabbit", page.title)
		assertEquals(
			"**Alice** and **the White Rabbit** first meet in **Down the Rabbit-Hole**, in Part One › Chapter 1.\n\n" +
				"They also share:\n\n" +
				"- **The Pool of Tears**, in Part One › Chapter 1\n" +
				"- **The Trial**, in Part Two\n\n" +
				"They share 3 scenes.",
			page.markdown,
		)
		assertEquals(
			listOf(
				Triple("open:7", "Down the Rabbit-Hole", true),
				Triple("open:3", "The Pool of Tears", true),
				Triple("open:1", "The Trial", true),
				Triple("back", "Back", false),
				Triple("open:7", "Open Down the Rabbit-Hole", false),
			),
			driver.buttons,
		)
		assertEquals(listOf("open:7"), page.buttons.filter { it.primary }.map { it.id })
		assertEquals(false, page.buttons.single { it.id == "back" }.validate)
		assertTrue(page.fields.isEmpty())
		// Scene 9 is archived and 42 is no scene: neither is shared.
		assertEquals(Meeting(entry(1), entry(2), listOf(7, 3, 1)), driver.state<Meeting>())
		assertEquals(listOf(1, 2), reads)
	}

	@Test
	fun `a single shared scene at the top of the tree has no groups to name`() {
		val driver = find(4, 1)
		assertEquals("Cheshire Cat and Alice", driver.page!!.title)
		assertEquals(
			"**Cheshire Cat** and **Alice** first meet in **Interlude \\#1**.\n\nIt is the only scene they share.",
			driver.page!!.markdown,
		)
		assertEquals(
			listOf(Triple("open:2", "Interlude #1", true), Triple("back", "Back", false), Triple("open:2", "Open Interlude #1", false)),
			driver.buttons,
		)
	}

	@Test
	fun `a pair sharing only archived or missing scenes never meets`() {
		val driver = find(2, 6)
		val page = driver.page!!
		assertEquals("the White Rabbit and Mock Turtle", page.title)
		assertEquals(
			"**the White Rabbit** and **Mock Turtle** are never in the same scene.\n\n" +
				"This goes by the references confirmed in each scene's metadata: if they do share a scene, add " +
				"whichever of them it is missing in that scene's references panel.",
			page.markdown,
		)
		assertEquals(listOf(Triple("back", "Back", false)), driver.buttons)
		assertEquals(Meeting(entry(2), entry(6)), driver.state<Meeting>())
		assertEquals(1, trees)
	}

	@Test
	fun `a pair with no scene in common never meets, without reading the tree`() {
		val driver = find(8, 1)
		assertEquals("**Dodo** and **Alice** are never in the same scene.", driver.page!!.markdown!!.substringBefore("\n"))
		assertEquals(0, trees)
	}

	@Test
	fun `back returns to the picking page with the pair still picked`() {
		val driver = find(1, 2).press("back")
		assertEquals(listOf("a", "b"), driver.page!!.fields.map { it.key })
		assertEquals(entry(1), driver.pick("a"))
		assertEquals(entry(2), driver.pick("b"))

		driver.fill("b", entry(4)).press("find")
		assertEquals("Alice and Cheshire Cat", driver.page!!.title)
		driver.press("back")
		assertEquals(entry(4), driver.pick("b"))

		assertEquals(entry(6), find(2, 6).press("back").pick("b"))
	}

	@Test
	fun `a scene's button opens it, as does the footer's`() {
		val driver = find(1, 2).press("open:3")
		assertEquals("scene" to 3, driver.opened!!.let { it.place to it.id })
		assertTrue(driver.ended)
		assertEquals("scene" to 7, find(1, 2).press("open:7").opened!!.let { it.place to it.id })
	}

	@Test
	fun `a scene that is not one they share is not opened`() {
		val state = stateOf(Meeting(entry(1), entry(2), listOf(7, 3, 1)))
		fun press(button: String) = meet(ActionRequest("meet", "Wonderland", button = button, state = state))
		for (button in listOf("open:5", "open:", "open:x")) {
			val reply = press(button)
			assertNull(reply.open)
			assertNull(reply.page)
			assertEquals(setOf(""), reply.errors.keys)
		}
		assertNull(meet(ActionRequest("meet", "Wonderland", button = "open:7")).open)
		assertEquals("scene", press("open:1").open!!.place)
	}

	@Test
	fun `a failed operation is an error on the picking page`() {
		val gone = find(1, 99)
		assertEquals(mapOf("" to "No entry 99"), gone.errors)
		assertEquals(listOf("a", "b"), gone.page!!.fields.map { it.key })
		assertEquals(entry(99), gone.pick("b"))

		val locked = find(1, 2, project = "Locked")
		assertEquals(mapOf("" to "Not allowed to read the scenes"), locked.errors)
		assertEquals(listOf("find"), locked.page!!.buttons.map { it.id })
	}

	@Test
	fun `an unknown button goes back to picking`() {
		val reply: ActionReply = meet(ActionRequest("meet", "Wonderland", button = "later", state = stateOf(Meeting(entry(1), entry(2)))))
		assertEquals(listOf("a", "b"), reply.page!!.fields.map { it.key })
		assertEquals(JsonPrimitive(2), (reply.page!!.fields[1].default as JsonObject)["id"])
	}
}
