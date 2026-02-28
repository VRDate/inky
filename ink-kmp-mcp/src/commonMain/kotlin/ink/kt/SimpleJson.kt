package ink.kt

import kotlinx.serialization.json.*

/**
 * JSON serialisation — kotlinx.serialization reader + streaming writer.
 *
 * Reader: delegates to kotlinx.serialization.json (KMP-compatible, well-tested).
 * Writer: streaming StringBuilder state machine (unchanged from original).
 *
 * The Reader converts JsonElement trees to the untyped Any? maps/lists that the
 * ink runtime expects (LinkedHashMap<String, Any?> and MutableList<Any?>).
 *
 * Number handling preserves ink runtime semantics:
 * - Integer literals (no '.', 'e', 'E') → Int
 * - Float literals (with '.', 'e', 'E') → Float
 * - Critical: "0.0" must parse as Float, not Int (ink runtime depends on this)
 */
object SimpleJson {

    private val json = Json { isLenient = false }

    fun textToDictionary(text: String): LinkedHashMap<String, Any?> {
        val element = try {
            json.parseToJsonElement(text)
        } catch (e: Exception) {
            throw StoryException("Failed to parse JSON: ${e.message}")
        }
        val obj = element as? JsonObject
            ?: throw StoryException("Expected JSON object but got: ${element::class.simpleName}")
        return jsonObjectToLinkedHashMap(obj)
    }

    fun textToArray(text: String): List<Any?> {
        val element = try {
            json.parseToJsonElement(text)
        } catch (e: Exception) {
            throw StoryException("Failed to parse JSON: ${e.message}")
        }
        val arr = element as? JsonArray
            ?: throw StoryException("Expected JSON array but got: ${element::class.simpleName}")
        return jsonArrayToList(arr)
    }

    // ── kotlinx JsonElement → untyped Any? conversion ─────────────

    private fun jsonElementToAny(element: JsonElement): Any? = when (element) {
        is JsonNull -> null
        is JsonObject -> jsonObjectToLinkedHashMap(element)
        is JsonArray -> jsonArrayToList(element)
        is JsonPrimitive -> jsonPrimitiveToAny(element)
    }

    private fun jsonObjectToLinkedHashMap(obj: JsonObject): LinkedHashMap<String, Any?> {
        val map = LinkedHashMap<String, Any?>(obj.size)
        for ((key, value) in obj) {
            map[key] = jsonElementToAny(value)
        }
        return map
    }

    private fun jsonArrayToList(arr: JsonArray): MutableList<Any?> {
        val list = ArrayList<Any?>(arr.size)
        for (element in arr) {
            list.add(jsonElementToAny(element))
        }
        return list
    }

    /**
     * Convert a JSON primitive to the appropriate Kotlin type.
     *
     * Preserves ink runtime number semantics:
     * - Booleans → Boolean
     * - Strings → String
     * - Numbers with '.', 'e', 'E' → Float (ink runtime expects Float, not Double)
     * - Numbers without decimal/exponent → Int
     */
    private fun jsonPrimitiveToAny(primitive: JsonPrimitive): Any? {
        if (primitive is JsonNull) return null

        // Boolean
        if (primitive.booleanOrNull != null) return primitive.boolean

        // String (quoted)
        if (primitive.isString) return primitive.content

        // Number — determine Int vs Float from the raw content
        val content = primitive.content
        val isFloat = '.' in content || 'e' in content || 'E' in content

        if (isFloat) {
            return content.toFloatOrNull()
                ?: throw StoryException("Failed to parse float value: $content")
        } else {
            return content.toIntOrNull()
                ?: throw StoryException("Failed to parse integer value: $content")
        }
    }

    // ── Writer ────────────────────────────────────────────────────

    /**
     * SAM callback for nested write operations.
     * C#: Action<Writer>, Java: InnerWriter interface, JS: function callback
     * Kotlin: fun interface — SAM conversion, lambdas auto-convert
     */
    fun interface InnerWriter {
        fun write(w: Writer)
    }

    /**
     * State machine JSON writer — StringBuilder only (no Stream, zero deps).
     * C#: supports StringWriter + Stream, Java: StringWriter + OutputStream
     * Kotlin: StringBuilder only — commonMain compatible, sufficient for ink runtime
     */
    class Writer {

        private val sb = StringBuilder()
        private val stateStack = ArrayDeque<StateElement>()

        // ── Object methods ──

        fun writeObject(inner: InnerWriter) {
            writeObjectStart()
            inner.write(this)
            writeObjectEnd()
        }

        fun writeObjectStart() {
            startNewObject(container = true)
            stateStack.addLast(StateElement(State.Object))
            sb.append('{')
        }

        fun writeObjectEnd() {
            assert(state == State.Object)
            sb.append('}')
            stateStack.removeLast()
        }

        // ── Property methods ──

        fun writeProperty(name: String, inner: InnerWriter) {
            writePropertyStart(name)
            inner.write(this)
            writePropertyEnd()
        }

        fun writeProperty(id: Int, inner: InnerWriter) {
            writePropertyStart(id.toString())
            inner.write(this)
            writePropertyEnd()
        }

        fun writeProperty(name: String, content: String) {
            writePropertyStart(name)
            write(content)
            writePropertyEnd()
        }

        fun writeProperty(name: String, content: Int) {
            writePropertyStart(name)
            write(content)
            writePropertyEnd()
        }

        fun writeProperty(name: String, content: Boolean) {
            writePropertyStart(name)
            write(content)
            writePropertyEnd()
        }

        fun writePropertyStart(name: String) {
            assert(state == State.Object)

            if (childCount > 0) sb.append(',')

            sb.append('"')
            sb.append(name)
            sb.append("\":")

            incrementChildCount()

            stateStack.addLast(StateElement(State.Property))
        }

        fun writePropertyStart(id: Int) {
            writePropertyStart(id.toString())
        }

        fun writePropertyEnd() {
            assert(state == State.Property)
            assert(childCount == 1)
            stateStack.removeLast()
        }

        fun writePropertyNameStart() {
            assert(state == State.Object)

            if (childCount > 0) sb.append(',')

            sb.append('"')

            incrementChildCount()

            stateStack.addLast(StateElement(State.Property))
            stateStack.addLast(StateElement(State.PropertyName))
        }

        fun writePropertyNameEnd() {
            assert(state == State.PropertyName)
            sb.append("\":")
            // Pop PropertyName, leaving Property state
            stateStack.removeLast()
        }

        fun writePropertyNameInner(str: String) {
            assert(state == State.PropertyName)
            sb.append(str)
        }

        // ── Array methods ──

        fun writeArrayStart() {
            startNewObject(container = true)
            stateStack.addLast(StateElement(State.Array))
            sb.append('[')
        }

        fun writeArrayEnd() {
            assert(state == State.Array)
            sb.append(']')
            stateStack.removeLast()
        }

        // ── Value methods ──

        fun write(i: Int) {
            startNewObject(container = false)
            sb.append(i)
        }

        /**
         * Write float value with special handling for Infinity/NaN.
         * C#: f.ToString(InvariantCulture), checks string for "Infinity"/"NaN"
         * Java: Float.toString(f) — has == bug (should use .equals), Kotlin fixes this
         * Both ensure ".0" suffix for whole-number floats to preserve float type on read-back
         */
        fun write(f: Float) {
            startNewObject(container = false)

            val floatStr = f.toString()

            when {
                floatStr == "Infinity" -> sb.append("3.4E+38")
                floatStr == "-Infinity" -> sb.append("-3.4E+38")
                floatStr == "NaN" -> sb.append("0.0")
                else -> {
                    sb.append(floatStr)
                    if ('.' !in floatStr && 'E' !in floatStr) {
                        sb.append(".0") // ensure it reads back as float
                    }
                }
            }
        }

        fun write(str: String, escape: Boolean = true) {
            startNewObject(container = false)
            sb.append('"')
            if (escape) writeEscapedString(str) else sb.append(str)
            sb.append('"')
        }

        fun write(b: Boolean) {
            startNewObject(container = false)
            sb.append(if (b) "true" else "false")
        }

        fun writeNull() {
            startNewObject(container = false)
            sb.append("null")
        }

        // ── String building methods ──

        fun writeStringStart() {
            startNewObject(container = false)
            stateStack.addLast(StateElement(State.String))
            sb.append('"')
        }

        fun writeStringEnd() {
            assert(state == State.String)
            sb.append('"')
            stateStack.removeLast()
        }

        fun writeStringInner(str: String, escape: Boolean = true) {
            assert(state == State.String)
            if (escape) writeEscapedString(str) else sb.append(str)
        }

        // ── Utility ──

        fun clear() {
            sb.clear()
            stateStack.clear()
        }

        override fun toString(): String = sb.toString()

        // ── Private ──

        /**
         * Escape special characters for JSON string output.
         * C#/Java/Kotlin: only escape \n, \t among control chars. Others silently dropped.
         */
        private fun writeEscapedString(str: String) {
            for (c in str) {
                if (c < ' ') {
                    // Only escape \n and \t among control characters — matches C#/Java
                    when (c) {
                        '\n' -> sb.append("\\n")
                        '\t' -> sb.append("\\t")
                        // Other control chars silently dropped
                    }
                } else {
                    when (c) {
                        '\\', '"' -> {
                            sb.append('\\')
                            sb.append(c)
                        }
                        else -> sb.append(c)
                    }
                }
            }
        }

        private fun startNewObject(container: Boolean) {
            if (container) {
                assert(state == State.None || state == State.Property || state == State.Array)
            } else {
                assert(state == State.Property || state == State.Array)
            }

            if (state == State.Array && childCount > 0) sb.append(',')

            if (state == State.Property) assert(childCount == 0)

            if (state == State.Array || state == State.Property) incrementChildCount()
        }

        private val state: State
            get() = if (stateStack.isNotEmpty()) stateStack.last().type else State.None

        private val childCount: Int
            get() = if (stateStack.isNotEmpty()) stateStack.last().childCount else 0

        private fun incrementChildCount() {
            check(stateStack.isNotEmpty())
            stateStack.last().childCount++
        }

        /**
         * Debug-only assertion — matches C# [Conditional("DEBUG")] and Java Assert.
         * In Kotlin, we throw StoryException so tests can verify error conditions.
         */
        private fun assert(condition: Boolean) {
            if (!condition) throw StoryException("Assert failed while writing JSON")
        }

        private enum class State {
            None, Object, Array, Property, PropertyName, String
        }

        /**
         * C# uses struct StateElement { State type, int childCount }.
         * Java uses class StateElement with constructor.
         * Kotlin: simple class (not data class — mutable childCount).
         * Using ArrayDeque instead of Stack (no synchronized overhead).
         */
        private class StateElement(
            val type: State,
            var childCount: Int = 0
        )
    }
}
