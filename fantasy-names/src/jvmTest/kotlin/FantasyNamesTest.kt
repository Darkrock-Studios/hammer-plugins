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
import kotlin.test.assertTrue
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive

class FantasyNamesTest {
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

	private val styles = Regex("value = \"([\\w-]+)\", label = \"[A-Z][^\"]+\" },")
		.findAll(java.io.File("manifest.toml").readText().substringBefore("key = \"gender\"")).map { it.groupValues[1] }.toList()

	@Test
	fun `a seed gives the same names again`() {
		val names = generate(42, NamesInput())
		assertEquals(names, generate(42, NamesInput()))
		assertNotEquals(names, generate(43, NamesInput()))
		assertEquals(8, names.size)
		assertEquals(MAX_NAMES, generate(1, NamesInput(count = 99)).size)
		assertFailsWith<IllegalStateException> { generate(42, NamesInput(style = "atlantean")) }
		assertTrue((1L..40).flatMap { generate(it, NamesInput(count = 20, bynames = false)) }.none { it in listOf("Anna", "Beth", "Gwen") })
	}

	@Test
	fun `a made-up name sounds like the real ones without being one`() {
		val real = realNames("old-kingdom", "masculine")!!
		val known = real.mapTo(HashSet()) { it.lowercase() }
		val maker = NameMaker(real)
		val random = Random(3)
		val names = List(50) { maker.make(random, known::contains) }
		assertTrue(names.all { it.length in 4..12 && it.lowercase() !in known && it[0].isUpperCase() }, "$names")
		assertTrue(names.all { name -> name.lowercase().windowed(4).all { part -> known.any { part in it } } }, "$names")
		assertTrue(names.toSet().size > 40)
		assertEquals("Abc", NameMaker(listOf("Abc")).make(random) { true })
		assertFailsWith<IllegalStateException> { NameMaker(emptyList()) }
	}

	@Test
	fun `every style in the manifest makes up names of each gender, none of them real or twice`() {
		assertEquals(9, styles.size)
		for (style in styles) for (gender in listOf("feminine", "masculine")) {
			val real = listOf("feminine", "masculine").flatMap { realNames(style, it).orEmpty() }.toSet()
			assertTrue(real.size > 200, style)
			val given = generate(11, NamesInput(style, gender, 20, false))
			assertTrue(given.size == 20 && given.none { ' ' in it || it in real } && given.toSet().size == 20, "$style $gender: $given")
			val full = generate(11, NamesInput(style, gender, 20))
			assertTrue(full.all { ' ' in it && it[0].isUpperCase() }, "$style $gender: $full")
		}
	}

	@Test
	fun `a style's format gives the second name`() {
		fun names(style: String, gender: String) = generate(5, NamesInput(style, gender, 20))
		assertTrue(names("elvish", "feminine").all { it.matches(Regex("\\p{Lu}\\p{Ll}+ ferch \\p{Lu}\\p{Ll}+")) })
		assertTrue(names("elvish", "masculine").all { it.matches(Regex("\\p{Lu}\\p{Ll}+ ap \\p{Lu}\\p{Ll}+")) })
		assertTrue(names("dwarvish", "feminine").all { it.endsWith("sdottir") })
		assertTrue((1L..20).flatMap { generate(it, NamesInput("dwarvish", "masculine", 20)) }.all { it.endsWith("sson") && "sss" !in it })
		assertTrue(names("high-elvish", "any").all { it.matches(Regex("\\p{Lu}\\p{Ll}+ \\p{Lu}\\p{Ll}+")) })
		assertTrue(names("old-kingdom", "masculine").all { it.endsWith("son") })
		assertTrue(names("mythic", "any").all { it.matches(Regex("\\p{Lu}\\p{Ll}+ of \\p{Lu}\\p{Ll}+")) })
		assertTrue(names("forest", "masculine").all { it.matches(Regex(".*[^aeiouy]ovic")) })
		assertTrue(names("orcish", "any").all { " of clan " in it })
		val places = realNames("mythic", "places")!!
		assertTrue((1L..20).flatMap { names("mythic", "any") }.none { it.substringAfter(" of ") in places })
	}

	@Test
	fun `the page has a name to add for each, more names, and the options in its state`() {
		val driver = driver().open().fill("style", "dwarvish").fill("count", 3).run()
		val state = driver.state<NamesState>()!!
		assertEquals(listOf("dwarvish", 3, true), state.options.let { listOf(it.style, it.count, it.bynames) })
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
		val driver = driver().open().fill("style", "orcish").fill("count", 4).run().press("add:0")
		val before = driver.state<NamesState>()!!
		driver.press("more")
		val after = driver.state<NamesState>()!!
		assertNotEquals(before.seed, after.seed)
		assertEquals(listOf("orcish", 4), listOf(after.options.style, after.options.count))
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
		assertEquals("elvish", ActionRequest("names", "Storm", state = stale.page!!.state).state<NamesState>()!!.options.style)
		assertTrue(created.isEmpty())
		val state = stateOf(NamesState(42, NamesInput(count = 2)))
		assertFailsWith<IllegalStateException> { names(ActionRequest("names", "Storm", button = "add:2", state = state)) }
	}
}
