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
	fun `an era without lists of its own gives the names of all years`() {
		assertEquals(generate(42, NamesInput()), generate(42, NamesInput(era = "1700")))
		assertFailsWith<IllegalStateException> { generate(42, NamesInput(culture = "atlantean")) }
	}

	@Test
	fun `surnames are written as they usually are`() {
		val surnames = nameList("american", "surnames")!!
		assertTrue(listOf("O'Brien", "McDonald", "De La Cruz", "St. John").all { it in surnames })
		assertTrue("Mcdonald" !in surnames)
	}

	@Test
	fun `a name is picked as often as it is common`() {
		val list = NameList(listOf("Rare\t1", "Mary Ann\t99", ""))
		val random = Random(5)
		val rare = (1..1000).count { list.pick(random) == "Rare" }
		assertTrue(rare in 1..39)
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
