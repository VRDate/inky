package ink.kt

import kotlin.math.pow

/**
 * A one-class expression evaluator.
 * Originally based on https://github.com/uklimaschewski/EvalEx
 * KMP-compatible: uses Double instead of BigDecimal, no Java reflection.
 *
 * Creates a new expression instance from an expression string.
 * @param originalExpression The expression, e.g. `"2.4*sin(3)/(2-4)"` or `"sin(y)>0 & max(z, 3)>3"`
 */
class Expression(originalExpression: String) {
    class ExpressionException(message: String) : RuntimeException(message)

    /**
     * Pluggable interface for game object method dispatch.
     * Replaces Java reflection for KMP compatibility.
     */
    fun interface GameObjectResolver {
        fun call(obj: Any, method: String, params: List<Any>): Any
    }

    private var rpn: List<String>? = null
    private val firstVarChars: String = "_"
    private val varChars: String = "_."
    private val operators = sortedMapOf<String, Operator>(String.CASE_INSENSITIVE_ORDER)
    private var expression: String = originalExpression.trim()
        private set

    /** Optional resolver for game object method calls (e.g. `object.method(args)`). */
    var gameObjectResolver: GameObjectResolver? = null

    /**
     * Expression tokenizer that allows iterating over a [String] expression token by token.
     * Blank characters are skipped.
     */
    private inner class Tokenizer(input: String) : Iterator<String> {
        var pos = 0
            private set
        private val input: String = input.trim()
        private var previousToken: String? = null

        override fun hasNext(): Boolean = pos < input.length

        private fun peekNextChar(): Char =
            if (pos < input.length - 1) input[pos + 1] else 0.toChar()

        override fun next(): String {
            val token = StringBuilder()
            if (pos >= input.length) {
                previousToken = null
                return ""
            }
            var ch = input[pos]
            while (ch.isWhitespace() && pos < input.length) {
                ch = input[++pos]
            }
            if (ch.isDigit()) {
                while ((ch.isDigit() || ch == decimalSeparator
                        || ch == 'e' || ch == 'E'
                        || ch == minusSign && token.isNotEmpty()
                        && ('e' == token[token.length - 1] || 'E' == token[token.length - 1])
                        || ch == '+' && token.isNotEmpty()
                        && ('e' == token[token.length - 1] || 'E' == token[token.length - 1]))
                    && pos < input.length
                ) {
                    token.append(input[pos++])
                    ch = if (pos == input.length) '0' else input[pos]
                }
            } else if (ch == minusSign
                && peekNextChar().isDigit()
                && ("(" == previousToken
                        || "," == previousToken
                        || previousToken == null
                        || operators.containsKey(previousToken!!))
            ) {
                token.append(minusSign)
                pos++
                token.append(next())
            } else if (ch.isLetter() || firstVarChars.indexOf(ch) >= 0 || ch == '_') {
                while ((ch.isLetter()
                        || ch.isDigit()
                        || varChars.indexOf(ch) >= 0
                        || ch == '_' || ch == '.' || ch == '\"')
                    && pos < input.length
                ) {
                    token.append(input[pos++])
                    ch = if (pos == input.length) '0' else input[pos]
                }
            } else if (ch == '\"') {
                token.append(input[pos++])
                ch = if (pos == input.length) '0' else input[pos]
                while (ch != '\"' && pos < input.length) {
                    token.append(ch)
                    pos++
                    ch = if (pos == input.length) '0' else input[pos]
                }
                if (ch == '\"' && pos < input.length) {
                    token.append(ch)
                    pos++
                }
            } else if (ch == '(' || ch == ')' || ch == ',') {
                token.append(ch)
                pos++
            } else {
                while (!ch.isLetter() && !ch.isDigit()
                    && firstVarChars.indexOf(ch) < 0 && !ch.isWhitespace()
                    && ch != '(' && ch != ')' && ch != ','
                    && pos < input.length
                ) {
                    token.append(input[pos])
                    pos++
                    ch = if (pos == input.length) '0' else input[pos]
                    if (ch == minusSign) break
                }
                if (!operators.containsKey(token.toString())) {
                    throw ExpressionException(
                        "Unknown operator '$token' at position ${pos - token.length + 1}"
                    )
                }
            }
            previousToken = token.toString()
            return previousToken!!
        }
    }

    init {
        this.expression = originalExpression
        addOperator(object : Operator("+", 20, true) {
            override fun eval(v1: Any, v2: Any): Any = when (v1) {
                is Double -> v1 + (v2 as Double)
                is String -> stripStringParameter(v1) + stripStringParameter(v2.toString())
                else -> 0.0
            }
        })
        addOperator(object : Operator("-", 20, true) {
            override fun eval(v1: Any, v2: Any): Any = when (v1) {
                is Double -> v1 - (v2 as Double)
                else -> 0.0
            }
        })
        addOperator(object : Operator("*", 30, true) {
            override fun eval(v1: Any, v2: Any): Any = when (v1) {
                is Double -> v1 * (v2 as Double)
                else -> 0.0
            }
        })
        addOperator(object : Operator("/", 30, true) {
            override fun eval(v1: Any, v2: Any): Any = when (v1) {
                is Double -> v1 / (v2 as Double)
                else -> 0.0
            }
        })
        addOperator(object : Operator("%", 30, true) {
            override fun eval(v1: Any, v2: Any): Any = when (v1) {
                is Double -> v1 % (v2 as Double)
                else -> 0.0
            }
        })
        addOperator(object : Operator("^", 40, false) {
            override fun eval(v1: Any, v2: Any): Any {
                if (v1 !is Double || v2 !is Double) return 0.0
                return v1.pow(v2)
            }
        })
        addOperator(object : Operator("&&", 4, false) {
            override fun eval(v1: Any, v2: Any): Any {
                val b1 = v1 != 0.0
                val b2 = v2 != 0.0
                return if (b1 && b2) 1.0 else 0.0
            }
        })
        addOperator(object : Operator("||", 2, false) {
            override fun eval(v1: Any, v2: Any): Any {
                val b1 = v1 != 0.0
                val b2 = v2 != 0.0
                return if (b1 || b2) 1.0 else 0.0
            }
        })
        addOperator(object : Operator(">", 10, false) {
            override fun eval(v1: Any, v2: Any): Any {
                if (v1 !is Double || v2 !is Double) return 0.0
                return if (v1 > v2) 1.0 else 0.0
            }
        })
        addOperator(object : Operator(">=", 10, false) {
            override fun eval(v1: Any, v2: Any): Any {
                if (v1 !is Double || v2 !is Double) return 0.0
                return if (v1 >= v2) 1.0 else 0.0
            }
        })
        addOperator(object : Operator("<", 10, false) {
            override fun eval(v1: Any, v2: Any): Any {
                if (v1 !is Double || v2 !is Double) return 0.0
                return if (v1 < v2) 1.0 else 0.0
            }
        })
        addOperator(object : Operator("<=", 10, false) {
            override fun eval(v1: Any, v2: Any): Any {
                if (v1 !is Double || v2 !is Double) return 0.0
                return if (v1 <= v2) 1.0 else 0.0
            }
        })
        addOperator(object : Operator("==", 7, false) {
            override fun eval(v1: Any, v2: Any): Any = operators["="]!!.eval(v1, v2)
        })
        addOperator(object : Operator("=", 7, false) {
            override fun eval(v1: Any, v2: Any): Any = when (v1) {
                is Double -> if (v1 == (v2 as Double)) 1.0 else 0.0
                is String -> if (stripStringParameter(v1) == stripStringParameter(v2 as String)) 1.0 else 0.0
                else -> 0.0
            }
        })
        addOperator(object : Operator("!=", 7, false) {
            override fun eval(v1: Any, v2: Any): Any = when (v1) {
                is Double -> if (v1 != (v2 as Double)) 1.0 else 0.0
                is String -> if (stripStringParameter(v1) != stripStringParameter(v2 as String)) 1.0 else 0.0
                else -> 0.0
            }
        })
        addOperator(object : Operator("<>", 7, false) {
            override fun eval(v1: Any, v2: Any): Any = operators["!="]!!.eval(v1, v2)
        })
        addOperator(object : Operator("?:", 40, false) {
            override fun eval(v1: Any, v2: Any): Any = when (v2) {
                is Double -> if (v1 is Double && v1 == 0.0) v2 else v1
                is String -> if (v1 is Double && v1 == 0.0) stripStringParameter(v2) else v1
                else -> if (v1 is Double && v1 == 0.0) v2 else v1
            }
        })
    }

    private fun shuntingYard(expression: String, vMap: VariableMap): List<String> {
        val outputQueue = mutableListOf<String>()
        val stack = ArrayDeque<String>()
        val tokenizer = Tokenizer(expression)
        var lastFunction: String? = null
        var previousToken: String? = null
        while (tokenizer.hasNext()) {
            val token = tokenizer.next()
            if (isNumber(token)) {
                outputQueue.add(token)
            } else if (isStringParameter(token)) {
                outputQueue.add(token)
            } else if (vMap.hasValue(token)) {
                outputQueue.add(token)
            } else if (vMap.hasFunction(token)) {
                stack.addLast(token)
                lastFunction = token
            } else if (vMap.hasGameObject(token)) {
                stack.addLast(token)
                lastFunction = token
            } else if (token[0].isLetter()) {
                stack.addLast(token)
            } else if ("," == token) {
                if (operators.containsKey(previousToken)) {
                    throw ExpressionException(
                        "Missing parameter(s) for operator $previousToken at character position ${tokenizer.pos - 1 - previousToken!!.length}"
                    )
                }
                while (stack.isNotEmpty() && "(" != stack.last()) {
                    outputQueue.add(stack.removeLast())
                }
                if (stack.isEmpty()) {
                    throw ExpressionException("Parse error for function '$lastFunction'")
                }
            } else if (operators.containsKey(token)) {
                if ("," == previousToken || "(" == previousToken) {
                    throw ExpressionException(
                        "Missing parameter(s) for operator $token at character position ${tokenizer.pos - token.length}"
                    )
                }
                val o1: Operator = operators[token]!!
                var token2: String? = if (stack.isEmpty()) null else stack.last()
                while (token2 != null
                    && operators.containsKey(token2)
                    && (o1.isLeftAssoc && o1.precedence <= operators[token2]!!.precedence
                            || o1.precedence < operators[token2]!!.precedence)
                ) {
                    outputQueue.add(stack.removeLast())
                    token2 = if (stack.isEmpty()) null else stack.last()
                }
                stack.addLast(token)
            } else if ("(" == token) {
                if (previousToken != null) {
                    if (isNumber(previousToken)) {
                        throw ExpressionException("Missing operator at character position ${tokenizer.pos}")
                    }
                    if (vMap.hasFunction(previousToken) || vMap.hasGameObject(previousToken)) {
                        outputQueue.add(token)
                    }
                }
                stack.addLast(token)
            } else if (")" == token) {
                if (operators.containsKey(previousToken)) {
                    throw ExpressionException(
                        "Missing parameter(s) for operator $previousToken at character position ${tokenizer.pos - 1 - previousToken!!.length}"
                    )
                }
                while (stack.isNotEmpty() && "(" != stack.last()) {
                    outputQueue.add(stack.removeLast())
                }
                if (stack.isEmpty()) {
                    throw ExpressionException("Mismatched parentheses")
                }
                stack.removeLast()
                if (stack.isNotEmpty() && (vMap.hasFunction(stack.last()) || vMap.hasGameObject(stack.last()))) {
                    outputQueue.add(stack.removeLast())
                }
            }
            previousToken = token
        }
        while (stack.isNotEmpty()) {
            val element = stack.removeLast()
            if ("(" == element || ")" == element) {
                throw ExpressionException("Mismatched parentheses")
            }
            if (!operators.containsKey(element)) {
                throw ExpressionException("Unknown operator or function: $element")
            }
            outputQueue.add(element)
        }
        return outputQueue
    }

    fun eval(vMap: VariableMap): Any {
        val stack = ArrayDeque<Any>()
        for (token in getRPN(vMap)) {
            if (isNumber(token)) {
                stack.addLast(token.toDouble())
            } else if (operators.containsKey(token)) {
                val v1 = stack.removeLast()
                val v2 = stack.removeLast()
                stack.addLast(operators[token]!!.eval(v2, v1))
            } else if (vMap.hasValue(token)) {
                stack.addLast(vMap.getValue(token))
            } else if (vMap.hasFunction(token)) {
                val f = vMap.getFunction(token)
                val p = mutableListOf<Any>()
                while (stack.isNotEmpty() && stack.last() !== PARAMS_START) {
                    val param = stack.removeLast()
                    if (param is String)
                        p.add(0, stripStringParameter(param))
                    else
                        p.add(0, param)
                }
                if (stack.isNotEmpty() && stack.last() === PARAMS_START) {
                    stack.removeLast()
                }
                if (f.isFixedNumParams && p.size != f.numParams) {
                    throw ExpressionException(
                        "Function $token expected ${f.numParams} parameters, got ${p.size}"
                    )
                }
                stack.addLast(f.eval(p, vMap))
            } else if (vMap.hasGameObject(token)) {
                val vr = token.substring(0, token.indexOf("."))
                val function = token.substring(token.indexOf(".") + 1)
                val vl = vMap.getValue(vr)
                val p = mutableListOf<Any>()
                while (stack.isNotEmpty() && stack.last() !== PARAMS_START) {
                    val obj = stack.removeLast()
                    if (obj is String)
                        p.add(0, stripStringParameter(obj))
                    else
                        p.add(0, obj)
                }
                if (stack.isNotEmpty() && stack.last() === PARAMS_START) {
                    stack.removeLast()
                }
                if (vl is Double && vl == 0.0) {
                    stack.addLast(0.0)
                } else {
                    // KMP-compatible: use pluggable GameObjectResolver instead of Java reflection
                    val resolver = gameObjectResolver
                    if (resolver != null) {
                        try {
                            var fResult: Any = resolver.call(vl, function, p)
                            fResult = when (fResult) {
                                is Boolean -> if (fResult) 1.0 else 0.0
                                is Int -> fResult.toDouble()
                                is Float -> fResult.toDouble()
                                else -> fResult
                            }
                            stack.addLast(fResult)
                        } catch (e: Exception) {
                            throw InkRunTimeException(
                                "Could not call method $function on variable $vr with ${p.size} params. ${vMap.debugInfo()}", e
                            )
                        }
                    } else {
                        throw InkRunTimeException(
                            "Game object method call $vr.$function requires a GameObjectResolver. ${vMap.debugInfo()}"
                        )
                    }
                }
            } else if ("(" == token) {
                stack.addLast(PARAMS_START)
            } else {
                stack.addLast(token)
            }
        }
        val obj: Any? = stack.removeLast()
        when (obj) {
            is Double -> {
                // Strip trailing zeros: return Int-like doubles as clean doubles
                return if (obj == obj.toLong().toDouble()) obj.toLong().toDouble() else obj
            }
            is String -> if (isStringParameter(obj)) return stripStringParameter(obj)
        }
        return obj ?: 0
    }

    private fun addOperator(oper: Operator) {
        operators[oper.oper] = oper
    }

    private fun getRPN(vMap: VariableMap): List<String> {
        if (rpn == null) {
            rpn = shuntingYard(expression, vMap)
            validate(rpn!!, vMap)
        }
        return rpn!!
    }

    private fun validate(rpn: List<String>, vMap: VariableMap) {
        val stack = ArrayDeque<Int>()
        stack.addLast(0)
        for (token in rpn) {
            if (operators.containsKey(token)) {
                if (stack.last() < 2) {
                    throw ExpressionException("Missing parameter(s) for operator $token")
                }
                stack[stack.size - 1] = stack.last() - 2 + 1
            } else if (vMap.hasValue(token)) {
                stack[stack.size - 1] = stack.last() + 1
            } else if (vMap.hasFunction(token.uppercase())) {
                val f = vMap.getFunction(token.uppercase())
                val numParams = stack.removeLast()
                if (f.isFixedNumParams && numParams != f.numParams) {
                    throw ExpressionException(
                        "Function $token expected ${f.numParams} parameters, got $numParams"
                    )
                }
                if (stack.size <= 0) {
                    throw ExpressionException("Too many function calls, maximum scope exceeded")
                }
                stack[stack.size - 1] = stack.last() + 1
            } else if (vMap.hasGameObject(token)) {
                stack.removeLast()
                if (stack.size <= 0) {
                    throw ExpressionException("Too many function calls, maximum scope exceeded")
                }
                stack[stack.size - 1] = stack.last() + 1
            } else if ("(" == token) {
                stack.addLast(0)
            } else {
                stack[stack.size - 1] = stack.last() + 1
            }
        }
        if (stack.size > 1) {
            throw ExpressionException("Too many unhandled function parameter lists")
        } else if (stack.last() > 1) {
            throw ExpressionException("Too many numbers or values")
        } else if (stack.last() < 1) {
            throw ExpressionException("Empty expression")
        }
    }

    override fun toString(): String = expression

    interface LazyNumber {
        fun eval(): Any
    }

    companion object {
        val PI = kotlin.math.PI
        val e = kotlin.math.E
        private const val decimalSeparator = '.'
        private const val minusSign = '-'
        private val PARAMS_START = object : LazyNumber {
            override fun eval(): Double = 0.0
        }

        fun isNumber(st: String): Boolean {
            if (st[0] == minusSign && st.length == 1) return false
            if (st[0] == '+' && st.length == 1) return false
            if (st[0] == 'e' || st[0] == 'E') return false
            return st.none { !it.isDigit() && it != minusSign && it != decimalSeparator && it != 'e' && it != 'E' && it != '+' }
        }

        private fun isStringParameter(st: String): Boolean =
            st.startsWith("\"") && st.endsWith("\"")

        private fun stripStringParameter(st: String): String {
            if (st.startsWith("\"") && st.endsWith("\""))
                return st.substring(1, st.length - 1)
            if (st.startsWith("\'") && st.endsWith("\'"))
                return st.substring(1, st.length - 1)
            return st
        }
    }
}
