import java.security.MessageDigest
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

plugins {
	id("com.darkrockstudios.hammer.plugin")
}

// Compiles skills/<name>/SKILL.md into the module, so the folder stays the one copy of each skill.
val skillsDir = layout.projectDirectory.dir("skills")
val generatedSkills = layout.buildDirectory.dir("generated/skills/commonMain")
val generateSkills = tasks.register("generateSkills") {
	inputs.dir(skillsDir)
	outputs.dir(generatedSkills)
	doLast {
		val skills = skillsDir.asFile.listFiles { file -> file.resolve("SKILL.md").isFile }!!.sortedBy { it.name }
		val entries = skills.joinToString(",\n") { dir ->
			val bytes = dir.resolve("SKILL.md").readBytes()
			val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
			"\tSkillFile(${literal(dir.name)}, ${literal(bytes.toString(Charsets.UTF_8))}, \"sha256:$digest\", ${bytes.size})"
		}
		generatedSkills.get().file("SkillFiles.kt").asFile.apply { parentFile.mkdirs() }.writeText(
			"// Generated from skills/ by generateSkills.\n\ninternal val SKILL_FILES = listOf(\n$entries,\n)\n"
		)
	}
}
extensions.getByType<KotlinMultiplatformExtension>().sourceSets.getByName("commonMain").kotlin.srcDir(generateSkills)

/**
 * [text] as a Kotlin string literal, all ASCII so a split never breaks a character, and split so no piece
 * passes the JVM's 64 KiB constant limit.
 */
fun literal(text: String): String = text.chunked(8192).joinToString(" + ") { chunk ->
	chunk.map { c ->
		when {
			c == '\\' || c == '"' || c == '$' -> "\\$c"
			c == '\n' -> "\\n"
			c in ' '..'~' -> c.toString()
			else -> "\\u%04x".format(c.code)
		}
	}.joinToString("", "\"", "\"")
}
