package ink.kt.mica

import ink.kt.mica.util.InkParseException

object InkParser {

    fun parse(provider: StoryWrapper, fileName: String): Story {
        val input: String = provider.getFileContent(fileName)
        return parse(input, provider, fileName)
    }

    fun parse(input: String, provider: StoryWrapper, fileName: String): Story {
        val includes = mutableListOf<String>()
        val content = mutableMapOf<String, Content>()
        var topContainer: Container? = null
        val lines = input.lines()
        var lineNumber = 1
        var currentContainer: Container? = null
        for (rawLine in lines) {
            var trimmedLine = rawLine.trim()
            if (trimmedLine.contains(Symbol.COMMENT)) {
                trimmedLine = trimmedLine.substring(0, trimmedLine.indexOf(Symbol.COMMENT)).trim()
            }
            if (trimmedLine.contains(Symbol.HASHMARK)) {
                val tags = trimmedLine.substring(trimmedLine.indexOf(Symbol.HASHMARK) + 1).trim()
                    .split(Symbol.HASHMARK)
                for (tag in tags) {
                    val tagTxt = tag.trim()
                    if (tagTxt.isNotEmpty()) {
                        val current = Tag(tagTxt, currentContainer, lineNumber)
                        if (currentContainer != null)
                            currentContainer.add(current)
                        if (!content.containsKey(current.id))
                            content[current.id] = current
                    }
                }
                trimmedLine = trimmedLine.substring(0, trimmedLine.indexOf(Symbol.HASHMARK)).trim()
            }
            if (trimmedLine.startsWith(Symbol.INCLUDE)) {
                val includeFile = trimmedLine.replace(Symbol.INCLUDE, "").trim()
                includes.add(includeFile)
            }
            val tokens = parseLine(lineNumber, trimmedLine, currentContainer)
            for (current in tokens) {
                if (current is Container)
                    currentContainer = current
                if (!content.containsKey(current.id))
                    content[current.id] = current
                if (currentContainer != null && topContainer == null)
                    topContainer = currentContainer
            }
            lineNumber++
        }
        if (topContainer == null)
            throw InkParseException("Could not detect a root knot node in $fileName")
        val story = Story(provider, fileName, topContainer, content)
        for (includeFile in includes) {
            story.add(parse(provider, includeFile))
        }
        return story
    }

    private fun parseLine(lineNumber: Int, line: String, currentContainer: Container?): List<Content> {
        val firstChar = if (line.isEmpty()) Symbol.WHITESPACE else line[0]
        when (firstChar) {
            Symbol.HEADER -> {
                if (Knot.isKnot(line))
                    return mutableListOf(Knot(line, lineNumber))
                if (Stitch.isStitchHeader(line)) {
                    if (currentContainer == null)
                        throw InkParseException("Stitch without a containing Knot at line $lineNumber")
                    return parseContainer(Stitch(line, Stitch.getParent(currentContainer), lineNumber))
                }
            }
            Symbol.CHOICE_DOT, Symbol.CHOICE_PLUS -> {
                if (currentContainer == null)
                    throw InkParseException("Choice without an anchor at line $lineNumber")
                val choiceDepth = Choice.getChoiceDepth(line)
                return parseContainer(
                    Choice(line, choiceDepth, Choice.getParent(currentContainer, choiceDepth), lineNumber)
                )
            }
            Symbol.DASH -> {
                if (line.startsWith(Symbol.DIVERT))
                    return parseDivert(lineNumber, line, currentContainer)
                if (currentContainer == null)
                    throw InkParseException("Dash without an anchor at line $lineNumber")
                if (isConditional(currentContainer)) {
                    return parseContainer(
                        ConditionalOption(line, getConditional(currentContainer), lineNumber)
                    )
                } else {
                    val level = Gather.getChoiceDepth(line)
                    return parseContainer(
                        Gather(line, Gather.getParent(currentContainer, level), level, lineNumber)
                    )
                }
            }
            Symbol.VAR_DECL, Symbol.VAR_STAT -> {
                if (currentContainer == null)
                    throw InkParseException("Declaration is not inside a knot/container at line $lineNumber")
                if (Declaration.isVariableHeader(line))
                    return parseContainer(Declaration(lineNumber, line, currentContainer))
            }
            Symbol.CBRACE_LEFT -> if (Conditional.isConditionalHeader(line))
                return parseContainer(Conditional(line, currentContainer!!, lineNumber))
            Symbol.CBRACE_RIGHT -> if (currentContainer is Conditional || currentContainer is ConditionalOption)
                return mutableListOf(getConditional(currentContainer).parent as Content)
            else -> { }
        }
        if (line.contains(Symbol.DIVERT))
            return parseDivert(lineNumber, line, currentContainer)
        if (line.isNotEmpty() && currentContainer != null)
            return parseContainer(Content(Content.getId(currentContainer), line, currentContainer, lineNumber))
        return emptyList()
    }

    private fun parseContainer(cont: Content): MutableList<Content> {
        cont.parent!!.add(cont)
        val list = mutableListOf(cont)
        if (cont is Container)
            list.addAll(cont.children)
        return list
    }

    private fun isConditional(currentContainer: Container?): Boolean {
        var container = currentContainer
        while (container != null) {
            if (container is Conditional) return true
            container = container.parent
        }
        return false
    }

    private fun getConditional(currentContainer: Container): Container {
        var container: Container? = currentContainer
        while (container != null) {
            if (container is Conditional) return container
            container = container.parent
        }
        return currentContainer
    }

    internal fun parseDivert(lineNumber: Int, line: String, currentContainer: Container?): List<Content> {
        val ret = mutableListOf<Content>()
        val div = line.split(Symbol.DIVERT)
        if (div[0].isNotEmpty()) {
            val text = Content(Content.getId(currentContainer!!), "${div[0]}${Symbol.GLUE}", currentContainer, lineNumber)
            currentContainer.add(text)
            ret.add(text)
        }
        for (i in 1 until div.size) {
            if (div[i].isNotEmpty()) {
                val divert = Divert(lineNumber, div[i].trim(), currentContainer!!)
                currentContainer.add(divert)
                ret.add(divert)
            }
        }
        return ret
    }
}
