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
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

// An MCP server over stdio (https://modelcontextprotocol.io): Hammer hands `command` each line of
// standard input, a JSON-RPC message, and writes back what it returns. Every operation this plugin is
// granted is offered as a tool, writes and deletes only when its settings allow. Its manifest asks for no
// operation that writes a scene's prose: Hammer's AI is a tool for the writer, not a replacement.

private val json = Json { ignoreUnknownKeys = true }

@Serializable
private class Settings(val changes: Boolean = false, val deletes: Boolean = false)

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
		put("instructions", "Hammer is an app for writing stories and the worlds they live in. This offers agents tools for interacting with this authors Hammer projects." +
				"You should have a love for literature, and the craft of writing." +
				"Importantly, this is meant as a tool for the author, not as a replacement. You are not a co-author, or creative partner. You are more like the StarTrek computer. Agents should never write prose for a scene." +
				"Agents should even demur from helping ideate: something as simple as picking a name for a character is part of the creative process." +
				"A writing tool such as this should never substitute for the author's own creativity." +
				"Start with project_list; most tools take a project name.")
	}
}

/** An operation offered as a tool. */
private class Tool(val name: String, val op: Operation) {
	fun toJson() = buildJsonObject {
		put("name", name)
		put("description", op.description)
		put("inputSchema", op.input)
		putJsonObject("annotations") {
			put("readOnlyHint", op.access == READ)
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
	return ops.filter { it.name != OPS_LIST && settings.allows(it.access) }
		.map { Tool(toolName(it.name), it) }
		.groupBy { it.name }
		.mapNotNull { (_, tools) -> tools.singleOrNull() }
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
private const val READ = "read"
private const val DESTRUCTIVE = "destructive"

private const val PARSE_ERROR = -32700
private const val INVALID_REQUEST = -32600
private const val METHOD_NOT_FOUND = -32601
private const val INVALID_PARAMS = -32602
private const val INTERNAL_ERROR = -32603

