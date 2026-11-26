import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// The skills in skills/, served through the MCP Skills extension
// (https://modelcontextprotocol.io/extensions/skills/overview). Each is one SKILL.md, compiled in by the
// build with its digest, so a skill here is always the one in the plugin's source.

/** A skill's SKILL.md: its folder's [name], its [text], and the SHA-256 [digest] and byte [size] of that text. */
internal class SkillFile(val name: String, val text: String, val digest: String, val size: Int)

internal class Skill(val file: SkillFile) {
	val uri = "skill://${file.name}/SKILL.md"
	val frontmatter: Map<String, String> = frontmatter(file.text)
	val name get() = frontmatter.getValue("name")
	val description get() = frontmatter.getValue("description")

	init {
		require(name == file.name) { "Skill '${file.name}' is named '$name' in its frontmatter" }
		require("description" in frontmatter) { "Skill '${file.name}' has no description" }
	}

	/** The extension's Skill entry: frontmatter, and the manifest of its one file. */
	fun toJson() = buildJsonObject {
		put("uri", uri)
		put("frontmatter", JsonObject(frontmatter.mapValues { JsonPrimitive(it.value) }))
		put("resources", buildJsonArray {
			add(buildJsonObject {
				put("uri", uri)
				put("digest", file.digest)
				put("size", file.size)
			})
		})
	}

	fun resource() = buildJsonObject {
		put("uri", uri)
		put("name", name)
		put("description", description)
		put("mimeType", MARKDOWN)
	}

	fun contents() = buildJsonObject {
		put("uri", uri)
		put("mimeType", MARKDOWN)
		put("text", file.text)
	}
}

internal val SKILLS: List<Skill> by lazy { SKILL_FILES.map(::Skill) }

internal fun skill(uri: String): Skill? = SKILLS.firstOrNull { it.uri == uri }

/**
 * The YAML frontmatter of [text], which must be `key: value` lines of plain strings. Anything richer
 * fails rather than be served differently from what the file says, since a client checks the two match.
 */
internal fun frontmatter(text: String): Map<String, String> {
	val lines = text.lines()
	require(lines.first() == "---") { "SKILL.md must start with frontmatter" }
	val end = lines.drop(1).indexOf("---") + 1
	require(end > 0) { "SKILL.md frontmatter is never closed" }
	return lines.subList(1, end).associate { line ->
		val key = line.substringBefore(": ", "")
		val value = line.substringAfter(": ")
		require(key.matches(KEY) && value.isNotEmpty() && isPlainString(value)) { "Frontmatter line '$line' is not a plain string" }
		key to value
	}
}

/**
 * Whether YAML reads [value] as itself: a string with no quoting, comment, nesting, or other type. Tabs
 * are refused outright, since YAML trims them where they border a plain string.
 */
private fun isPlainString(value: String) = value.first() !in "\"'&*!|>%@`#{}[],-?:~ " && '\t' !in value &&
	value.last() != ' ' && ": " !in value && " #" !in value && !value.endsWith(":") &&
	!value.matches(NOT_A_STRING)

private val KEY = Regex("[a-z][a-z0-9-]*")
private val NOT_A_STRING = Regex("(?i)true|false|yes|no|on|off|null|[-+]?(\\.?[0-9].*|\\.inf|\\.nan)")
private const val MARKDOWN = "text/markdown"
