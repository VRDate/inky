package ink.kt

import kotlin.random.Random

object StoryText {
    private const val ERROR = "(ERROR:"

    fun getText(text: String, count: Int, variables: VariableMap): String {
        var ret = text
        while (ret.contains(Symbol.CBRACE_LEFT)) {
            val start = ret.lastIndexOf(Symbol.CBRACE_LEFT)
            val end = ret.indexOf(Symbol.CBRACE_RIGHT, start)
            if (end < 0) {
                variables.logException(InkRunTimeException("Mismatched curly braces in text: $text"))
                return ret
            }
            val s = ret.substring(start, end + 1)
            val res = evaluateText(s, count, variables)
            ret = ret.replace(s, res)
        }
        return ret
    }

    private fun evaluateText(str: String, count: Int, variables: VariableMap): String {
        val s = str.replace(Symbol.CBRACE_LEFT.toString(), "").replace(Symbol.CBRACE_RIGHT.toString(), "")
        if (s.contains(":")) return evaluateConditionalText(s, variables)
        if (s.startsWith("&")) return evaluateCycleText(s, count)
        if (s.startsWith("~")) return evaluateShuffleText(s)
        if (s.startsWith("!")) return evaluateOnceOnlyText(s, count)
        if (s.contains("|")) return evaluateSequenceText(s, count)
        return evaluateTextVariable(s, variables)
    }

    private fun evaluateTextVariable(s: String, variables: VariableMap): String {
        return try {
            val obj = Declaration.evaluate(s, variables)
            if (obj is Double) {
                // Format cleanly: 5.0 → "5", 3.14 → "3.14"
                if (obj == obj.toLong().toDouble()) obj.toLong().toString() else obj.toString()
            } else obj.toString()
        } catch (e: InkRunTimeException) {
            variables.logException(e)
            "$ERROR$s${Symbol.BRACE_RIGHT}"
        }
    }

    private fun evaluateSequenceText(str: String, count: Int): String {
        val tokens = str.split("[|]".toRegex()).dropLastWhile(String::isEmpty).toTypedArray()
        val i = if (count < tokens.size) count else tokens.size - 1
        return tokens[i]
    }

    private fun evaluateShuffleText(str: String): String {
        val s = str.substring(1)
        val tokens = s.split("[|]".toRegex()).dropLastWhile(String::isEmpty).toTypedArray()
        return tokens[Random.nextInt(tokens.size)]
    }

    private fun evaluateOnceOnlyText(str: String, count: Int): String {
        val s = str.substring(1)
        val tokens = s.split("[|]".toRegex()).dropLastWhile(String::isEmpty).toTypedArray()
        return if (count < tokens.size) tokens[count] else ""
    }

    private fun evaluateCycleText(str: String, count: Int): String {
        val s = str.substring(1)
        val tokens = s.split("[|]".toRegex()).dropLastWhile(String::isEmpty).toTypedArray()
        return tokens[count % tokens.size]
    }

    private fun evaluateConditionalText(str: String, variables: VariableMap): String {
        if (str.startsWith("?")) {
            val condition = str.substring(1, str.indexOf(Symbol.COLON)).trim()
            val text = str.substring(str.indexOf(Symbol.COLON) + 1)
            val options = text.split("[|]".toRegex()).dropLastWhile(String::isEmpty).toTypedArray()
            var v = 0
            try {
                val value = Declaration.evaluate(condition, variables)
                v = when (value) {
                    is Boolean -> if (value) 1 else 0
                    is Double -> value.toInt()
                    else -> 1
                }
            } catch (e: InkRunTimeException) {
                variables.logException(e)
            }
            if (v >= options.size) return options[options.size - 1]
            if (v < 0) return options[0]
            return options[v]
        }
        // Regular conditional
        val condition = str.substring(0, str.indexOf(Symbol.COLON)).trim()
        val text = str.substring(str.indexOf(Symbol.COLON) + 1)
        val options = text.split("[|]".toRegex()).dropLastWhile(String::isEmpty).toTypedArray()
        if (options.size > 2)
            variables.logException(InkRunTimeException("Too many options in a conditional text."))
        val ifText = options[0]
        val elseText = if (options.size == 1) "" else options[1]
        return try {
            val obj = Declaration.evaluate(condition, variables)
            when (obj) {
                is Double -> if (obj.toInt() > 0) ifText else elseText
                is Boolean -> if (obj) ifText else elseText
                else -> {
                    variables.logException(
                        InkRunTimeException("Condition in conditional text did not resolve into a number or boolean.")
                    )
                    elseText
                }
            }
        } catch (e: InkRunTimeException) {
            variables.logException(e)
            elseText
        }
    }
}
