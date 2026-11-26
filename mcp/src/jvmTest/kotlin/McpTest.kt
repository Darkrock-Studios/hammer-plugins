import hammer.CommandRequest
import hammer.FakeHammerHost
import hammer.host
import java.io.File
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

class McpTest {
	init {
		host = FakeHammerHost(
			mapOf(
				"ops.list" to { _ ->
					buildJsonObject {
						put("operations", buildJsonArray {
							add(buildJsonObject {
								put("name", "project.list")
								put("description", "Lists projects.")
								put("access", "read")
								putJsonObject("input") { put("type", "object") }
							})
						})
					}
				},
				"project.list" to { _ -> buildJsonObject { put("projects", buildJsonArray {}) } },
			),
		)
	}

	private fun send(method: String, params: JsonObject? = null, version: String? = null): JsonObject {
		val withMeta = if (version == null) params else buildJsonObject {
			params?.forEach { (key, value) -> put(key, value) }
			putJsonObject("_meta") {
				put("io.modelcontextprotocol/protocolVersion", version)
				putJsonObject("io.modelcontextprotocol/clientCapabilities") {}
			}
		}
		val line = buildJsonObject {
			put("jsonrpc", "2.0")
			put("id", 1)
			put("method", method)
			if (withMeta != null) put("params", withMeta)
		}.toString()
		return Json.parseToJsonElement(mcpReply(CommandRequest("mcp", line = line))!!).jsonObject
	}

	private fun JsonObject.result() = getValue("result").jsonObject
	private fun JsonObject.errorCode() = getValue("error").jsonObject.getValue("code").jsonPrimitive.int
	private fun uriParams(uri: String) = buildJsonObject { put("uri", uri) }

	@Test
	fun legacyInitializeDeclaresSkills() {
		val result = send("initialize", buildJsonObject { put("protocolVersion", "2025-06-18") }).result()
		assertEquals("2025-06-18", result.getValue("protocolVersion").jsonPrimitive.content)
		val capabilities = result.getValue("capabilities").jsonObject
		assertTrue("resources" in capabilities)
		assertTrue("io.modelcontextprotocol/skills" in capabilities.getValue("extensions").jsonObject)
		assertNull(result["resultType"])
	}

	@Test
	fun initializeWithAnUnknownVersionGetsTheNewestLegacyOne() {
		val result = send("initialize", buildJsonObject { put("protocolVersion", "2026-07-28") }).result()
		assertEquals("2025-11-25", result.getValue("protocolVersion").jsonPrimitive.content)
	}

	@Test
	fun discoverListsVersionsAndTheExtension() {
		val result = send("server/discover", version = MODERN).result()
		assertEquals("complete", result.getValue("resultType").jsonPrimitive.content)
		assertEquals(MODERN, result.getValue("supportedVersions").jsonArray.first().jsonPrimitive.content)
		assertTrue("io.modelcontextprotocol/skills" in result.getValue("capabilities").jsonObject.getValue("extensions").jsonObject)
		assertTrue("ttlMs" in result && "cacheScope" in result)
		assertEquals(
			"hammer",
			result.getValue("_meta").jsonObject.getValue("io.modelcontextprotocol/serverInfo").jsonObject.getValue("name").jsonPrimitive.content,
		)
	}

	@Test
	fun anUnsupportedVersionIsRefusedWithTheSupportedOnes() {
		val error = send("tools/list", version = "1900-01-01").getValue("error").jsonObject
		assertEquals(-32022, error.getValue("code").jsonPrimitive.int)
		val data = error.getValue("data").jsonObject
		assertEquals("1900-01-01", data.getValue("requested").jsonPrimitive.content)
		assertTrue(data.getValue("supported").jsonArray.any { it.jsonPrimitive.content == MODERN })
	}

	@Test
	fun modernResultsAreCompleteAndCacheable() {
		val tools = send("tools/list", version = MODERN).result()
		assertEquals("complete", tools.getValue("resultType").jsonPrimitive.content)
		assertEquals("private", tools.getValue("cacheScope").jsonPrimitive.content)
		val call = send("tools/call", buildJsonObject { put("name", "project_list") }, MODERN).result()
		assertEquals("complete", call.getValue("resultType").jsonPrimitive.content)
		assertFalse(call.getValue("isError").jsonPrimitive.content.toBoolean())
	}

	@Test
	fun legacyResultsCarryNoModernFields() {
		val tools = send("tools/list").result()
		assertEquals(listOf("tools"), tools.keys.toList())
		val skills = send("skills/list").result()
		assertEquals(listOf("skills"), skills.keys.toList())
	}

	@Test
	fun skillsListHasEveryFolderWithItsDigestAndSize() {
		val skills = send("skills/list", version = MODERN).result().getValue("skills").jsonArray.map { it.jsonObject }
		val folders = File("skills").listFiles()!!.filter { File(it, "SKILL.md").isFile }.map { it.name }.sorted()
		assertEquals(4, folders.size)
		assertEquals(folders, skills.map { it.getValue("frontmatter").jsonObject.getValue("name").jsonPrimitive.content })
		for (skill in skills) {
			val name = skill.getValue("frontmatter").jsonObject.getValue("name").jsonPrimitive.content
			val uri = "skill://$name/SKILL.md"
			assertEquals(uri, skill.getValue("uri").jsonPrimitive.content)
			val bytes = File("skills/$name/SKILL.md").readBytes()
			val resource = skill.getValue("resources").jsonArray.single().jsonObject
			assertEquals(uri, resource.getValue("uri").jsonPrimitive.content)
			assertEquals(bytes.size, resource.getValue("size").jsonPrimitive.int)
			assertEquals("sha256:" + sha256(bytes), resource.getValue("digest").jsonPrimitive.content)
		}
	}

	@Test
	fun skillsGetMatchesTheListing() {
		val listed = send("skills/list", version = MODERN).result().getValue("skills").jsonArray.first().jsonObject
		val got = send("skills/get", uriParams(listed.getValue("uri").jsonPrimitive.content), MODERN).result()
		assertEquals(listed, got.getValue("skill").jsonObject)
		assertEquals("public", got.getValue("cacheScope").jsonPrimitive.content)
	}

	@Test
	fun skillsGetRefusesAnUnknownSkill() {
		assertEquals(-32602, send("skills/get", uriParams("skill://nope/SKILL.md"), MODERN).errorCode())
	}

	@Test
	fun resourcesReadServesTheFileAsWritten() {
		val bytes = File("skills/pov-check/SKILL.md").readBytes()
		val contents = send("resources/read", uriParams("skill://pov-check/SKILL.md"), MODERN).result()
			.getValue("contents").jsonArray.single().jsonObject
		assertEquals("text/markdown", contents.getValue("mimeType").jsonPrimitive.content)
		val text = contents.getValue("text").jsonPrimitive.content
		assertEquals(sha256(bytes), sha256(text.toByteArray()))
	}

	@Test
	fun resourcesReadOfAnUnknownUriIsNotFoundInEachEra() {
		assertEquals(-32602, send("resources/read", uriParams("skill://nope/SKILL.md"), MODERN).errorCode())
		assertEquals(-32002, send("resources/read", uriParams("skill://nope/SKILL.md")).errorCode())
	}

	@Test
	fun resourcesListNamesEachSkill() {
		val resources = send("resources/list").result().getValue("resources").jsonArray.map { it.jsonObject }
		val hammer = resources.single { it.getValue("uri").jsonPrimitive.content == "skill://hammer/SKILL.md" }
		assertEquals("hammer", hammer.getValue("name").jsonPrimitive.content)
		assertTrue(hammer.getValue("description").jsonPrimitive.content.isNotEmpty())
	}

	@Test
	fun frontmatterReadsPlainStrings() {
		assertEquals(
			mapOf("name" to "a-b", "description" to "Does things - well, \"mostly\"."),
			frontmatter("---\nname: a-b\ndescription: Does things - well, \"mostly\".\n---\n# Body\n"),
		)
	}

	@Test
	fun frontmatterRefusesWhatItCannotServeVerbatim() {
		for (line in listOf("description: \"quoted\"", "description: a: b", "description: yes", "description: 12", "metadata:", "  nested: x", "description: x # comment")) {
			assertFailsWith<IllegalArgumentException>(line) { frontmatter("---\nname: a\n$line\n---\n") }
		}
		assertFailsWith<IllegalArgumentException> { frontmatter("# No frontmatter\n") }
		assertFailsWith<IllegalArgumentException> { frontmatter("---\nname: a\n") }
	}

	private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

	private companion object {
		const val MODERN = "2026-07-28"
	}
}
