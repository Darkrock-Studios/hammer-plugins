import hammer.ActionDriver
import hammer.ActionRequest
import hammer.FakeHammerHost
import hammer.OperationException
import hammer.host
import hammer.state
import hammer.stateOf
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive

class RealNamesTest {
	private val created = mutableListOf<JsonObject>()

	init {
		host = FakeHammerHost(
			mapOf(
				"entry.create" to { input ->
					if (input["project"]!!.jsonPrimitive.content == "Locked") throw OperationException("PermissionDenied", "Not allowed")
					created += input
					buildJsonObject {}
				},
			),
		)
	}

	private fun driver(project: String = "Storm") = ActionDriver("names", ::names, project)

	@Test
	fun `a seed gives the same names again, with surnames`() {
		val names = generate(42, NamesInput())
		assertEquals(names, generate(42, NamesInput()))
		assertNotEquals(names, generate(43, NamesInput()))
		assertEquals(8, names.size)
		assertTrue(names.all { ' ' in it })
		assertEquals(MAX_NAMES, generate(1, NamesInput(count = 99)).size)
	}

	@Test
	fun `given names come from the decade's list of the gender asked for`() {
		val list = nameList("american", "feminine-1880")!!
		val names = generate(7, NamesInput(era = "1880", gender = "feminine", count = 20, surnames = false))
		assertTrue(names.all { it in list })
		assertTrue("Mary" in list && "John" !in list)
		assertTrue("Mary" !in nameList("american", "masculine-1900")!!)
	}

	@Test
	fun `an era without lists of its own gives the nearest decade's names, or those of all years`() {
		fun women(era: String) = generate(42, NamesInput(culture = "mexican", era = era, gender = "feminine", surnames = false))
		assertNull(nameList("mexican", "feminine-1930"))
		assertEquals(women("1940"), women("1930"))
		assertEquals(women("any"), women("1890"))
		assertEquals(generate(42, NamesInput()), generate(42, NamesInput(era = "1700")))
		assertFailsWith<IllegalStateException> { generate(42, NamesInput(culture = "atlantean")) }
	}

	@Test
	fun `a culture's format orders the name`() {
		val surnames = nameList("japanese", "surnames")!!
		val given = nameList("japanese", "masculine-1950")!!
		val names = generate(3, NamesInput(culture = "japanese", era = "1950", gender = "masculine"))
		assertTrue(names.all { it.substringBefore(' ') in surnames && it.substringAfter(' ') in given })
		assertTrue(generate(3, NamesInput(culture = "japanese", gender = "masculine", surnames = false)).none { ' ' in it })
		assertTrue("François" in nameList("french", "surnames")!! && "Hélène" in nameList("french", "feminine-1950")!!)
	}

	@Test
	fun `every culture in the manifest gives names of each gender and rarity`() {
		val manifest = java.io.File("manifest.toml").readText()
		val cultures = Regex("value = \"(\\w+)\", label = \"[A-Z]\\w+\" }").findAll(manifest.substringBefore("key = \"era\"")).map { it.groupValues[1] }.toList()
		assertTrue(cultures.size > 20 && "american" in cultures)
		for (culture in cultures) for (gender in listOf("feminine", "masculine")) for (rarity in listOf("common", "typical", "rare")) {
			val names = generate(11, NamesInput(culture, "1950", gender, rarity, 5))
			assertTrue(names.size == 5 && names.all { ' ' in it }, "$culture $gender $rarity: $names")
		}
	}

	@Test
	fun `a culture may have two surnames, or a surname list for each gender`() {
		assertTrue(generate(5, NamesInput(culture = "mexican", count = 20)).all { it.count { c -> c == ' ' } >= 2 })

		assertNull(nameList("russian", "surnames"))
		val women = nameList("russian", "surnames-feminine")!!
		val men = nameList("russian", "surnames-masculine")!!
		assertTrue("Ivanova" in women && "Ivanov" !in women && "Ivanov" in men && "Ivanova" !in men)
		assertTrue(generate(5, NamesInput(culture = "russian", gender = "feminine", count = 20)).all { it.substringAfter(' ') in women })
		assertTrue(generate(5, NamesInput(culture = "russian", gender = "masculine", count = 20)).all { it.substringAfter(' ') in men })
	}

	@Test
	fun `surnames are written as they usually are`() {
		val surnames = nameList("american", "surnames")!!
		assertTrue(listOf("O'Brien", "McDonald", "De La Cruz", "St. John").all { it in surnames })
		assertTrue("Mcdonald" !in surnames)
	}

	@Test
	fun `a name is picked as often as it is common`() {
		val list = NameList(listOf("Mary Ann\t99", "Rare\t1", ""))
		val random = Random(5)
		val rare = (1..1000).count { list.pick(random) == "Rare" }
		assertTrue(rare in 1..39)
		assertTrue((1..100).all { list.pick(random, "common") == "Mary Ann" && list.pick(random, "rare") == "Rare" })
		assertEquals("Only", NameList(listOf("Only\t5")).pick(random, "rare"))
		val surnames = nameList("american", "surnames")!!
		assertTrue((1..200).none { surnames.pick(random, "rare") == "Smith" })
		assertTrue((1..200).map { surnames.pick(random, "common") }.toSet().let { "Smith" in it && it.size > 50 })
		assertTrue("Mary Ann" in list)
		assertFailsWith<IllegalStateException> { NameList(listOf("Mary")) }
		assertNull(nameList("american", "nothing"))
	}

	@Test
	fun `the page has a name to add for each, more names, and the options in its state`() {
		val driver = driver().open().fill("culture", "japanese").fill("count", 3).run()
		val state = driver.state<NamesState>()!!
		assertEquals(listOf("japanese", 3, true), state.options.let { listOf(it.culture, it.count, it.surnames) })
		val names = generate(state.seed, state.options)
		assertEquals("Press a name to add it to the encyclopedia as a person.\n\n${names.joinToString("\n") { "- $it" }}\n", driver.page!!.markdown)
		assertEquals(names.indices.map { "add:$it" } + "more", driver.page!!.buttons.map { it.id })
		assertEquals(names, driver.page!!.buttons.filter { it.inline }.map { it.label })
		assertEquals("More names", driver.page!!.buttons.single { !it.inline }.label)
	}

	@Test
	fun `pressing a name adds it as a person and marks it`() {
		val driver = driver().open().fill("count", 2).run()
		val names = driver.state<NamesState>()!!.let { generate(it.seed, it.options) }
		driver.press("add:1")
		assertEquals(listOf("Added ${names[1]} to the encyclopedia"), driver.messages)
		assertEquals(listOf("Storm", "person", names[1]), listOf("project", "type", "name").map { created.single()[it]!!.jsonPrimitive.content })
		assertTrue(driver.page!!.markdown!!.contains("- ${names[1]} (added)"))
		assertEquals(listOf("add:0", "more"), driver.page!!.buttons.map { it.id })
		driver.press("add:0")
		assertEquals(listOf("more"), driver.page!!.buttons.map { it.id })
		assertEquals(3, driver.state<NamesState>()!!.added)
	}

	@Test
	fun `more names are new ones of the same options, none added`() {
		val driver = driver().open().fill("culture", "irish").fill("count", 4).run().press("add:0")
		val before = driver.state<NamesState>()!!
		driver.press("more")
		val after = driver.state<NamesState>()!!
		assertNotEquals(before.seed, after.seed)
		assertEquals(listOf("irish", 4), listOf(after.options.culture, after.options.count))
		assertEquals(0, after.added)
		assertEquals(5, driver.page!!.buttons.size)
	}

	@Test
	fun `a refused entry says why and marks nothing`() {
		val driver = driver("Locked").open().run()
		val page = driver.page
		driver.press("add:0")
		assertEquals(listOf("Not allowed"), driver.messages)
		assertEquals(page!!.markdown, driver.page!!.markdown)
		assertEquals(9, driver.page!!.buttons.size)
		assertTrue(created.isEmpty())
	}

	@Test
	fun `a page whose state is unreadable starts over, and a name not in the list fails`() {
		val stale = names(ActionRequest("names", "Storm", button = "add:0", state = JsonPrimitive("add:42:0:0")))
		assertEquals(9, stale.page!!.buttons.size)
		assertEquals("american", ActionRequest("names", "Storm", state = stale.page!!.state).state<NamesState>()!!.options.culture)
		assertTrue(created.isEmpty())
		val state = stateOf(NamesState(42, NamesInput(count = 2)))
		assertFailsWith<IllegalStateException> { names(ActionRequest("names", "Storm", button = "add:2", state = state)) }
	}
}
