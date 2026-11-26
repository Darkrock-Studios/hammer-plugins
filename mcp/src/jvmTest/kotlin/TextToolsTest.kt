import hammer.CommandRequest
import hammer.FakeHammerHost
import hammer.OperationException
import hammer.host
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

class TextToolsTest {
	/** The book, in story order: id, name, and markdown. */
	private val scenes = listOf(
		Triple(2, "Ashes on the Water", "The harbor bells rang *till* dawn.\n\nStill the harbormaster waited until noon."),
		Triple(4, "Left-Handed", "No bells here."),
		Triple(7, "The Registry", "*E.V. E.V. E.V.* Till then, the harbor slept. Teo’s harbor."),
	)
	private val readManyInputs = mutableListOf<JsonObject>()

	init {
		operations = null
		host = FakeHammerHost(
			mapOf(
				"ops.list" to { _ ->
					buildJsonObject {
						putJsonArray("operations") {
							add(op("ops.list", "read"))
							add(op("scene.read_many", "read"))
							// Named like the plugin's own tool, which wins.
							add(op("text.count", "read"))
							add(op("scene.delete", "destructive"))
						}
					}
				},
				"scene.read_many" to { input ->
					readManyInputs += input
					if (input["project"]?.jsonPrimitive?.content == "Stuck") {
						return@to buildJsonObject {
							putJsonArray("scenes") {}
							put("next", 5)
						}
					}
					if (input["project"]?.jsonPrimitive?.content != "Harbor") throw OperationException("NotFound", "No project named 'Bad'")
					val ids = input["ids"]?.jsonArray?.map { it.jsonPrimitive.int }
					val after = input["after"]?.jsonPrimitive?.int
					val wanted = scenes.filter { ids == null || it.first in ids }
					// A page of one scene, to exercise paging.
					val page = wanted.dropWhile { after != null && it.first != after }.drop(if (after == null) 0 else 1).take(1)
					val last = page.isEmpty() || page.single() == wanted.last()
					buildJsonObject {
						putJsonArray("scenes") {
							page.forEach { (id, name, markdown) ->
								add(buildJsonObject {
									put("id", id)
									put("name", name)
									putJsonArray("path") { add(JsonPrimitive("Chapter 1")) }
									put("markdown", markdown)
									putJsonObject("meta") { put("outline", "harbor harbor") }
								})
							}
						}
						put("next", if (last) JsonNull else JsonPrimitive(page.single().first))
					}
				},
			),
		)
	}

	private fun op(name: String, access: String) = buildJsonObject {
		put("name", name)
		put("description", "Host $name")
		put("access", access)
		putJsonObject("input") { put("type", "object") }
	}

	private fun rpc(method: String, params: JsonObject = JsonObject(emptyMap())): JsonObject {
		val line = buildJsonObject {
			put("jsonrpc", "2.0")
			put("id", 1)
			put("method", method)
			put("params", params)
		}.toString()
		return Json.parseToJsonElement(mcpReply(CommandRequest("mcp", line = line))!!).jsonObject
	}

	private fun tools(): List<JsonObject> = rpc("tools/list")["result"]!!.jsonObject["tools"]!!.jsonArray.map { it.jsonObject }

	/** The tool's result: its text parsed when it succeeded, and whether it was an error. */
	private fun call(name: String, arguments: JsonObject): Pair<JsonElement, Boolean> {
		val result = rpc("tools/call", buildJsonObject {
			put("name", name)
			put("arguments", arguments)
		})["result"]!!.jsonObject
		val text = result["content"]!!.jsonArray.single().jsonObject["text"]!!.jsonPrimitive.content
		val isError = result["isError"]!!.jsonPrimitive.boolean
		return (if (isError) JsonPrimitive(text) else Json.parseToJsonElement(text)) to isError
	}

	private fun args(vararg pairs: Pair<String, Any>) = buildJsonObject {
		put("project", "Harbor")
		pairs.forEach { (key, value) ->
			when (value) {
				is String -> put(key, value)
				is Int -> put(key, value)
				is Double -> put(key, value)
				is Boolean -> put(key, value)
				is List<*> -> put(key, JsonArray(value.map { if (it is Int) JsonPrimitive(it) else JsonPrimitive(it as String) }))
			}
		}
	}

	@Test
	fun `the text tools are listed as read tools, and win a name collision`() {
		val tools = tools()
		assertEquals(listOf("scene_read_many", "text_count", "text_find"), tools.map { it["name"]!!.jsonPrimitive.content })
		val count = tools.single { it["name"]!!.jsonPrimitive.content == "text_count" }
		assertTrue(count["description"]!!.jsonPrimitive.content.startsWith("Exact count"))
		val annotations = count["annotations"]!!.jsonObject
		assertEquals(true, annotations["readOnlyHint"]!!.jsonPrimitive.boolean)
		assertEquals(false, annotations["destructiveHint"]!!.jsonPrimitive.boolean)
		assertEquals(false, annotations["openWorldHint"]!!.jsonPrimitive.boolean)
		val schema = count["inputSchema"]!!.jsonObject
		assertEquals(listOf("project", "terms"), schema["required"]!!.jsonArray.map { it.jsonPrimitive.content })
	}

	@Test
	fun `text_count counts each term in every scene, page by page`() {
		val (result, isError) = call("text_count", args("terms" to listOf("harbor", "till", "E.V.", "Teo's", "absent")))
		assertTrue(!isError, result.toString())
		val output = result.jsonObject
		assertEquals(3, output["scenesScanned"]!!.jsonPrimitive.int)
		val terms = output["terms"]!!.jsonArray.map { it.jsonObject }
		assertEquals(listOf("harbor", "till", "E.V.", "Teo's", "absent"), terms.map { it["term"]!!.jsonPrimitive.content })
		assertEquals(listOf(3, 2, 3, 1, 0), terms.map { it["total"]!!.jsonPrimitive.int })
		val harbor = terms[0]["scenes"]!!.jsonArray.map { it.jsonObject }
		assertEquals(listOf(2 to 1, 7 to 2), harbor.map { it["id"]!!.jsonPrimitive.int to it["count"]!!.jsonPrimitive.int })
		assertEquals("Ashes on the Water", harbor[0]["name"]!!.jsonPrimitive.content)
		assertEquals(0, terms[4]["scenes"]!!.jsonArray.size)
		// Three pages of one scene each, after the first passing back the last page's next.
		assertEquals(listOf(null, 2, 4), readManyInputs.map { it["after"]?.jsonPrimitive?.intOrNull })
	}

	@Test
	fun `text_count honours case and whole words, and narrows by ids`() {
		val (strict, _) = call("text_count", args("terms" to listOf("Till"), "caseSensitive" to true))
		assertEquals(1, strict.jsonObject["terms"]!!.jsonArray[0].jsonObject["total"]!!.jsonPrimitive.int)
		val (loose, _) = call("text_count", args("terms" to listOf("till"), "wholeWord" to false))
		assertEquals(3, loose.jsonObject["terms"]!!.jsonArray[0].jsonObject["total"]!!.jsonPrimitive.int)
		val (narrow, _) = call("text_count", args("terms" to listOf("harbor"), "ids" to listOf(7)))
		assertEquals(1, narrow.jsonObject["scenesScanned"]!!.jsonPrimitive.int)
		assertEquals(listOf(7), readManyInputs.last()["ids"]!!.jsonArray.map { it.jsonPrimitive.int })
	}

	@Test
	fun `text_find pages through matches with the full total`() {
		val (first, _) = call("text_find", args("term" to "harbor", "limit" to 2))
		val output = first.jsonObject
		assertEquals(3, output["total"]!!.jsonPrimitive.int)
		assertEquals(2, output["next"]!!.jsonPrimitive.int)
		val matches = output["matches"]!!.jsonArray.map { it.jsonObject }
		assertEquals(listOf(2, 7), matches.map { it["id"]!!.jsonPrimitive.int })
		assertEquals("The harbor bells rang till dawn.", matches[0]["context"]!!.jsonPrimitive.content)
		assertEquals("The Registry", matches[1]["name"]!!.jsonPrimitive.content)

		val (second, _) = call("text_find", args("term" to "harbor", "limit" to 2, "offset" to 2))
		assertEquals(3, second.jsonObject["total"]!!.jsonPrimitive.int)
		assertEquals(JsonNull, second.jsonObject["next"])
		val last = second.jsonObject["matches"]!!.jsonArray.single().jsonObject
		assertEquals("E.V. E.V. E.V. Till then, the harbor slept. Teo's harbor.", last["context"]!!.jsonPrimitive.content)
	}

	@Test
	fun `bad arguments are tool errors with a plain message`() {
		assertEquals(JsonPrimitive("terms[1] is blank") to true, call("text_count", args("terms" to listOf("bell", " "))))
		assertEquals(JsonPrimitive("terms[0] is blank") to true, call("text_count", args("terms" to listOf("**"))))
		assertEquals(JsonPrimitive("terms must hold 1 to 50 terms") to true, call("text_count", args("terms" to emptyList<String>())))
		assertEquals(JsonPrimitive("terms must hold 1 to 50 terms") to true, call("text_count", args("terms" to List(51) { "a" })))
		assertEquals(JsonPrimitive("terms must be a list of strings") to true, call("text_count", args("terms" to "bell")))
		assertEquals(JsonPrimitive("term is blank") to true, call("text_find", args("term" to "")))
		assertEquals(JsonPrimitive("term must be a string") to true, call("text_find", args()))
		assertEquals(JsonPrimitive("limit must be a whole number from 1 to 200") to true, call("text_find", args("term" to "a", "limit" to 201)))
		assertEquals(JsonPrimitive("limit must be a whole number from 1 to 200") to true, call("text_find", args("term" to "a", "limit" to 0)))
		assertEquals(JsonPrimitive("offset must be a whole number from 0") to true, call("text_find", args("term" to "a", "offset" to -1)))
		assertEquals(JsonPrimitive("wholeWord must be true or false") to true, call("text_find", args("term" to "a", "wholeWord" to 3)))
		assertEquals(JsonPrimitive("project must be a string") to true, call("text_find", buildJsonObject { put("term", "a") }))
		// Nothing was read for any of them.
		assertTrue(readManyInputs.isEmpty())
	}

	@Test
	fun `a cursor that does not move is a tool error`() {
		val (result, isError) = call("text_count", buildJsonObject {
			put("project", "Stuck")
			putJsonArray("terms") { add(JsonPrimitive("harbor")) }
		})
		assertTrue(isError)
		assertEquals("scene.read_many returned the same page twice", result.jsonPrimitive.content)
	}

	@Test
	fun `a whole number written as a decimal is taken`() {
		val (result, isError) = call("text_find", args("term" to "harbor", "limit" to 1.0, "offset" to 0.0))
		assertTrue(!isError)
		assertEquals(1, result.jsonObject["matches"]!!.jsonArray.size)
		assertTrue(call("text_find", args("term" to "harbor", "limit" to 1.5)).second)
	}

	@Test
	fun `the host's errors are tool errors with its message`() {
		val arguments = buildJsonObject {
			put("project", "Bad")
			put("term", "harbor")
		}
		assertEquals(JsonPrimitive("No project named 'Bad'") to true, call("text_find", arguments))
	}

	@Test
	fun `an unknown tool is still an error`() {
		val reply = rpc("tools/call", buildJsonObject { put("name", "text_nope") })
		assertNull(reply["result"])
		assertEquals("Unknown tool 'text_nope'", reply["error"]!!.jsonObject["message"]!!.jsonPrimitive.content)
	}
}
