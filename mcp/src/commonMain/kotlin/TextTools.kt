import hammer.callJsonText
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

// Tools the plugin computes itself, from scene.read.many, on a Hammer with no text.count and text.find of
// its own: search finds a scene once however often a term is in it, ignores case, and matches inside
// longer words and in titles.

private val json = Json { ignoreUnknownKeys = true }

@Serializable
private class ScenePage(val scenes: List<PageScene>, val next: JsonElement? = null)

@Serializable
private class PageScene(val id: Int, val name: String, val markdown: String)

private class SceneCount(val id: Int, val name: String, val count: Int)

/** What a tool can't run with or carry on from, reported to the agent as a tool error. */
internal class ToolInputException(message: String) : Exception(message)

internal val textTools = listOf(
	Tool(
		TEXT_COUNT,
		"Exact count of each term in scene text, per scene: every occurrence, not one hit per scene as search " +
			"gives. Matches whole words unless wholeWord is false, ignores italics and curly quotes, and skips titles " +
			"and outlines.",
		READ,
		schema(required = listOf("project", "terms")) {
			putJsonObject("terms") {
				put("type", "array")
				putJsonObject("items") {
					put("type", "string")
					put("minLength", 1)
				}
				put("minItems", 1)
				put("maxItems", MAX_TERMS)
				put("description", "Forms to count, each separately.")
			}
		},
		::textCount,
	),
	Tool(
		TEXT_FIND,
		"Every occurrence of a term in scene text, in story order, with the text around it. Matches as text_count " +
			"does; total is always the full count, and next is the offset of the following page.",
		READ,
		schema(required = listOf("project", "term")) {
			putJsonObject("term") {
				put("type", "string")
				put("minLength", 1)
			}
			putJsonObject("limit") {
				put("type", "integer")
				put("minimum", 1)
				put("maximum", MAX_LIMIT)
				put("default", DEFAULT_LIMIT)
			}
			putJsonObject("offset") {
				put("type", "integer")
				put("minimum", 0)
				put("default", 0)
			}
		},
		::textFind,
	),
)

/** The input schema both tools share, with [extra] properties. */
private fun schema(required: List<String>, extra: JsonObjectBuilder.() -> Unit) = buildJsonObject {
	put("type", "object")
	putJsonObject("properties") {
		putJsonObject("project") { put("type", "string") }
		extra()
		putJsonObject("caseSensitive") {
			put("type", "boolean")
			put("default", false)
		}
		putJsonObject("wholeWord") {
			put("type", "boolean")
			put("default", true)
			put("description", "Not inside a longer word.")
		}
		putJsonObject("group") {
			put("type", "integer")
			put("description", "Only this group's scenes.")
		}
		putJsonObject("ids") {
			put("type", "array")
			putJsonObject("items") { put("type", "integer") }
			put("description", "Only these scenes.")
		}
	}
	putJsonArray("required") { required.forEach { add(JsonPrimitive(it)) } }
}

private fun textCount(arguments: JsonObject): String {
	val project = arguments.string("project")
	val terms = (arguments["terms"] as? JsonArray)?.map { (it as? JsonPrimitive)?.takeIf { it.isString }?.content }
		?: throw ToolInputException("terms must be a list of strings")
	if (terms.isEmpty() || terms.size > MAX_TERMS) throw ToolInputException("terms must hold 1 to $MAX_TERMS terms")
	val caseSensitive = arguments.flag("caseSensitive", false)
	val wholeWord = arguments.flag("wholeWord", true)
	val matchers = terms.mapIndexed { i, term -> matcher(term, "terms[$i]", caseSensitive, wholeWord) }

	val found = terms.map { mutableListOf<SceneCount>() }
	var scanned = 0
	forEachScene(project, arguments) { scene ->
		scanned++
		val text = normalizeText(scene.markdown)
		matchers.forEachIndexed { i, matcher ->
			val count = matcher.count(text)
			if (count > 0) found[i] += SceneCount(scene.id, scene.name, count)
		}
	}
	return buildJsonObject {
		put("scenesScanned", scanned)
		putJsonArray("terms") {
			terms.forEachIndexed { i, term ->
				add(buildJsonObject {
					put("term", term)
					put("total", found[i].sumOf { it.count })
					putJsonArray("scenes") {
						found[i].forEach { scene ->
							add(buildJsonObject {
								put("id", scene.id)
								put("name", scene.name)
								put("count", scene.count)
							})
						}
					}
				})
			}
		}
	}.toString()
}

private fun textFind(arguments: JsonObject): String {
	val project = arguments.string("project")
	val matcher = matcher((arguments["term"] as? JsonPrimitive)?.takeIf { it.isString }?.content, "term",
		arguments.flag("caseSensitive", false), arguments.flag("wholeWord", true))
	val limit = arguments.int("limit", DEFAULT_LIMIT, 1..MAX_LIMIT)
	val offset = arguments.int("offset", 0, 0..Int.MAX_VALUE)

	// Every scene is scanned for the total, but only this page's matches are kept.
	var total = 0
	val matches = mutableListOf<JsonObject>()
	forEachScene(project, arguments) { scene ->
		val text = normalizeText(scene.markdown)
		for (start in matcher.matches(text)) {
			if (total >= offset && total - offset < limit) {
				matches += buildJsonObject {
					put("id", scene.id)
					put("name", scene.name)
					put("context", context(text, start, start + matcher.term.length))
				}
			}
			total++
		}
	}
	return buildJsonObject {
		put("total", total)
		put("matches", JsonArray(matches))
		put("next", if (total - offset > limit) offset + limit else null)
	}.toString()
}

private fun matcher(term: String?, name: String, caseSensitive: Boolean, wholeWord: Boolean): TextMatcher {
	term ?: throw ToolInputException("$name must be a string")
	return TextMatcher(term, caseSensitive, wholeWord).also {
		if (it.term.isEmpty()) throw ToolInputException("$name is blank")
	}
}

/** Runs [action] on each scene [arguments] name, a page at a time, in story order. */
private fun forEachScene(project: String, arguments: JsonObject, action: (PageScene) -> Unit) {
	var after: JsonElement? = null
	do {
		val input = buildJsonObject {
			put("project", project)
			arguments["group"]?.takeUnless { it is JsonNull }?.let { put("group", it) }
			arguments["ids"]?.takeUnless { it is JsonNull }?.let { put("ids", it) }
			put("maxWords", PAGE_WORDS)
			after?.let { put("after", it) }
		}
		val page = json.decodeFromString(ScenePage.serializer(), callJsonText(SCENE_READ_MANY, input))
		page.scenes.forEach(action)
		val next = page.next?.takeUnless { it is JsonNull }
		// A cursor that doesn't move would be read forever.
		if (next != null && next == after) throw ToolInputException("$SCENE_READ_MANY returned the same page twice")
		after = next
	} while (after != null)
}

private fun JsonObject.string(key: String): String =
	(this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: throw ToolInputException("$key must be a string")

private fun JsonObject.flag(key: String, default: Boolean): Boolean {
	val value = this[key]?.takeUnless { it is JsonNull } ?: return default
	return (value as? JsonPrimitive)?.booleanOrNull ?: throw ToolInputException("$key must be true or false")
}

private fun JsonObject.int(key: String, default: Int, range: IntRange): Int {
	val value = this[key]?.takeUnless { it is JsonNull } ?: return default
	// Some clients write a whole number as 50.0.
	val number = (value as? JsonPrimitive)?.let { it.intOrNull ?: it.doubleOrNull?.takeIf { d -> d % 1.0 == 0.0 && d in INT_RANGE }?.toInt() }
	return number?.takeIf { it in range }
		?: throw ToolInputException(
			if (range.last == Int.MAX_VALUE) "$key must be a whole number from ${range.first}" else "$key must be a whole number from ${range.first} to ${range.last}",
		)
}

private const val TEXT_COUNT = "text_count"
private const val TEXT_FIND = "text_find"
private const val SCENE_READ_MANY = "scene.read.many"
private val INT_RANGE = Int.MIN_VALUE.toDouble()..Int.MAX_VALUE.toDouble()
private const val MAX_TERMS = 50
private const val DEFAULT_LIMIT = 50
private const val MAX_LIMIT = 200
/** Words a page of scene.read.many holds, outlines and notes included: few calls, and little text held at once. */
private const val PAGE_WORDS = 20_000
