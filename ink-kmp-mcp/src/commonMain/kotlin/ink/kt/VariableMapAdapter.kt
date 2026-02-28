package ink.kt

/**
 * Adapter that wraps a Story to provide VariableMap access for the parser engine.
 * Delegates to the Story's parser fields (variables, content, functions, container).
 */
internal class VariableMapAdapter(internal val story: Story) : VariableMap {

    override fun logException(e: Exception) {
        story.wrapper?.logException(e)
    }

    override fun getValue(token: String): Any {
        if (Symbol.THIS == token)
            return story.container!!.id
        var c: Container? = story.container
        while (c != null) {
            if (c is ParameterizedContainer && c.hasValue(token))
                return c.getValue(token)
            c = c.parent
        }
        if (token.startsWith(Symbol.DIVERT)) {
            val k = token.substring(2).trim()
            if (story.content.containsKey(k)) return story.content[k]!!
            story.wrapper?.logException(InkRunTimeException("Could not identify container id: $k"))
            return 0.0
        }
        if (story.content.containsKey(token)) {
            val storyContainer = story.content[token]!!
            return storyContainer.count.toDouble()
        }
        val pathId = getValueId(token)
        if (story.content.containsKey(pathId)) {
            val storyContainer = story.content[pathId]!!
            return storyContainer.count.toDouble()
        }
        val knotId = getKnotId(token)
        if (story.content.containsKey(knotId)) {
            val storyContainer = story.content[knotId]!!
            return storyContainer.count.toDouble()
        }
        if (story.container is ParameterizedContainer &&
            (story.container as ParameterizedContainer).hasValue(token))
            return (story.container as ParameterizedContainer).getValue(token)
        if (story.variables.containsKey(token))
            return story.variables[token]!!
        story.wrapper?.logException(InkRunTimeException("Could not identify the variable $token or $pathId"))
        return 0.0
    }

    private fun getValueId(id: String): String {
        if (id.contains(Symbol.DOT.toString())) return id
        return "${story.container!!.id}${Symbol.DOT}$id"
    }

    private fun getKnotId(id: String): String {
        if (id.contains(Symbol.DOT.toString())) return id
        var knot: Container? = story.container
        while (knot != null) {
            if (knot is Knot) return "${knot.id}${Symbol.DOT}$id"
            knot = knot.parent
        }
        return id
    }

    override fun hasValue(token: String): Boolean {
        if (story.functions.containsKey(token)) return false
        if (story.content.containsKey(token)) return true
        if (story.content.containsKey(getValueId(token))) return true
        if (story.content.containsKey(getKnotId(token))) return true
        if (story.container is ParameterizedContainer &&
            (story.container as ParameterizedContainer).hasValue(token))
            return true
        return story.variables.containsKey(token)
    }

    override fun hasFunction(token: String): Boolean =
        story.functions.containsKey(token.lowercase())

    override fun getFunction(token: String): Function {
        if (hasFunction(token.lowercase()))
            return story.functions[token.lowercase()]!!
        throw RuntimeException("Function not found: $token")
    }

    override fun hasGameObject(token: String): Boolean {
        if (Expression.isNumber(token)) return false
        if (token.contains(".")) {
            return hasValue(token.substring(0, token.indexOf(Symbol.DOT)))
        }
        return false
    }

    override fun debugInfo(): String = buildString {
        append("StoryDebugInfo File: ${story.fileNames}")
        append(" Container: ${story.container?.id}")
        val mc = story.container
        if (mc != null && mc.index < mc.size) {
            val cnt = mc[mc.index]
            append(" Line#: ${cnt.lineNumber}")
        }
    }
}
