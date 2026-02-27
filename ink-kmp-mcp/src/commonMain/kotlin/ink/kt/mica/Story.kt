package ink.kt.mica

import ink.kt.mica.util.InkRunTimeException
import kotlin.random.Random

class Story(
    internal val wrapper: StoryWrapper,
    fileName: String,
    internal var container: Container,
    internal val content: MutableMap<String, Content>
) : VariableMap {

    // Story Content
    internal val fileNames: MutableList<String> = mutableListOf()
    private val interrupts = mutableListOf<StoryInterrupt>()
    private val storyEnd = Knot("== END ==", 0)
    private var endProcessing = false
    private var currentText = Symbol.GLUE
    val text: MutableList<String> = mutableListOf()
    val choices = mutableListOf<Container>()
    internal val variables = mutableMapOf<String, Any>()
    private val functions = sortedMapOf<String, Function>(String.CASE_INSENSITIVE_ORDER)

    init {
        fileNames.add(fileName)
        content[storyEnd.id] = storyEnd
        for (cnt in content) {
            if (cnt.value is Knot && (cnt.value as Knot).isFunction)
                functions[cnt.value.id.lowercase()] = cnt.value as Knot
        }
        putVariable(Symbol.TRUE, 1.0)
        putVariable(Symbol.FALSE, 0.0)
        putVariable(Symbol.PI, Expression.PI)
        putVariable(Symbol.e, Expression.e)
        functions[IS_NULL] = IsNullFunction()
        functions[NOT] = NotFunction()
        functions[RANDOM] = RandomFunction()
        functions[FLOOR] = FloorFunction()
    }

    fun add(story: Story) {
        content.putAll(story.content)
        functions.putAll(story.functions)
        variables.putAll(story.variables)
        story.fileNames
            .filterNot { fileNames.contains(it) }
            .forEach { fileNames.add(it) }
    }

    fun next(): List<String> {
        choices.clear()
        currentText = Symbol.GLUE
        endProcessing = false
        while (container.index >= container.size)
            increment()
        while (!endProcessing) {
            val current = container.get(container.index)
            when (current) {
                is Stitch -> {
                    if (container.index == 0) {
                        container.index = container.size
                        container = current
                        container.index = 0
                        current.count++
                    } else {
                        container.index = container.size
                        increment()
                    }
                }
                is Choice -> {
                    if (current.isFallBack()) {
                        if (choices.isEmpty()) {
                            container.index++
                            container = current
                            container.index = 0
                            current.count++
                        } else {
                            increment()
                        }
                    } else {
                        if (current.evaluateConditions(this))
                            choices.add(current)
                        increment()
                    }
                }
                is Gather -> {
                    if (choices.isNotEmpty()) {
                        endProcessing = true
                    } else {
                        container.index++
                        container = current
                        container.index = 0
                        current.count++
                    }
                }
                is Conditional -> {
                    container.index++
                    container = current.resolveConditional(this)
                    current.count++
                    container.index = 0
                    if (container.index >= container.size)
                        increment()
                }
                is Declaration -> {
                    current.evaluate(this)
                    increment()
                }
                is Divert -> {
                    container.index++
                    container = current.resolveDivert(this)
                    container.count++
                    container.index = 0
                }
                is Tag -> {
                    wrapper.resolveTag(current.text)
                    increment()
                }
                else -> {
                    addText(current)
                    current.count++
                    increment()
                }
            }
            if (container.id == storyEnd.id)
                endProcessing = true
        }
        if (currentText.isNotEmpty()) {
            val txt = cleanUpText(currentText)
            if (txt.isNotEmpty())
                text.add(txt)
        }
        return text
    }

    private fun increment() {
        container.index++
        while (container.index >= container.size && !endProcessing) {
            when (container) {
                is Choice -> container = container.parent!!
                is Gather -> container = container.parent!!
                is Conditional -> container = container.parent!!
                is ConditionalOption -> container = container.parent!!
                else -> endProcessing = true
            }
        }
    }

    private fun addText(current: Content) {
        val nextText = current.getText(this)
        if (currentText.endsWith(Symbol.GLUE) || nextText.startsWith(Symbol.GLUE))
            currentText += nextText
        else {
            text.add(cleanUpText(currentText))
            currentText = nextText
        }
    }

    fun choose(idx: Int) {
        val i = if (idx == -1) choices.size - 1 else idx
        if (i < choices.size && i >= 0) {
            container = choices[i]
            container.count++
            container.index = 0
            choices.clear()
        } else {
            val cId = container.id
            throw InkRunTimeException(
                "Trying to select choice $i that does not exist in story: ${fileNames[0]} container: $cId cIndex: ${container.index}"
            )
        }
    }

    val choiceSize: Int get() = choices.size

    fun choiceText(i: Int): String {
        if (i >= choices.size || i < 0)
            throw InkRunTimeException(
                "Trying to retrieve choice $i that does not exist in story: ${fileNames[0]} container: ${container.id} cIndex: ${container.index}"
            )
        return (choices[i] as Choice).getText(this)
    }

    fun putVariable(key: String, value: Any) {
        var c: Container? = container
        while (c != null) {
            if (c is Knot || c is Function || c is Stitch) {
                if ((c as ParameterizedContainer).hasValue(key)) {
                    when (value) {
                        is Boolean -> c.setValue(key, if (value) 1.0 else 0.0)
                        is Int -> c.setValue(key, value.toDouble())
                        else -> c.setValue(key, value)
                    }
                    return
                }
            }
            c = c.parent
        }
        variables[key] = value
    }

    fun putVariables(map: Map<String, Any>) {
        variables.putAll(map)
    }

    val isEnded: Boolean get() = container.id == storyEnd.id

    fun setContainer(s: String) {
        container = content[s] as Container
    }

    override fun logException(e: Exception) {
        wrapper.logException(e)
    }

    override fun getValue(token: String): Any {
        if (Symbol.THIS == token)
            return container.id
        var c: Container? = container
        while (c != null) {
            if (c is ParameterizedContainer && c.hasValue(token))
                return c.getValue(token)
            c = c.parent
        }
        if (token.startsWith(Symbol.DIVERT)) {
            val k = token.substring(2).trim()
            if (content.containsKey(k)) return content[k]!!
            wrapper.logException(InkRunTimeException("Could not identify container id: $k"))
            return 0.0
        }
        if (content.containsKey(token)) {
            val storyContainer = content[token]!!
            return storyContainer.count.toDouble()
        }
        val pathId = getValueId(token)
        if (content.containsKey(pathId)) {
            val storyContainer = content[pathId]!!
            return storyContainer.count.toDouble()
        }
        val knotId = getKnotId(token)
        if (content.containsKey(knotId)) {
            val storyContainer = content[knotId]!!
            return storyContainer.count.toDouble()
        }
        if (container is ParameterizedContainer && (container as ParameterizedContainer).hasValue(token))
            return (container as ParameterizedContainer).getValue(token)
        if (variables.containsKey(token))
            return variables[token]!!
        wrapper.logException(InkRunTimeException("Could not identify the variable $token or $pathId"))
        return 0.0
    }

    private fun getValueId(id: String): String {
        if (id.contains(Symbol.DOT.toString())) return id
        return "${container.id}${Symbol.DOT}$id"
    }

    private fun getKnotId(id: String): String {
        if (id.contains(Symbol.DOT.toString())) return id
        var knot: Container? = container
        while (knot != null) {
            if (knot is Knot) return "${knot.id}${Symbol.DOT}$id"
            knot = knot.parent
        }
        return id
    }

    override fun hasValue(token: String): Boolean {
        if (functions.containsKey(token)) return false
        if (content.containsKey(token)) return true
        if (content.containsKey(getValueId(token))) return true
        if (content.containsKey(getKnotId(token))) return true
        if (container is ParameterizedContainer && (container as ParameterizedContainer).hasValue(token))
            return true
        return variables.containsKey(token)
    }

    override fun hasFunction(token: String): Boolean =
        functions.containsKey(token.lowercase())

    override fun getFunction(token: String): Function {
        if (hasFunction(token.lowercase()))
            return functions[token.lowercase()]!!
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
        append("StoryDebugInfo File: $fileNames")
        append(" Container: ${container.id}")
        if (container.index < container.size) {
            val cnt = container.get(container.index)
            append(" Line#: ${cnt.lineNumber}")
        }
    }

    private class IsNullFunction : Function {
        override val numParams: Int = 1
        override val isFixedNumParams: Boolean = true
        override fun eval(params: List<Any>, vMap: VariableMap): Any {
            val param = params[0]
            return if (param == 0.0) 1.0 else 0.0
        }
    }

    class NotFunction : Function {
        override val numParams: Int = 1
        override val isFixedNumParams: Boolean = true
        override fun eval(params: List<Any>, vMap: VariableMap): Any {
            return when (val param = params[0]) {
                is Boolean -> !param
                is Double -> if (param == 0.0) 1.0 else 0.0
                else -> 0.0
            }
        }
    }

    private class RandomFunction : Function {
        override val numParams: Int = 1
        override val isFixedNumParams: Boolean = true
        override fun eval(params: List<Any>, vMap: VariableMap): Any {
            val param = params[0]
            if (param is Double) {
                val v = param.toInt()
                return if (v > 0) Random.nextInt(v).toDouble() else 0.0
            }
            return 0.0
        }
    }

    private class FloorFunction : Function {
        override val numParams: Int = 1
        override val isFixedNumParams: Boolean = true
        override fun eval(params: List<Any>, vMap: VariableMap): Any {
            val param = params[0]
            if (param is Double) {
                return param.toInt().toDouble()
            }
            return 0.0
        }
    }

    fun resolveInterrupt(divert: String): String {
        for (interrupt in interrupts) {
            if (interrupt.isActive && interrupt.isDivert) {
                try {
                    val res = Declaration.evaluate(interrupt.condition, this)
                    if (checkResult(res)) {
                        val text = interrupt.text.substring(2)
                        val from = text.substring(0, text.indexOf(Symbol.DIVERT)).trim()
                        if (from == divert) {
                            if (!contains(interrupt.file))
                                add(InkParser.parse(wrapper, interrupt.file))
                            val to = text.substring(text.indexOf(Symbol.DIVERT) + 2).trim()
                            interrupt.isActive = false
                            putVariable(Symbol.EVENT, interrupt)
                            return to
                        }
                    }
                } catch (e: InkRunTimeException) {
                    wrapper.logException(e)
                    return divert
                }
            }
        }
        return divert
    }

    fun addInterrupt(i: StoryInterrupt) = interrupts.add(i)

    fun clearInterrupts() = interrupts.clear()

    fun contains(s: String): Boolean = fileNames.any { it.equals(s, ignoreCase = true) }

    companion object {
        private const val IS_NULL = "isnull"
        private const val NOT = "not"
        private const val RANDOM = "random"
        private const val FLOOR = "floor"

        private fun cleanUpText(str: String): String =
            str.replace(Symbol.GLUE.toRegex(), " ")
                .replace("\\s+".toRegex(), " ")
                .trim()
    }

    fun getDivert(d: String): Container {
        if (content.containsKey(d))
            return content[d] as Container
        if (content.containsKey(getValueId(d)))
            return content[getValueId(d)] as Container
        if (content.containsKey(getKnotId(d)))
            return content[getKnotId(d)] as Container
        if (variables.containsKey(d)) {
            val t = variables[d]
            if (t is Container) return t
            else throw InkRunTimeException("Attempt to divert to a variable $d which is not a Container")
        }
        throw InkRunTimeException("Attempt to divert to non-defined node $d")
    }

    private fun checkResult(res: Any): Boolean {
        if (res is Boolean) return res
        if (res is Double) return res > 0.0
        return false
    }

    fun clear() = text.clear()
}
