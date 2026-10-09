import hammer.ActionRequest
import hammer.FakeHammerHost
import hammer.OperationException
import hammer.host
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

	private fun request(button: String? = null, project: String = "Storm", vararg input: Pair<String, JsonPrimitive>) =
		ActionRequest("names", project, input = JsonObject(input.toMap()), button = button)

	private val styles = Regex("value = \"([\\w-]+)\", label = \"[A-Z][^\"]+\\(")
		.findAll(java.io.File("manifest.toml").readText()).map { it.groupValues[1] }.toList()

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
		assertEquals(4, styles.size)
		for (style in styles) for (gender in listOf("feminine", "masculine")) {
			val real = listOf("feminine", "masculine", "bynames").flatMap { realNames(style, it).orEmpty() }.toSet()
			assertTrue(real.size > 300, style)
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
	}

	@Test
	fun `the first reply has a button for each name, and one for more`() {
		val reply = names(request())
		assertEquals(9, reply.buttons.size)
		assertTrue(reply.buttons.last().let { it.id == "more" && it.footer })
		assertTrue(reply.buttons.first().id.matches(Regex("add:-?\\d+:0:0")))
		assertTrue(reply.markdown!!.contains("- ${reply.buttons.first().label}\n"))
	}

	@Test
	fun `pressing a name adds it as a person and marks it`() {
		val names = generate(42, NamesInput(count = 2))
		val reply = names(request("add:42:1:0", "Storm", "count" to JsonPrimitive(2)))
		assertEquals("Added ${names[1]} to the encyclopedia", reply.message)
		assertEquals(listOf("Storm", "person", names[1]), listOf("project", "type", "name").map { created.single()[it]!!.jsonPrimitive.content })
		assertTrue(reply.markdown!!.contains("- ${names[1]} (added)"))
		assertEquals(listOf("add:42:0:2", "more"), reply.buttons.map { it.id })
	}

	@Test
	fun `a refused entry says why and marks nothing`() {
		val reply = names(request("add:42:0:0", "Locked"))
		assertEquals("Not allowed", reply.message)
		assertEquals(9, reply.buttons.size)
		assertFailsWith<IllegalStateException> { names(request("add:42:8:0")) }
	}
}
