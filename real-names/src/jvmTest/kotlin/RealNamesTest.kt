import hammer.ActionRequest
import hammer.FakeHammerHost
import hammer.OperationException
import hammer.host
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

	private fun request(button: String? = null, project: String = "Storm", vararg input: Pair<String, JsonPrimitive>) =
		ActionRequest("names", project, input = JsonObject(input.toMap()), button = button)

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
		fun men(era: String) = generate(42, NamesInput(culture = "mexican", era = era, gender = "masculine", surnames = false))
		assertNull(nameList("mexican", "masculine-1930"))
		assertEquals(men("1940"), men("1930"))
		assertEquals(men("any"), men("1890"))
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
