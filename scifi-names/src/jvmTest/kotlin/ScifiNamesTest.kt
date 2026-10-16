import hammer.ActionRequest
import hammer.FakeHammerHost
import hammer.OperationException
import hammer.host
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive

class ScifiNamesTest {
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

	private fun request(action: String, style: String, button: String? = null, project: String = "Storm", vararg input: Pair<String, JsonPrimitive>) =
		ActionRequest(action, project, input = JsonObject(mapOf("style" to JsonPrimitive(style)) + input), button = button)

	/** Each action of the manifest with its styles. */
	private val styles = java.io.File("manifest.toml").readText().split("[[actions]]").drop(1).associate { action ->
		Regex("name = \"(\\w+)\"").find(action)!!.groupValues[1] to
			Regex("value = \"([\\w-]+)\"").findAll(action).map { it.groupValues[1] }.toList()
	}

	@Test
	fun `a seed gives the same names again`() {
		val names = generate(42, "characters", NamesInput("harsh"))
		assertEquals(names, generate(42, "characters", NamesInput("harsh")))
		assertNotEquals(names, generate(43, "characters", NamesInput("harsh")))
		assertEquals(8, names.size)
		assertEquals(MAX_NAMES, generate(1, "characters", NamesInput("harsh", 99)).size)
		assertFailsWith<IllegalStateException> { generate(42, "characters", NamesInput("atlantean")) }
		assertFailsWith<IllegalStateException> { generate(42, "characters", NamesInput("../things/naval")) }
		assertFailsWith<IllegalStateException> { generate(42, "robots", NamesInput("harsh")) }
	}

	@Test
	fun `every style of every action in the manifest gives names, no two alike`() {
		assertEquals(KINDS.keys, styles.keys)
		assertEquals(listOf(6, 3, 4), styles.values.map { it.size })
		for ((kind, list) in styles) for (style in list) for (seed in 1L..5) {
			val names = generate(seed, kind, NamesInput(style, 20))
			assertTrue(names.size == 20 && names.toSet().size == 20 && names.all { it[0].isUpperCase() && it.trim() == it }, "$kind $style: $names")
		}
	}

	@Test
	fun `an alien's names are made up, with a second unless asked not to`() {
		val real = lines("characters", "clipped", "names")!!
		val names = generate(3, "characters", NamesInput("clipped", 20))
		assertTrue(names.all { it.split(' ').let { parts -> parts.size == 2 && parts.none(real::contains) } }, "$names")
		assertTrue(names.all { '\'' in it })
		assertTrue(generate(3, "characters", NamesInput("clipped", 20, long = false)).none { ' ' in it || it in real })
	}

	@Test
	fun `a world's name sounds like a star's without being one, or follows a pattern`() {
		val real = lines("worlds", "stars", "names")!!
		assertTrue(real.size > 500 && "Aldebaran" in real && "Titan" in real)
		assertTrue((1L..10).flatMap { generate(it, "worlds", NamesInput("stars", 20)) }.none { ' ' in it || it in real })
		val settled = generate(4, "worlds", NamesInput("settled", 20))
		assertTrue(settled.all { name -> ' ' in name && name.split(' ').none { it.removeSuffix("'s") in real } }, "$settled")
		val catalogue = generate(4, "worlds", NamesInput("catalogue", 20))
		assertTrue(catalogue.all { it.matches(Regex("(\\p{Lu}\\p{Ll}+|[A-Z]{2,3})[- ][1-9]\\d+([b-g]|\\.\\d\\d)")) }, "$catalogue")
		assertTrue(catalogue.none { it.substringBefore('-').substringBefore(' ') in lines("worlds", "catalogue", "surveyors")!! })
	}

	@Test
	fun `a ship's name is made of the style's words, after a prefix unless asked not to`() {
		val prefixes = lines("things", "naval", "prefixes")!!
		val virtues = lines("things", "naval", "virtues")!!
		val names = generate(6, "things", NamesInput("naval", 20))
		assertTrue(names.all { it.substringBefore(' ') in prefixes })
		assertTrue(names.any { it.substringAfter(' ') in virtues })
		val bare = generate(6, "things", NamesInput("naval", 20, long = false))
		assertTrue(bare.none { it.substringBefore(' ') in prefixes } && bare.any { it in virtues })
		assertTrue(generate(6, "things", NamesInput("lyrical", 20, long = false)).any { " of " in it })
	}

	@Test
	fun `a handle, a designation, and a company each have a long form`() {
		fun names(kind: String, style: String, long: Boolean) = (1L..5).flatMap { generate(it, kind, NamesInput(style, 20, long)) }
		assertTrue(names("characters", "handles", false).all { it.matches(Regex("[A-Z][a-z]+(-?[a-z]+|_?\\d+)?")) })
		assertTrue(names("characters", "handles", true).all { it.matches(Regex("\\p{L}+ \"[^\" ]+\" \\p{L}+")) })
		val classes = lines("characters", "designations", "classes")!!
		assertTrue(names("characters", "designations", false).none { name -> classes.any(name::startsWith) })
		assertTrue(names("characters", "designations", true).all { name -> classes.any { name.startsWith("$it ") } })
		val roots = lines("things", "corporations", "roots")!!
		val suffixes = lines("things", "corporations", "suffixes")!!
		assertTrue(names("things", "corporations", false).none { it in roots || suffixes.any(it::endsWith) })
		assertTrue(names("things", "corporations", true).all { name -> suffixes.any { name.endsWith(" $it") } })
	}

	@Test
	fun `the first reply has a button for each name, and one for more`() {
		val reply = names(request("worlds", "stars"))
		assertEquals(9, reply.buttons.size)
		assertTrue(reply.buttons.last().let { it.id == "more" && it.footer })
		assertTrue(reply.buttons.first().id.matches(Regex("add:-?\\d+:0:0")))
		assertTrue(reply.markdown!!.contains("- ${reply.buttons.first().label}\n"))
		assertTrue(reply.markdown!!.contains("as a place"))
	}

	@Test
	fun `pressing a name adds it as its kind of entry and marks it`() {
		for ((kind, type) in KINDS) {
			val style = styles.getValue(kind).first()
			val names = generate(42, kind, NamesInput(style, 2))
			val reply = names(request(kind, style, "add:42:1:0", "Storm", "count" to JsonPrimitive(2)))
			assertEquals("Added ${names[1]} to the encyclopedia", reply.message)
			assertEquals(listOf("Storm", type, names[1]), listOf("project", "type", "name").map { created.last()[it]!!.jsonPrimitive.content })
			assertTrue(reply.markdown!!.contains("- ${names[1]} (added)"))
			assertEquals(listOf("add:42:0:2", "more"), reply.buttons.map { it.id })
		}
	}

	@Test
	fun `a refused entry says why and marks nothing`() {
		val reply = names(request("things", "naval", "add:42:0:0", "Locked"))
		assertEquals("Not allowed", reply.message)
		assertEquals(9, reply.buttons.size)
		assertFailsWith<IllegalStateException> { names(request("things", "naval", "add:42:8:0")) }
		assertFailsWith<IllegalStateException> { names(request("robots", "naval")) }
	}
}
