import hammer.ExportRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

class MarkdownTest {
	private fun export(vararg chapters: ExportRequest.Chapter, asChapters: Boolean = true, numberChapters: Boolean? = null) =
		markdownExport(
			ExportRequest(
				"markdown.md",
				"Wonderland",
				topLevelAsChapters = asChapters,
				chapters = chapters.toList(),
				settings = buildJsonObject { numberChapters?.let { put("numberChapters", JsonPrimitive(it)) } },
			),
		)

	private val one = ExportRequest.Chapter("Down the Rabbit-Hole", listOf("Alice was *tired*.", "Then she fell."))
	private val two = ExportRequest.Chapter("The Pool of Tears", listOf("Curiouser."))

	@Test
	fun `chapters are headings, and scenes are joined by a blank line`() {
		assertEquals(
			"# Wonderland\n\n\n## Down the Rabbit-Hole\n\nAlice was *tired*.\n\nThen she fell.\n\n## The Pool of Tears\n\nCuriouser.\n",
			export(one, two),
		)
	}

	@Test
	fun `chapters are numbered when the setting is on`() {
		assertEquals(
			"# Wonderland\n\n\n## 1. Down the Rabbit-Hole\n\nAlice was *tired*.\n\nThen she fell.\n\n## 2. The Pool of Tears\n\nCuriouser.\n",
			export(one, two, numberChapters = true),
		)
	}

	@Test
	fun `without chapters, top-level scenes are joined by blank lines and never numbered`() {
		assertEquals(
			"# Wonderland\n\nAlice was *tired*.\n\nThen she fell.\n\n\nCuriouser.\n",
			export(one, two, asChapters = false, numberChapters = true),
		)
	}

	@Test
	fun `names and scenes are written untrimmed`() {
		assertEquals("# Wonderland\n\n\n##  Spaced \n\n  Indented\n", export(ExportRequest.Chapter(" Spaced ", listOf("  Indented"))))
	}

	@Test
	fun `an empty story is its title`() {
		assertEquals("# Wonderland\n\n", export())
	}

	@Test
	fun `image references are left as written`() {
		val scene = "Before.\n\n![image](hammer://image/5)\n\n![image](<hammer://image/7>)\n\nAfter."
		assertEquals(
			"# Wonderland\n\n\n## Maps\n\n$scene\n",
			export(ExportRequest.Chapter("Maps", listOf(scene))),
		)
	}
}
