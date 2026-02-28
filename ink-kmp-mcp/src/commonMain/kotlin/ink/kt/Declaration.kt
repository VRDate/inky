package ink.kt

class Declaration internal constructor(
    lineNumber: Int,
    decl: String,
    parent: Container
) : InkObject() {
    init {
        this.id = getId(parent)
        this.text = if (decl.startsWith(VAR_))
            decl.substring(4).trim()
        else
            decl.substring(1).replace(Symbol.RETURN, Symbol.RETURNEQ).trim()
        this.parent = parent
        this.lineNumber = lineNumber
    }

    private val isDeclaration = decl.startsWith(VAR_)

    fun evaluate(story: Story) {
        if (isDeclaration)
            declareVariable(story)
        else
            calculate(story)
    }

    private fun declareVariable(story: Story) {
        val tokens = EQ_REGEX.split(text)
        if (tokens.size != 2)
            throw InkRunTimeException(
                "Invalid variable declaration. Expected values, values, and/or operators after '='."
            )
        val variable = tokens[0].trim()
        val value = tokens[1].trim()
        if (value.startsWith(Symbol.DIVERT)) {
            val directTo = story.getValue(value)
            if (directTo is Container) {
                story.variables[variable] = directTo
                return
            } else {
                throw InkRunTimeException(
                    "DeclareVariable $variable declared as equals to an invalid address $value"
                )
            }
        }
        story.variables[variable] = evaluate(value, story)
    }

    private fun calculate(story: Story) {
        val tokens = EQ_REGEX.split(text)
        if (tokens.size == 1) {
            evaluate(tokens[0], story)
            return
        }
        if (tokens.size > 2)
            throw InkRunTimeException(
                "Invalid variable expression. Expected values, values, and operators after '=' in line $lineNumber"
            )
        val variable = tokens[0].trim()
        val value = tokens[1].trim()
        if (!story.hasValue(variable))
            throw InkRunTimeException(
                "CalculateVariable $variable is not defined in variable expression on line $lineNumber"
            )
        story.variables[variable] = evaluate(value, story)
    }

    companion object {
        private const val VAR_ = "VAR "
        private const val TILDE_ = "~ "
        private val EQ_REGEX = "[=]+".toRegex()
        private const val AND_WS = " and "
        private const val OR_WS = " or "
        private const val TRUE_LC = "true"
        private const val FALSE_LC = "false"

        fun getId(parent: Container): String =
            "${parent.id}${Symbol.DOT}${parent.size}"

        fun evaluate(str: String, variables: VariableMap): Any {
            if (str.isEmpty()) return 1.0
            var ev = ""
            try {
                ev = str
                    .replace(AND_WS.toRegex(), " && ")
                    .replace(OR_WS.toRegex(), " || ")
                    .replace(TRUE_LC.toRegex(), Symbol.TRUE)
                    .replace(FALSE_LC.toRegex(), Symbol.FALSE)
                val ex = Expression(ev)
                return ex.eval(variables)
            } catch (e: Expression.ExpressionException) {
                throw InkRunTimeException("Error evaluating expression $ev. ${e.message}", e)
            }
        }

        fun isVariableHeader(str: String): Boolean =
            str.startsWith(VAR_) || str.startsWith(TILDE_)
    }
}
