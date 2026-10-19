// First meeting: an action that picks two characters and finds the first scene, in story order, that
// both are confirmed to appear in, by the references in each scene's metadata (entry.read's
// referencedBy). It lists the other scenes they share too, each a button that opens it.
import hammer.ActionReply
import hammer.ActionReply.Button
import hammer.ActionReply.Field
import hammer.ActionReply.Open
import hammer.ActionReply.Page
import hammer.ActionRequest
import hammer.EntityRef
import hammer.OperationException
import hammer.call
import hammer.input
import hammer.state
import hammer.stateOf
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** The two characters picked, as the picking page's input has them. */
@Serializable
private class Picks(val a: EntityRef? = null, val b: EntityRef? = null)

/** The state of a page of results: the pair, and the ids of the scenes they share in story order, which its buttons open. */
@Serializable
data class Meeting(val a: EntityRef, val b: EntityRef, val scenes: List<Int> = emptyList())

@Serializable
private class Entry(val name: String, val referencedBy: List<Int> = emptyList())

@Serializable
private class SceneTree(val nodes: List<Node> = emptyList()) {
	@Serializable
	class Node(val id: Int, val name: String, val kind: String, val children: List<Node> = emptyList())
}

/** A scene with the names of the groups it is in, outermost first. */
private class PlacedScene(val id: Int, val name: String, val groups: List<String>)

/** The separator of a scene's groups, as Hammer's picker shows them. */
private const val GROUP_SEPARATOR = " › "

private const val OPEN = "open:"

fun meet(request: ActionRequest): ActionReply {
	// Run from an entry, that entry starts as the first character; Hammer drops it when it is not a person.
	val button = request.button
		?: return pickPage(request.context.id?.takeIf { request.context.place == "entry" }?.let { EntityRef("entry", it) })
	val meeting = request.state<Meeting>()
	return when {
		button == "find" -> find(request)
		button.startsWith(OPEN) -> open(button.removePrefix(OPEN).toIntOrNull(), meeting)
		// Back, or a button no page of this version has.
		else -> pickPage(meeting?.a, meeting?.b)
	}
}

/** The page picking the pair, starting with [a] and [b]. */
private fun pickPage(a: EntityRef? = null, b: EntityRef? = null) = ActionReply(
	Page(
		markdown = "Pick two characters to find the first scene they are both in.",
		fields = listOf(character("a", "First character", a), character("b", "Second character", b)),
		buttons = listOf(Button("find", "Find", primary = true)),
	),
)

private fun character(key: String, label: String, default: EntityRef?) =
	Field.entity(key, label, required = true, kinds = listOf("entry"), entryTypes = listOf("person"), default = default)

private fun find(request: ActionRequest): ActionReply {
	val picks = request.input<Picks>()
	val a = picks.a?.takeIf { it.kind == "entry" }
	val b = picks.b?.takeIf { it.kind == "entry" }
	if (a == null || b == null) {
		return ActionReply(errors = listOfNotNull("a".takeIf { a == null }, "b".takeIf { b == null }).associateWith { "Pick a character." })
	}
	if (a.id == b.id) return ActionReply(errors = mapOf("b" to "Pick two different characters."))
	return try {
		val first = read(request.project, a.id)
		val second = read(request.project, b.id)
		val shared = first.referencedBy.toSet().intersect(second.referencedBy.toSet())
		val scenes = if (shared.isEmpty()) emptyList() else storyOrder(tree(request.project)).filter { it.id in shared }
		ActionReply(resultPage(first.name, second.name, scenes, Meeting(a, b, scenes.map { it.id })))
	} catch (e: OperationException) {
		ActionReply(errors = mapOf("" to (e.message ?: "Hammer could not read those characters")))
	}
}

private fun read(project: String, id: Int) = call<Entry>("entry.read", buildJsonObject {
	put("project", project)
	put("id", id)
})

private fun tree(project: String) = call<SceneTree>("scene.tree", buildJsonObject { put("project", project) })

/** The tree's scenes in story order, the order a walk of it meets them, each with its groups. */
private fun storyOrder(tree: SceneTree): List<PlacedScene> {
	val scenes = mutableListOf<PlacedScene>()
	fun walk(nodes: List<SceneTree.Node>, groups: List<String>) {
		for (node in nodes) {
			if (node.kind == "scene") scenes += PlacedScene(node.id, node.name, groups) else walk(node.children, groups + node.name)
		}
	}
	walk(tree.nodes, emptyList())
	return scenes
}

private fun resultPage(first: String, second: String, scenes: List<PlacedScene>, meeting: Meeting): Page {
	val pair = "**${markdownText(first)}** and **${markdownText(second)}**"
	val back = Button("back", "Back", validate = false)
	if (scenes.isEmpty()) {
		return Page(
			title = "$first and $second",
			markdown = "$pair are never in the same scene.\n\n" +
				"This goes by the references confirmed in each scene's metadata: if they do share a scene, add " +
				"whichever of them it is missing in that scene's references panel.",
			buttons = listOf(back),
			state = stateOf(meeting),
		)
	}
	val markdown = buildString {
		append("$pair first meet in ${place(scenes.first())}.\n\n")
		if (scenes.size > 1) {
			append("They also share:\n\n")
			for (scene in scenes.drop(1)) append("- ${place(scene)}\n")
			append("\nThey share ${scenes.size} scenes.")
		} else {
			append("It is the only scene they share.")
		}
	}
	return Page(
		title = "$first and $second",
		markdown = markdown,
		buttons = scenes.map { Button("$OPEN${it.id}", it.name, inline = true) } +
			listOf(back, Button("$OPEN${scenes.first().id}", "Open ${scenes.first().name}", primary = true)),
		state = stateOf(meeting),
	)
}

/** [scene]'s name in bold, then the groups it is in, if any. */
private fun place(scene: PlacedScene): String {
	val name = "**${markdownText(scene.name)}**"
	return if (scene.groups.isEmpty()) name else "$name, in ${markdownText(scene.groups.joinToString(GROUP_SEPARATOR))}"
}

/**
 * Goes to the scene [id], if it is one of those on the page. Any other id keeps the page as it is, with an
 * error, since Hammer fails a reply that opens an item that does not exist.
 */
private fun open(id: Int?, meeting: Meeting?): ActionReply =
	if (id != null && meeting != null && id in meeting.scenes) ActionReply(open = Open("scene", id))
	else ActionReply(errors = mapOf("" to "That scene is not one they share. Go back and find them again."))

/** [text] as it should read, with the characters that would format it escaped. */
private fun markdownText(text: String): String = buildString {
	for (c in text) {
		if (c in "\\*_`[]<>#|~=") append('\\')
		append(c)
	}
}
