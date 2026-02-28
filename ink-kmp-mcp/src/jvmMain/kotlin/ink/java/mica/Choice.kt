package ink.java.mica

import ink.java.mica.util.InkRunTimeException

internal class Choice(
    textBase: String,
    val level: Int,
    parent: Container,
    lineNumber: Int
) : Container(
    Choice.getId(textBase, parent),
    Choice.extractChoiceText(textBase),
    parent,
    lineNumber
) {
    private val conditions: MutableList<String> = mutableListOf()
    private val repeatable = (textBase[0] == Symbol.CHOICE_PLUS)

    init {
        val notation = textBase[0]
        var header = textBase
        while (header[0] == notation)
            header = header.substring(1).trim()
        if (header.startsWith(Symbol.BRACE_LEFT)) {
            header = header.substring(header.indexOf(Symbol.BRACE_RIGHT) + 1).trim()
        }
        while (header.startsWith(Symbol.CBRACE_LEFT)) {
            conditions.add(
                header.substring(
                    header.indexOf(Symbol.CBRACE_LEFT) + 1,
                    header.indexOf(Symbol.CBRACE_RIGHT)
                ).trim()
            )
            header = header.substring(header.indexOf(Symbol.CBRACE_RIGHT) + 1).trim()
        }
        val result = if (header.contains("]"))
            header.replace("\\[.*\\]".toRegex(), "").trim()
        else
            header
        if (result.isNotEmpty()) {
            if (result.contains(Symbol.DIVERT))
                InkParser.parseDivert(lineNumber, result, this)
            else
                children.add(Content("${id}${Symbol.DOT}$size", result, this, lineNumber))
        }
    }

    override fun getText(story: Story): String =
        StoryText.getText(text, count, story)

    fun isFallBack(): Boolean = text.isEmpty()

    fun evaluateConditions(story: Story): Boolean {
        if (count > 0 && !repeatable) return false
        for (condition in conditions) {
            try {
                val obj = Declaration.evaluate(condition, story)
                if (obj is Boolean && !obj) return false
                if (obj is Double && obj.toInt() <= 0) return false
            } catch (e: InkRunTimeException) {
                story.logException(e)
                return false
            }
        }
        return true
    }

    companion object {
        fun getChoiceDepth(line: String): Int {
            val notation = line[0]
            var lvl = 0
            var s = line.substring(1).trim()
            while (s[0] == notation) {
                lvl++
                s = s.substring(1).trim()
            }
            return lvl
        }

        fun getId(header: String, parent: Container): String {
            val notation = header[0]
            var id = header
            while (id.startsWith(notation))
                id = id.substring(1).trim()
            if (id.startsWith(Symbol.BRACE_LEFT)) {
                id = id.substring(
                    id.indexOf(Symbol.BRACE_LEFT) + 1,
                    id.indexOf(Symbol.BRACE_RIGHT)
                ).trim()
                return "${parent.id}${Symbol.DOT}$id"
            }
            return "${parent.id}${Symbol.DOT}${parent.size}"
        }

        fun extractChoiceText(header: String): String {
            val notation = header[0]
            var text = header
            while (text[0] == notation)
                text = text.substring(1).trim()
            if (text.startsWith(Symbol.BRACE_LEFT)) {
                text = text.substring(text.indexOf(Symbol.BRACE_RIGHT) + 1).trim()
            }
            while (text.startsWith(Symbol.CBRACE_LEFT)) {
                text = text.substring(text.indexOf(Symbol.CBRACE_RIGHT) + 1).trim()
            }
            if (text.contains("]"))
                text = text.substring(0, text.indexOf(Symbol.SBRACE_RIGHT))
                    .replace(Symbol.SBRACE_LEFT.toString(), "").trim()
            return text
        }

        fun getParent(currentContainer: Container, lvl: Int): Container {
            var ret = currentContainer
            while (true) {
                if ((ret is Choice && lvl <= ret.level) || (ret is Gather && lvl <= ret.level))
                    ret = ret.parent!!
                else
                    return ret
            }
        }
    }
}
