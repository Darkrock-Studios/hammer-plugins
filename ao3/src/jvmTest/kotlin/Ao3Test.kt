import hammer.ExportRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray

class Ao3Test {
	private fun chapter(name: String, vararg scenes: String) =
		ExportRequest.Chapter(name, prose = scenes.map { Json.parseToJsonElement(it).jsonArray })

	private fun export(vararg chapters: ExportRequest.Chapter, asChapters: Boolean = true, sceneBreak: String? = null) =
		ao3Export(
			ExportRequest(
				"ao3.txt",
				"Wonderland",
				topLevelAsChapters = asChapters,
				chapters = chapters.toList(),
				settings = buildJsonObject { sceneBreak?.let { put("sceneBreak", JsonPrimitive(it)) } },
			),
		)

	private fun paragraph(text: String) = """{"type": "paragraph", "spans": [{"text": "$text"}]}"""

	/** [name] goes into the JSON as is, so a quote in it must come backslash-escaped. */
	private fun image(id: Int, name: String) = """{"type": "image", "id": $id, "name": "$name"}"""

	@Test
	fun `paragraphs and scenes, with a marker per chapter`() {
		val one = chapter(
			" Down the Rabbit-Hole ",
			"[${paragraph("Alice was tired.")}, {\"type\": \"blank\"}, ${paragraph("So very tired.")}]",
			"[${paragraph("Then she fell.")}]",
		)
		val two = chapter("The Pool of Tears", "[${paragraph("Curiouser.")}]")
		assertEquals(
			"""
			===== Chapter 1 of 2: Down the Rabbit-Hole =====

			<p>Alice was tired.</p>
			<p>So very tired.</p>
			<hr />
			<p>Then she fell.</p>


			===== Chapter 2 of 2: The Pool of Tears =====

			<p>Curiouser.</p>

			""".trimIndent(),
			export(one, two),
		)
	}

	@Test
	fun `a rule breaks a scene, blank passages and chapters are dropped`() {
		val scene = "[${paragraph("Before.")}, {\"type\": \"rule\"}, {\"type\": \"rule\"}, ${paragraph("After.")}, {\"type\": \"rule\"}]"
		val empty = chapter("Empty", "[${paragraph("   ")}]")
		assertEquals(
			"===== Chapter 1 of 1: One =====\n\n<p>Before.</p>\n<p align=\"center\">* * *</p>\n<p>After.</p>\n",
			export(chapter("One", scene), empty, sceneBreak = "Asterisks"),
		)
	}

	@Test
	fun `without chapters, the story is one text with scene breaks between chapters`() {
		assertEquals(
			"<p>One.</p>\n<p>&nbsp;</p>\n<p>Two.</p>\n",
			export(chapter("A", "[${paragraph("One.")}]"), chapter("B", "[${paragraph("Two.")}]"), asChapters = false, sceneBreak = "Blank"),
		)
	}

	@Test
	fun `markup in the text is escaped, and a newline is one break`() {
		assertEquals("<p>a &lt;3 b &amp; c &gt; d<br />e</p>", blockHtml(Block.Paragraph(listOf(Span("a <3 b & c > d\ne")))))
	}

	@Test
	fun `styles nest, and a tag stays open across spans that keep it`() {
		val spans = listOf(
			Span("She "),
			Span("was ", italic = true),
			Span("very", italic = true, bold = true),
			Span(" late", italic = true),
			Span(", "),
			Span("struck", strikethrough = true),
			Span(" "),
			Span("code()", code = true),
		)
		assertEquals(
			"She <em>was <strong>very</strong> late</em>, <s>struck</s> <code>code()</code>",
			spansHtml(spans),
		)
	}

	@Test
	fun `links to the web are kept, others are left as text`() {
		assertEquals(
			"<a href=\"https://example.com/?a=1&amp;b=&quot;2&quot;\"><em>here</em></a> and there",
			spansHtml(listOf(Span("here", italic = true, link = "https://example.com/?a=1&b=\"2\""), Span(" and "), Span("there", link = "javascript:alert(1)"))),
		)
	}

	@Test
	fun `nested lists`() {
		val list = Block.Listing(
			false,
			listOf(
				Block.ListItem(0, false, listOf(Span("tea"))),
				Block.ListItem(1, true, listOf(Span("milk"))),
				Block.ListItem(2, false, listOf(Span("warm"))),
				Block.ListItem(0, false, listOf(Span("cake"))),
			),
		)
		assertEquals(
			"<ul>\n<li>tea\n<ol>\n<li>milk\n<ul>\n<li>warm</li>\n</ul></li>\n</ol></li>\n<li>cake</li>\n</ul>",
			blockHtml(list),
		)
	}

	@Test
	fun `headings, quotes, code, and tables`() {
		assertEquals("<h4>Part <em>One</em></h4>", blockHtml(Block.Heading(1, listOf(Span("Part "), Span("One", italic = true)))))
		assertEquals("<h6>Deep</h6>", blockHtml(Block.Heading(5, listOf(Span("Deep")))))
		assertEquals(
			"<blockquote>\n<p>Off with</p>\n<p>her head!</p>\n</blockquote>",
			blockHtml(Block.Quote(listOf(listOf(Span("Off with")), listOf(Span(" ")), listOf(Span("her head!"))))),
		)
		assertEquals("<pre>if (a &lt; b)\n  go()</pre>", blockHtml(Block.Code("if (a < b)\n  go()\n\n")))
		assertEquals(
			"<table>\n<thead>\n<tr><th>Who</th><th>What</th></tr>\n</thead>\n<tbody>\n<tr><td>Hatter</td><td><em>tea</em></td></tr>\n</tbody>\n</table>",
			blockHtml(
				Block.Table(
					listOf(listOf(Span("Who")), listOf(Span("What"))),
					listOf(listOf(listOf(Span("Hatter")), listOf(Span("tea", italic = true)))),
				),
			),
		)
	}

	@Test
	fun `unknown blocks are skipped`() {
		assertEquals(
			"===== Chapter 1 of 1: One =====\n\n<p>Kept.</p>\n",
			export(chapter("One", "[{\"type\": \"hologram\", \"beams\": 3}, ${paragraph("Kept.")}]")),
		)
	}

	@Test
	fun `a Hammer without prose input is told to update`() {
		val markdownOnly = ExportRequest.Chapter("One", scenes = listOf("Alice"))
		assertFailsWith<IllegalStateException> { export(markdownOnly) }
		assertEquals("", export())
	}

	@Test
	fun `an image line is an img placeholder on its own line, for the author to give a URL`() {
		assertEquals(
			"===== Chapter 1 of 1: One =====\n\n<p>Before.</p>\n<img alt=\"Cat\" src=\"\">\n<p>After.</p>\n",
			export(chapter("One", "[${paragraph("Before.")}, ${image(5, "Cat")}, ${paragraph("After.")}]")),
		)
		assertEquals(
			"===== Chapter 1 of 1: One =====\n\n<img alt=\"A\" src=\"\">\n<hr />\n<img alt=\"B\" src=\"\">\n",
			export(chapter("One", "[${image(1, "A")}, {\"type\": \"rule\"}, ${image(2, "B")}]")),
		)
	}

	@Test
	fun `an image's name is escaped in its alt, with a newline as a space`() {
		assertEquals(
			"<img alt=\"Tweedledee &amp; &quot;Tweedledum&quot; &lt;Jr&gt;\" src=\"\">",
			blockHtml(Block.Image("Tweedledee & \"Tweedledum\" <Jr>")),
		)
		assertEquals("<img alt=\"Tweedledee and Tweedledum\" src=\"\">", blockHtml(Block.Image("Tweedledee\nand Tweedledum")))
		assertEquals(
			"===== Chapter 1 of 1: One =====\n\n<img alt=\"&quot;Jr&quot; &amp; co\" src=\"\">\n",
			export(chapter("One", "[${image(2, """\"Jr\" & co""")}]")),
		)
	}

	@Test
	fun `the image files Hammer sends are not used`() {
		val base64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=="
		val out = ao3Export(
			ExportRequest(
				"ao3.txt",
				"Wonderland",
				chapters = listOf(chapter("One", "[${image(7, "Map")}]")),
				images = listOf(ExportRequest.Image(7, "Map", "png", "image/png", base64)),
			),
		)
		assertEquals("===== Chapter 1 of 1: One =====\n\n<img alt=\"Map\" src=\"\">\n", out)
		assertFalse(base64 in out)
		assertFalse("data:" in out)
	}

	@Test
	fun `a paragraph of an image's name, as a Hammer without image blocks sends one, stays a paragraph`() {
		assertEquals("===== Chapter 1 of 1: One =====\n\n<p>Cat</p>\n", export(chapter("One", "[${paragraph("Cat")}]")))
	}
}
