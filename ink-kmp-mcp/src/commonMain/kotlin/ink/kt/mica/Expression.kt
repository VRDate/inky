package ink.kt.mica

import ink.kt.mica.util.InkRunTimeException
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * A one-class expression evaluator.
 * Originally based on https://github.com/uklimaschewski/EvalEx
 *
 * Creates a new expression instance from an expression string with a given default math context.
 * @param originalExpression The expression, e.g. `"2.4*sin(3)/(2-4)"` or `"sin(y)>0 & max(z, 3)>3"`
 * @param defaultMathContext The [MathContext] to use by default
 */
class Expression(
    originalExpression: String,
    private val defaultMathContext: MathContext = MathContext.DECIMAL32
) {
    class ExpressionException(message: String) : RuntimeException(message)

    private var mc = defaultMathContext
    private var rpn: List<String>? = null
    private val firstVarChars: String = "_"
    private val varChars: String = "_."
    private val operators = sortedMapOf<String, Operator>(String.CASE_INSENSITIVE_ORDER)
    private var expression: String = originalExpression.trim()
        private set

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
        this.mc = defaultMathContext
        this.expression = originalExpression
        addOperator(object : Operator("+", 20, true) {
            override fun eval(v1: Any, v2: Any): Any = when (v1) {
                is BigDecimal -> v1.add(v2 as BigDecimal, mc)
                is String -> stripStringParameter(v1) + stripStringParameter(v2.toString())
                else -> BigDecimal.ZERO
            }
        })
        addOperator(object : Operator("-", 20, true) {
            override fun eval(v1: Any, v2: Any): Any = when (v1) {
                is BigDecimal -> v1.subtract(v2 as BigDecimal, mc)
                else -> BigDecimal.ZERO
            }
        })
        addOperator(object : Operator("*", 30, true) {
            override fun eval(v1: Any, v2: Any): Any = when (v1) {
                is BigDecimal -> v1.multiply(v2 as BigDecimal, mc)
                else -> BigDecimal.ZERO
            }
        })
        addOperator(object : Operator("/", 30, true) {
            override fun eval(v1: Any, v2: Any): Any = when (v1) {
                is BigDecimal -> v1.divide(v2 as BigDecimal, mc)
                else -> BigDecimal.ZERO
            }
        })
        addOperator(object : Operator("%", 30, true) {
            override fun eval(v1: Any, v2: Any): Any = when (v1) {
                is BigDecimal -> v1.remainder(v2 as BigDecimal, mc)
                else -> BigDecimal.ZERO
            }
        })
        addOperator(object : Operator("^", 40, false) {
            override fun eval(v1: Any, v2: Any): Any {
                if (v1 !is BigDecimal || v2 !is BigDecimal) return BigDecimal.ZERO
                var v2m = v2
                val signOf2 = v2m.signum()
                val dn1 = v1.toDouble()
                v2m = v2m.multiply(BigDecimal(signOf2))
                val remainderOf2 = v2m.remainder(BigDecimal.ONE)
                val n2IntPart = v2m.subtract(remainderOf2)
                val intPow = v1.pow(n2IntPart.intValueExact(), mc)
                val doublePow = BigDecimal(Math.pow(dn1, remainderOf2.toDouble()))
                var result = intPow.multiply(doublePow, mc)
                if (signOf2 == -1) {
                    result = BigDecimal.ONE.divide(result, mc.precision, RoundingMode.HALF_UP)
                }
                return result
            }
        })
        addOperator(object : Operator("&&", 4, false) {
            override fun eval(v1: Any, v2: Any): Any {
                val b1 = v1 != BigDecimal.ZERO
                val b2 = v2 != BigDecimal.ZERO
                return if (b1 && b2) BigDecimal.ONE else BigDecimal.ZERO
            }
        })
        addOperator(object : Operator("||", 2, false) {
            override fun eval(v1: Any, v2: Any): Any {
                val b1 = v1 != BigDecimal.ZERO
                val b2 = v2 != BigDecimal.ZERO
                return if (b1 || b2) BigDecimal.ONE else BigDecimal.ZERO
            }
        })
        addOperator(object : Operator(">", 10, false) {
            override fun eval(v1: Any, v2: Any): Any {
                if (v1 !is BigDecimal || v2 !is BigDecimal) return BigDecimal.ZERO
                return if (v1.compareTo(v2) == 1) BigDecimal.ONE else BigDecimal.ZERO
            }
        })
        addOperator(object : Operator(">=", 10, false) {
            override fun eval(v1: Any, v2: Any): Any {
                if (v1 !is BigDecimal || v2 !is BigDecimal) return BigDecimal.ZERO
                return if (v1 >= v2) BigDecimal.ONE else BigDecimal.ZERO
            }
        })
        addOperator(object : Operator("<", 10, false) {
            override fun eval(v1: Any, v2: Any): Any {
                if (v1 !is BigDecimal || v2 !is BigDecimal) return BigDecimal.ZERO
                return if (v1.compareTo(v2) == -1) BigDecimal.ONE else BigDecimal.ZERO
            }
        })
        addOperator(object : Operator("<=", 10, false) {
            override fun eval(v1: Any, v2: Any): Any {
                if (v1 !is BigDecimal || v2 !is BigDecimal) return BigDecimal.ZERO
                return if (v1 <= v2) BigDecimal.ONE else BigDecimal.ZERO
            }
        })
        addOperator(object : Operator("==", 7, false) {
            override fun eval(v1: Any, v2: Any): Any = operators["="]!!.eval(v1, v2)
        })
        addOperator(object : Operator("=", 7, false) {
            override fun eval(v1: Any, v2: Any): Any = when (v1) {
                is BigDecimal -> if (v1.compareTo(v2 as BigDecimal) == 0) BigDecimal.ONE else BigDecimal.ZERO
                is String -> if (stripStringParameter(v1).compareTo(stripStringParameter(v2 as String)) == 0) BigDecimal.ONE else BigDecimal.ZERO
                else -> BigDecimal.ZERO
            }
        })
        addOperator(object : Operator("!=", 7, false) {
            override fun eval(v1: Any, v2: Any): Any = when (v1) {
                is BigDecimal -> if (v1.compareTo(v2 as BigDecimal) != 0) BigDecimal.ONE else BigDecimal.ZERO
                is String -> if (stripStringParameter(v1).compareTo(stripStringParameter(v2 as String)) != 0) BigDecimal.ONE else BigDecimal.ZERO
                else -> BigDecimal.ZERO
            }
        })
        addOperator(object : Operator("<>", 7, false) {
            override fun eval(v1: Any, v2: Any): Any = operators["!="]!!.eval(v1, v2)
        })
        addOperator(object : Operator("?:", 40, false) {
            override fun eval(v1: Any, v2: Any): Any = when (v2) {
                is BigDecimal -> if (v1 is BigDecimal && v1 == BigDecimal.ZERO) v2 else v1
                is String -> if (v1 is BigDecimal && v1 == BigDecimal.ZERO) stripStringParameter(v2) else v1
                else -> if (v1 is BigDecimal && v1 == BigDecimal.ZERO) v2 else v1
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
                stack.addLast(BigDecimal(token, mc))
            } else if (operators.containsKey(token)) {
                val v1 = stack.removeLast()
                val v2 = stack.removeLast()
                stack.addLast(operators[token]!!.eval(v2, v1))
            } else if (vMap.hasValue(token)) {
                val obj = vMap.getValue(token)
                when (obj) {
                    is BigDecimal -> stack.addLast(obj.round(mc))
                    else -> stack.addLast(obj)
                }
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
                if (vl is BigDecimal && vl == BigDecimal.ZERO) {
                    stack.addLast(BigDecimal.ZERO)
                } else {
                    // TODO: Replace Java reflection with Kotlin-compatible approach for KMP
                    // For now, game object method calls require JVM reflection
                    val paramTypes = p.map { it.javaClass }.toTypedArray()
                    val params = p.toTypedArray()
                    val valClass = vl.javaClass
                    try {
                        val m = valClass.getMethod(function, *paramTypes)
                        var fResult: Any = m.invoke(vl, *params) ?: BigDecimal.ZERO
                        fResult = when (fResult) {
                            is Boolean -> if (fResult) BigDecimal.ONE else BigDecimal.ZERO
                            is Int -> BigDecimal(fResult)
                            is Float -> BigDecimal(fResult.toDouble())
                            is Double -> BigDecimal(fResult)
                            else -> fResult
                        }
                        stack.addLast(fResult)
                    } catch (e: Exception) {
                        val errMsg = buildString {
                            append("Could not call method $function on variable $vr ")
                            append("($vr, ${valClass.name}) with parameters:")
                            paramTypes.forEach { append(" ${it.name}") }
                            append(". ${vMap.debugInfo()}")
                        }
                        throw InkRunTimeException(errMsg, e)
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
            is BigDecimal -> return obj.stripTrailingZeros()
            is String -> if (isStringParameter(obj)) return stripStringParameter(obj)
        }
        return obj ?: 0
    }

    @Suppress("unused")
    fun setPrecision(precision: Int): Expression {
        this.mc = MathContext(precision)
        return this
    }

    @Suppress("unused")
    fun setRoundingMode(roundingMode: RoundingMode): Expression {
        this.mc = MathContext(mc.precision, roundingMode)
        return this
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
        val PI = BigDecimal("3.1415926535897932384626433832795028841971693993751058209749445923078164062862089986280348253421170679")
        val e = BigDecimal("2.71828182845904523536028747135266249775724709369995957496696762772407663")
        private const val decimalSeparator = '.'
        private const val minusSign = '-'
        private val PARAMS_START = object : LazyNumber {
            override fun eval(): BigDecimal = BigDecimal(0)
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
