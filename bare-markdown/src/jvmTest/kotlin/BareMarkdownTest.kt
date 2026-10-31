import hammer.ExportRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

class BareMarkdownTest {
	private fun export(vararg chapters: ExportRequest.Chapter, asChapters: Boolean = true, numberChapters: Boolean? = null) =
		bareMarkdownExport(
			ExportRequest(
				"bare-markdown.md",
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
	fun `Hammer's images are left out`() {
		val scene = "Before.\n\n![image](hammer://image/5)\n\n![image](<hammer://image/7>)\n\nAfter."
		assertEquals(
			"# Wonderland\n\n\n## Maps\n\nBefore.\n\nAfter.\n",
			export(ExportRequest.Chapter("Maps", listOf(scene))),
		)
	}

	@Test
	fun `an image on a line of its own between paragraphs leaves one blank line`() {
		assertEquals("Before.\n\nAfter.", stripHammerImages("Before.\n\n![a map](hammer://image/5)\n\nAfter."))
	}

	@Test
	fun `an image first in a scene goes with the blank line after it`() {
		assertEquals("After.", stripHammerImages("![image](hammer://image/5)\n\nAfter."))
	}

	@Test
	fun `an image last in a scene with no newline goes with the blank line before it`() {
		assertEquals("Before.", stripHammerImages("Before.\n\n![image](hammer://image/5)"))
	}

	@Test
	fun `two image lines in a row go together`() {
		assertEquals(
			"Before.\n\nAfter.",
			stripHammerImages("Before.\n\n![image](hammer://image/5)\n![image](hammer://image/6)\n\nAfter."),
		)
	}

	@Test
	fun `an image line inside a paragraph goes without joining its lines`() {
		assertEquals("One line\nthe next.", stripHammerImages("One line\n![image](hammer://image/5)\nthe next."))
	}

	@Test
	fun `an image mid-sentence goes, and the space around it closes up`() {
		assertEquals(
			"She looked at the map and frowned.",
			stripHammerImages("She looked at the map ![image](hammer://image/5) and frowned."),
		)
		assertEquals("The map: here.", stripHammerImages("The map: ![image](hammer://image/5)here."))
	}

	@Test
	fun `an image in angle brackets goes too`() {
		assertEquals("Before.\n\nAfter.", stripHammerImages("Before.\n\n![image]( <hammer://image/7> )\n\nAfter."))
	}

	@Test
	fun `other images and text are left alone`() {
		val scene = "Before.\n\n![cover](https://example.com/cover.png)\n\n![x](hammer://image/abc) and hammer://image/5."
		assertEquals(scene, stripHammerImages(scene))
	}

	@Test
	fun `an image ending a chapter's scene keeps one blank line before the next scene`() {
		val maps = ExportRequest.Chapter("Maps", listOf("Before.\n\n![image](hammer://image/5)", "Next scene."))
		assertEquals("# Wonderland\n\n\n## Maps\n\nBefore.\n\nNext scene.\n", export(maps))
	}

	@Test
	fun `an image starting a line leaves no space before the text`() {
		assertEquals("Hello.", stripHammerImages("![i](hammer://image/5) Hello."))
	}

	@Test
	fun `an image ending a line leaves no space after the text`() {
		assertEquals("Text", stripHammerImages("Text ![i](hammer://image/5)"))
	}

	@Test
	fun `an image after two spaces leaves no hard break`() {
		assertEquals("Text\nnext", stripHammerImages("Text  ![i](hammer://image/5)\nnext"))
	}

	@Test
	fun `CRLF lines keep their line ends`() {
		assertEquals("B\r\n\r\nA", stripHammerImages("B\r\n\r\n![i](hammer://image/5)\r\n\r\nA"))
	}

	@Test
	fun `several images on one line all go`() {
		assertEquals("a b", stripHammerImages("a ![i](hammer://image/1) ![i](hammer://image/2) b"))
	}

	@Test
	fun `tabs around an image close up too`() {
		assertEquals("a\tb", stripHammerImages("a\t![i](hammer://image/1)\tb"))
	}

	@Test
	fun `an image last in a scene with a newline keeps the newline`() {
		assertEquals("B\n", stripHammerImages("B\n\n![i](hammer://image/5)\n"))
	}

	@Test
	fun `an image line after a blank line and before text keeps the blank line`() {
		assertEquals("B\n\nA", stripHammerImages("B\n\n![i](hammer://image/5)\nA"))
	}

	@Test
	fun `a scene that was only an image goes, and its neighbours stay one blank line apart`() {
		val maps = ExportRequest.Chapter("Maps", listOf("Before.", "![image](hammer://image/5)", "After."))
		assertEquals("# Wonderland\n\n\n## Maps\n\nBefore.\n\nAfter.\n", export(maps))
	}

	@Test
	fun `a chapter whose only scene was an image keeps its heading`() {
		val maps = ExportRequest.Chapter("Maps", listOf("![image](hammer://image/5)"))
		assertEquals("# Wonderland\n\n\n## Maps\n\n\n\n## The Pool of Tears\n\nCuriouser.\n", export(maps, two))
	}

	@Test
	fun `without chapters, an item that was only an image writes nothing`() {
		val map = ExportRequest.Chapter("Map", listOf("![image](hammer://image/5)"))
		assertEquals(
			"# Wonderland\n\nAlice was *tired*.\n\nThen she fell.\n\n\nCuriouser.\n",
			export(one, map, two, asChapters = false),
		)
		assertEquals("# Wonderland\n\nCuriouser.\n", export(map, two, asChapters = false))
	}

	@Test
	fun `a scene empty as stored is still joined`() {
		val gap = ExportRequest.Chapter("Gap", listOf("Before.", "", "After."))
		assertEquals("# Wonderland\n\n\n## Gap\n\nBefore.\n\n\n\nAfter.\n", export(gap))
	}
}
