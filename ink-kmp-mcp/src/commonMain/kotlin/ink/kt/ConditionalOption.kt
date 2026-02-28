package ink.kt

class ConditionalOption internal constructor(
    condition: String,
    parent: Container,
    lineNumber: Int
) : Container() {

    init {
        this.id = Declaration.getId(parent)
        this.text = getCondition(condition)
        this.parent = parent
        this.lineNumber = lineNumber

        if (condition.startsWith(Symbol.DASH) && !condition.endsWith(':')) {
            val txt = condition.substring(1).trim()
            if (txt.isNotEmpty()) {
                if (txt.contains(Symbol.DIVERT))
                    InkParser.parseDivert(lineNumber, txt, this)
                else
                    children.add(InkObject().apply {
                        this.id = "${this@ConditionalOption.id}${Symbol.DOT}${this@ConditionalOption.size}"
                        this.text = txt
                        this.parent = this@ConditionalOption
                        this.lineNumber = lineNumber
                    })
            }
        }
    }

    fun evaluate(vMap: VariableMap): Boolean {
        val res = Declaration.evaluate(text, vMap)
        return (res as Double).toInt() > 0
    }

    companion object {
        fun getCondition(txt: String): String {
            if (txt.isEmpty()) return txt
            if (!txt.endsWith(":")) return ""
            var str = if (txt.startsWith(Symbol.DASH) || txt.startsWith(Symbol.CBRACE_LEFT))
                txt.substring(1).trim()
            else
                txt
            str = str.substring(0, str.length - 1).trim()
            return if (str.equals("else", ignoreCase = true)) Symbol.TRUE else str
        }
    }
}
