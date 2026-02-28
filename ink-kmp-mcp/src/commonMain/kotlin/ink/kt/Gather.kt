package ink.kt

internal class Gather(
    text: String,
    parent: Container,
    val level: Int,
    lineNumber: Int
) : Container() {

    init {
        this.id = getId(text, parent)
        this.text = text
        this.parent = parent
        this.lineNumber = lineNumber

        var str = text.substring(1).trim()
        while (str[0] == Symbol.DASH)
            str = str.substring(1).trim()
        if (str.startsWith(Symbol.BRACE_LEFT)) {
            str = str.substring(str.indexOf(Symbol.BRACE_RIGHT) + 1).trim()
        }
        if (str.isNotEmpty()) {
            if (str.contains(Symbol.DIVERT))
                InkParser.parseDivert(lineNumber, str, this)
            else
                children.add(InkObject().apply {
                    this.id = Declaration.getId(this@Gather)
                    this.text = str
                    this.parent = this@Gather
                    this.lineNumber = lineNumber
                })
        }
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

        fun getId(text: String, parent: Container): String {
            var str = text.substring(1).trim()
            while (str[0] == Symbol.DASH)
                str = str.substring(1).trim()
            if (str.startsWith(Symbol.BRACE_LEFT)) {
                val id = str.substring(
                    str.indexOf(Symbol.BRACE_LEFT) + 1,
                    str.indexOf(Symbol.BRACE_RIGHT)
                ).trim()
                return "${parent.id}${Symbol.DOT}$id"
            }
            return "${parent.id}${Symbol.DOT}${parent.size}"
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
