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
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

// An MCP server over stdio (https://modelcontextprotocol.io): Hammer hands `command` each line of
// standard input, a JSON-RPC message, and writes back what it returns. Every operation this plugin is
// granted is offered as a tool.

private val json = Json { ignoreUnknownKeys = true }

@Serializable
private class Settings(val liveEdits: Boolean = false, val deletes: Boolean = false)

@Serializable
private class OperationList(val operations: List<Operation>)

@Serializable
private class Operation(val name: String, val description: String, val access: String, val input: JsonObject)

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
	return try {
		when (method) {
			"initialize" -> result(id, initialize(params))
			"ping" -> result(id, JsonObject(emptyMap()))
			"tools/list" -> result(id, buildJsonObject { put("tools", JsonArray(tools(settings).map { it.toJson() })) })
			"tools/call" -> result(id, call(params, settings))
			else -> error(id, METHOD_NOT_FOUND, "Unknown method '$method'")
		}
	} catch (e: RpcException) {
		error(id, e.code, e.message.orEmpty())
	} catch (e: Exception) {
		// A request always gets an answer, or the client waits on it forever.
		error(id, INTERNAL_ERROR, e.message ?: "Internal error")
	}
}

private fun initialize(params: JsonObject): JsonObject {
	val requested = (params["protocolVersion"] as? JsonPrimitive)?.contentOrNull
	return buildJsonObject {
		put("protocolVersion", requested?.takeIf { it in PROTOCOL_VERSIONS } ?: PROTOCOL_VERSIONS.first())
		putJsonObject("capabilities") { putJsonObject("tools") { put("listChanged", false) } }
		putJsonObject("serverInfo") {
			put("name", "hammer")
			put("version", SERVER_VERSION)
		}
		put("instructions", "Tools for the writer's Hammer projects. Start with project_list; most tools take a project name.")
	}
}

/** An operation offered as a tool, and the values of its input fields it refuses. */
private class Tool(val name: String, val op: Operation, val input: JsonObject, val refused: Map<String, Set<JsonElement>>) {
	fun toJson() = buildJsonObject {
		put("name", name)
		put("description", op.description)
		put("inputSchema", input)
		putJsonObject("annotations") {
			put("readOnlyHint", op.access == "read")
			put("destructiveHint", op.access == DESTRUCTIVE)
			put("openWorldHint", false)
		}
	}
}

// Operations do not change while the module is loaded, so they are fetched once.
private var operations: List<Operation>? = null

private fun tools(settings: Settings): List<Tool> {
	val ops = operations ?: json.decodeFromJsonElement(OperationList.serializer(), callJson(OPS_LIST, JsonObject(emptyMap())))
		.operations.also { operations = it }
	// tools/list already describes every tool, filtered by the settings, which ops.list is not.
	return ops.filter { it.name != OPS_LIST && (settings.deletes || it.access != DESTRUCTIVE) }.mapNotNull { if (settings.liveEdits) Tool(toolName(it.name), it, it.input, emptyMap()) else withoutLiveEdits(it) }
		.groupBy { it.name }
		.mapNotNull { (_, tools) -> tools.singleOrNull() }
}

/**
 * [op] without its live edits: null when its input, or a field's object, is one; otherwise with the
 * live values of its enum fields dropped from its schema and refused when called. Only top-level
 * fields are looked at.
 */
private fun withoutLiveEdits(op: Operation): Tool? {
	if (op.input[LIVE_EDIT_KEY] == JsonPrimitive(true)) return null
	val name = toolName(op.name)
	val properties = op.input["properties"] as? JsonObject ?: return Tool(name, op, op.input, emptyMap())
	val markers = properties.mapNotNull { (field, schema) -> schema.jsonObject[LIVE_EDIT_KEY]?.let { field to it } }.toMap()
	if (markers.values.any { it !is JsonArray }) return null
	val refused = markers.mapValues { (_, values) -> (values as JsonArray).toSet() }
	if (refused.isEmpty()) return Tool(name, op, op.input, emptyMap())

	val narrowed = properties.mapValues { (field, schema) ->
		val live = refused[field] ?: return@mapValues schema
		val allowed = (schema.jsonObject["enum"] as JsonArray).filter { it !in live }
		JsonObject(schema.jsonObject - LIVE_EDIT_KEY + ("enum" to JsonArray(allowed)))
	}
	val note = refused.keys.joinToString(" ") { field ->
		"Live edits are off, so $field can only be ${(narrowed.getValue(field).jsonObject["enum"] as JsonArray).joinToString(" or ")}."
	}
	val described = Operation(op.name, "${op.description} $note", op.access, op.input)
	return Tool(name, described, JsonObject(op.input + ("properties" to JsonObject(narrowed))), refused)
}

private fun call(params: JsonObject, settings: Settings): JsonObject {
	val name = (params["name"] as? JsonPrimitive)?.contentOrNull ?: throw RpcException(INVALID_PARAMS, "No tool name")
	val tool = tools(settings).firstOrNull { it.name == name } ?: throw RpcException(INVALID_PARAMS, "Unknown tool '$name'")
	val arguments = params["arguments"] as? JsonObject ?: JsonObject(emptyMap())
	tool.refused.entries.firstOrNull { (field, values) -> arguments[field] in values }?.let { (field, _) ->
		return toolResult("Live edits are off in Hammer's settings, so $field cannot be ${arguments[field]}.", isError = true)
	}
	return try {
		toolResult(callJsonText(tool.op.name, arguments), isError = false)
	} catch (e: OperationException) {
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

private class RpcException(val code: Int, message: String) : Exception(message)

private fun result(id: JsonElement, result: JsonObject) = buildJsonObject {
	put("jsonrpc", "2.0")
	put("id", id)
	put("result", result)
}

private fun error(id: JsonElement, code: Int, message: String) = buildJsonObject {
	put("jsonrpc", "2.0")
	put("id", id)
	putJsonObject("error") {
		put("code", code)
		put("message", message)
	}
}

/** Operation names use dots, which some MCP clients reject in tool names. */
private fun toolName(operation: String): String = operation.replace('.', '_')

/** Newest first; an unknown version the client asks for gets the newest. */
private val PROTOCOL_VERSIONS = listOf("2025-06-18", "2025-03-26", "2024-11-05")
private const val SERVER_VERSION = "1"

private const val OPS_LIST = "ops.list"
private const val DESTRUCTIVE = "destructive"

/** Marks input that changes a scene's text in place: see hammer-editor's `@LiveEdit`. */
private const val LIVE_EDIT_KEY = "x-hammer-live"

private const val PARSE_ERROR = -32700
private const val INVALID_REQUEST = -32600
private const val METHOD_NOT_FOUND = -32601
private const val INVALID_PARAMS = -32602
private const val INTERNAL_ERROR = -32603

