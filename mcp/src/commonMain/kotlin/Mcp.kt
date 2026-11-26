import hammer.CommandRequest
import hammer.OperationException
import hammer.callJson
import hammer.callJsonText
import hammer.settings
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

// An MCP server over stdio (https://modelcontextprotocol.io): Hammer hands `command` each line of
// standard input, a JSON-RPC message, and writes back what it returns. Every operation this plugin is
// granted is offered as a tool, writes and deletes only when its settings allow, along with the read
// tools in TextTools.kt that it computes itself. Its manifest asks for no operation that writes a scene's
// prose: Hammer's AI is a tool for the writer, not a replacement. It also serves the skills in skills/
// (see Skills.kt). It speaks both the legacy revisions, which open with initialize, and the modern ones,
// which carry their version on every request; each request is answered by its own.

private val json = Json { ignoreUnknownKeys = true }

@Serializable
private class Settings(val changes: Boolean = false, val deletes: Boolean = false)

@Serializable
private class OperationList(val operations: List<Operation>)

@Serializable
internal class Operation(val name: String, val description: String, val access: String, val input: JsonObject)

/** The line to answer [request] with, or null when it needs none. */
fun mcpReply(request: CommandRequest): String? = handle(request.line, request.settings<Settings>())?.toString()

/** The reply to [line], or null when it needs none, as for a notification. */
private fun handle(line: String, settings: Settings): JsonObject? {
	val message = try {
		json.parseToJsonElement(line)
	} catch (e: SerializationException) {
		return error(JsonNull, PARSE_ERROR, "Parse error")
	} as? JsonObject ?: return error(JsonNull, INVALID_REQUEST, "Expected a JSON-RPC object")

	// A notification has no id, and the client's replies have no method; neither is answered.
	val id = message["id"] ?: return null
	val method = (message["method"] as? JsonPrimitive)?.contentOrNull ?: return null
	val params = message["params"] as? JsonObject ?: JsonObject(emptyMap())
	// A modern request names its protocol version in each request's _meta; a legacy client names it once,
	// in initialize, and never again.
	val version = ((params["_meta"] as? JsonObject)?.get(VERSION_KEY) as? JsonPrimitive)?.contentOrNull
	if (version != null && version !in MODERN_VERSIONS && version !in LEGACY_VERSIONS) {
		return error(id, UNSUPPORTED_PROTOCOL_VERSION, "Unsupported protocol version", buildJsonObject {
			put("supported", JsonArray((MODERN_VERSIONS + LEGACY_VERSIONS).map(::JsonPrimitive)))
			put("requested", version)
		})
	}
	val modern = version in MODERN_VERSIONS
	return try {
		val reply = when (method) {
			"initialize" -> initialize(params)
			"server/discover" -> discover()
			"ping" -> JsonObject(emptyMap())
			"tools/list" -> cacheable(TOOLS_TTL, PRIVATE) { put("tools", JsonArray(tools(settings).map { it.toJson() })) }
			"tools/call" -> call(params, settings)
			"resources/list" -> cacheable(SKILLS_TTL, PUBLIC) { put("resources", JsonArray(SKILLS.map { it.resource() })) }
			"resources/templates/list" -> cacheable(SKILLS_TTL, PUBLIC) { put("resourceTemplates", JsonArray(emptyList())) }
			"resources/read" -> {
				val skill = skill(uri(params)) ?: throw RpcException(
					if (modern) INVALID_PARAMS else RESOURCE_NOT_FOUND, "Resource not found", buildJsonObject { put("uri", uri(params)) })
				cacheable(SKILLS_TTL, PUBLIC) { put("contents", buildJsonArray { add(skill.contents()) }) }
			}
			"skills/list" -> cacheable(SKILLS_TTL, PUBLIC) { put("skills", JsonArray(SKILLS.map { it.toJson() })) }
			"skills/get" -> {
				val skill = skill(uri(params)) ?: throw RpcException(INVALID_PARAMS, "No skill at '${uri(params)}'")
				cacheable(SKILLS_TTL, PUBLIC) { put("skill", skill.toJson()) }
			}
			else -> throw RpcException(METHOD_NOT_FOUND, "Unknown method '$method'")
		}
		result(id, if (modern) modernResult(reply) else legacyResult(reply))
	} catch (e: RpcException) {
		error(id, e.code, e.message.orEmpty(), e.data)
	} catch (e: Exception) {
		// A request always gets an answer, or the client waits on it forever.
		error(id, INTERNAL_ERROR, e.message ?: "Internal error")
	}
}

private fun uri(params: JsonObject): String =
	(params["uri"] as? JsonPrimitive)?.contentOrNull ?: throw RpcException(INVALID_PARAMS, "No uri")

/** A result clients may cache for [ttlMs], shared with others when [scope] is public. */
private fun cacheable(ttlMs: Long, scope: String, body: JsonObjectBuilder.() -> Unit) = buildJsonObject {
	body()
	put(TTL, ttlMs)
	put(CACHE_SCOPE, scope)
}

/** Every modern result says it is complete, and who answered it. */
private fun modernResult(reply: JsonObject) = buildJsonObject {
	reply.forEach { (key, value) -> put(key, value) }
	put("resultType", "complete")
	putJsonObject("_meta") { put(SERVER_INFO_KEY, serverInfo()) }
}

/** Caching hints are modern; a legacy client gets results as their revision defines them. */
private fun legacyResult(reply: JsonObject) = JsonObject(reply - TTL - CACHE_SCOPE)

private fun initialize(params: JsonObject): JsonObject {
	val requested = (params["protocolVersion"] as? JsonPrimitive)?.contentOrNull
	return buildJsonObject {
		put("protocolVersion", requested?.takeIf { it in LEGACY_VERSIONS } ?: LEGACY_VERSIONS.first())
		put("capabilities", capabilities())
		put("serverInfo", serverInfo())
		put("instructions", INSTRUCTIONS)
	}
}

private fun discover() = cacheable(SKILLS_TTL, PUBLIC) {
	put("supportedVersions", JsonArray((MODERN_VERSIONS + LEGACY_VERSIONS).map(::JsonPrimitive)))
	put("capabilities", capabilities())
	put("instructions", INSTRUCTIONS)
}

private fun capabilities() = buildJsonObject {
	putJsonObject("tools") { put("listChanged", false) }
	putJsonObject("resources") {}
	putJsonObject("extensions") { putJsonObject(SKILLS_EXTENSION) {} }
}

private fun serverInfo() = buildJsonObject {
	put("name", "hammer")
	put("version", SERVER_VERSION)
}

/** The skill [INSTRUCTIONS] sends agents to, which spells out its guidelines. */
internal const val HAMMER_SKILL = "skill://hammer/SKILL.md"

private const val INSTRUCTIONS = "Hammer is an app for writing stories and the worlds they live in. This offers agents tools for interacting with this authors Hammer projects. " +
	"You should have a love for literature, and the craft of writing. " +
	"Importantly, this is meant as a tool for the author, not as a replacement. You are not a co-author, or creative partner. You are more like the StarTrek computer. Agents should never write prose for a scene. " +
	"Agents should even demur from helping ideate: something as simple as picking a name for a character is part of the creative process. " +
	"Don't offer suggestions or lists of options either; leave the choice to the writer, even when the writer asks you directly. " +
	"A writing tool such as this should never substitute for the author's own creativity. " +
	"Before using the tools, read the hammer skill ($HAMMER_SKILL). " +
	"Start with project_list; most tools take a project name."

/**
 * A tool, which [run] answers with the text of its result. A tool that writes carries Hammer's stance in
 * its description too: some clients never show the model the server's instructions, and a model weighs
 * a tool's description as it decides to call it. Read and delete tools go without, since the tool list
 * is sent with every message and small local models have little room.
 */
internal class Tool(
	val name: String,
	val description: String,
	val access: String,
	val input: JsonObject,
	val run: (JsonObject) -> String,
) {
	fun toJson() = buildJsonObject {
		put("name", name)
		put("description", if (access == WRITE) "$description $WRITER_DECIDES" else description)
		put("inputSchema", input)
		putJsonObject("annotations") {
			put("readOnlyHint", access == READ)
			put("destructiveHint", access == DESTRUCTIVE)
			put("openWorldHint", false)
		}
	}
}

// Operations do not change while the module is loaded, so they are fetched once. Tests, which each fake
// their own host, clear it.
internal var operations: List<Operation>? = null

private fun tools(settings: Settings): List<Tool> {
	val ops = operations ?: json.decodeFromJsonElement(OperationList.serializer(), callJson(OPS_LIST, JsonObject(emptyMap())))
		.operations.also { operations = it }
	// tools/list already describes every tool, filtered by the settings, which ops.list is not. Two
	// operations whose tool names collide are both left out; an operation named like one of the plugin's
	// own tools gives way to it.
	val own = textTools.map { it.name }.toSet()
	return ops.filter { it.name != OPS_LIST && settings.allows(it.access) }
		.map { op -> Tool(toolName(op.name), op.description, op.access, op.input) { callJsonText(op.name, it) } }
		.filter { it.name !in own }
		.groupBy { it.name }
		.mapNotNull { (_, tools) -> tools.singleOrNull() }
		.plus(textTools.filter { settings.allows(it.access) })
}

private fun Settings.allows(access: String): Boolean = when (access) {
	READ -> true
	DESTRUCTIVE -> deletes
	else -> changes
}

private fun call(params: JsonObject, settings: Settings): JsonObject {
	val name = (params["name"] as? JsonPrimitive)?.contentOrNull ?: throw RpcException(INVALID_PARAMS, "No tool name")
	val tool = tools(settings).firstOrNull { it.name == name } ?: throw RpcException(INVALID_PARAMS, "Unknown tool '$name'")
	val arguments = params["arguments"] as? JsonObject ?: JsonObject(emptyMap())
	return try {
		toolResult(tool.run(arguments), isError = false)
	} catch (e: OperationException) {
		toolResult(e.message.orEmpty(), isError = true)
	} catch (e: ToolInputException) {
		toolResult(e.message.orEmpty(), isError = true)
	}
}

private fun toolResult(text: String, isError: Boolean) = buildJsonObject {
	put("content", buildJsonArray {
		add(buildJsonObject {
			put("type", "text")
			put("text", text)
		})
	})
	put("isError", isError)
}

private class RpcException(val code: Int, message: String, val data: JsonObject? = null) : Exception(message)

private fun result(id: JsonElement, result: JsonObject) = buildJsonObject {
	put("jsonrpc", "2.0")
	put("id", id)
	put("result", result)
}

private fun error(id: JsonElement, code: Int, message: String, data: JsonObject? = null) = buildJsonObject {
	put("jsonrpc", "2.0")
	put("id", id)
	putJsonObject("error") {
		put("code", code)
		put("message", message)
		if (data != null) put("data", data)
	}
}

/** Operation names use dots, which some MCP clients reject in tool names. */
private fun toolName(operation: String): String = operation.replace('.', '_')

/** Revisions without the initialize handshake, which carry their version on every request. */
private val MODERN_VERSIONS = listOf("2026-07-28")

/** Newest first; an initialize asking for a version not here gets the newest. */
private val LEGACY_VERSIONS = listOf("2025-11-25", "2025-06-18", "2025-03-26", "2024-11-05")
private const val SERVER_VERSION = "1"

private const val VERSION_KEY = "io.modelcontextprotocol/protocolVersion"
private const val SERVER_INFO_KEY = "io.modelcontextprotocol/serverInfo"
private const val SKILLS_EXTENSION = "io.modelcontextprotocol/skills"
private const val TTL = "ttlMs"
private const val CACHE_SCOPE = "cacheScope"
private const val PUBLIC = "public"
private const val PRIVATE = "private"

// Skills and the server's capabilities only change with the plugin; tools change with its settings.
private const val SKILLS_TTL = 3_600_000L
private const val TOOLS_TTL = 60_000L

private const val OPS_LIST = "ops.list"
internal const val READ = "read"
private const val WRITE = "write"
private const val DESTRUCTIVE = "destructive"

private const val WRITER_DECIDES = "Record only what the writer decided. Never invent or suggest names or other story " +
	"content, even if asked to choose."

private const val PARSE_ERROR = -32700
private const val INVALID_REQUEST = -32600
private const val METHOD_NOT_FOUND = -32601
private const val INVALID_PARAMS = -32602
private const val INTERNAL_ERROR = -32603
private const val RESOURCE_NOT_FOUND = -32002
private const val UNSUPPORTED_PROTOCOL_VERSION = -32022

