package com.bladecoder.ink.runtime

/**
 * Simple custom JSON serialisation — best of C# (inkle/ink), Java (blade-ink), JS (inkjs).
 *
 * Three-way comparison notes:
 * - C# (659 lines): static class, Reader private, Writer public, Stream support
 * - Java (565 lines): static class, Reader package-private, Writer public, OutputStream support
 * - JS (403 lines): delegates to native JSON.parse with float workaround (Safari "123.0f")
 *
 * Kotlin design decisions:
 * - `object` singleton instead of static class (idiomatic Kotlin)
 * - `fun interface InnerWriter` — SAM conversion, lambdas auto-convert (like NativeFunctionCall ops)
 * - `LinkedHashMap` for dictionary results — insertion-order + O(1) (per project directive)
 * - Reader is internal (nested class) — matches C# private, Java package-private
 * - Writer is public (nested class) — matches both C# and Java
 * - No Stream/OutputStream support — StringBuilder only (zero deps, commonMain compatible)
 * - Float formatting: handles Infinity/-Infinity/NaN like C# (Java has == bug, Kotlin uses string comparison)
 * - Unicode escape \uXXXX: Int.toString(16) instead of platform-specific hex parsing
 * - No checked exceptions (Kotlin has none) — throws StoryException on parse errors
 *
 * Zero 3rd party dependencies. Pure Kotlin stdlib.
 */
object SimpleJson {

    fun textToDictionary(text: String): LinkedHashMap<String, Any?> {
        return Reader(text).toDictionary()
    }

    fun textToArray(text: String): List<Any?> {
        return Reader(text).toArray()
    }

    // ── Reader ────────────────────────────────────────────────────

    /**
     * Recursive descent JSON reader.
     * C#: private class Reader { string _text, int _offset }
     * Java: static class Reader { String text, int offset }
     * Kotlin: internal class — matches C# private scope intent
     */
    internal class Reader(private val text: String) {

        private var offset: Int = 0
        private val rootObject: Any?

        init {
            skipWhitespace()
            rootObject = readObject()
        }

        @Suppress("UNCHECKED_CAST")
        fun toDictionary(): LinkedHashMap<String, Any?> =
            rootObject as LinkedHashMap<String, Any?>

        @Suppress("UNCHECKED_CAST")
        fun toArray(): List<Any?> =
            rootObject as List<Any?>

        private fun isNumberChar(c: Char): Boolean =
            c in '0'..'9' || c == '.' || c == '-' || c == '+' || c == 'E' || c == 'e'

        private fun isFirstNumberChar(c: Char): Boolean =
            c in '0'..'9' || c == '-' || c == '+'

        /**
         * Dispatch on first character → typed JSON value.
         * All three implementations share this exact dispatch table.
         */
        private fun readObject(): Any? {
            val currentChar = text[offset]

            return when {
                currentChar == '{' -> readDictionary()
                currentChar == '[' -> readArray()
                currentChar == '"' -> readString()
                isFirstNumberChar(currentChar) -> readNumber()
                tryRead("true") -> true
                tryRead("false") -> false
                tryRead("null") -> null
                else -> {
                    val snippet = text.substring(offset, minOf(offset + 30, text.length))
                    throw StoryException("Unhandled object type in JSON: $snippet")
                }
            }
        }

        /**
         * Read JSON object → LinkedHashMap (insertion-order + O(1)).
         * C#: Dictionary<string,object>, Java: HashMap<String,Object>
         * Kotlin: LinkedHashMap per project directive
         */
        private fun readDictionary(): LinkedHashMap<String, Any?> {
            val dict = LinkedHashMap<String, Any?>()

            expect("{")
            skipWhitespace()

            // Empty dictionary?
            if (tryRead("}")) return dict

            do {
                skipWhitespace()

                // Key
                val key = readString()

                skipWhitespace()

                // :
                expect(":")

                skipWhitespace()

                // Value
                val value = readObject()

                // Add to dictionary
                dict[key] = value

                skipWhitespace()
            } while (tryRead(","))

            expect("}")

            return dict
        }

        /**
         * Read JSON array → MutableList.
         * All three implementations: List<object> / ArrayList<Object> / Array
         */
        private fun readArray(): MutableList<Any?> {
            val list = mutableListOf<Any?>()

            expect("[")
            skipWhitespace()

            // Empty list?
            if (tryRead("]")) return list

            do {
                skipWhitespace()

                // Value
                val value = readObject()

                // Add to array
                list.add(value)

                skipWhitespace()
            } while (tryRead(","))

            expect("]")

            return list
        }

        /**
         * Read JSON string with escape sequences.
         * All three handle: \", \\, \/, \n, \t, \r, \b, \f, \uXXXX
         * C#/Java silently ignore \r, \b, \f — Kotlin follows same behavior.
         */
        private fun readString(): String {
            expect("\"")

            val sb = StringBuilder()

            while (offset < text.length) {
                val c = text[offset]

                if (c == '\\') {
                    // Escaped character
                    offset++
                    if (offset >= text.length) {
                        throw StoryException("Unexpected EOF while reading string")
                    }
                    when (val escaped = text[offset]) {
                        '"', '\\', '/' -> sb.append(escaped)
                        'n' -> sb.append('\n')
                        't' -> sb.append('\t')
                        'r', 'b', 'f' -> { /* Ignore other control characters — matches C#/Java */ }
                        'u' -> {
                            // 4-digit Unicode
                            if (offset + 4 >= text.length) {
                                throw StoryException("Unexpected EOF while reading unicode escape")
                            }
                            val digits = text.substring(offset + 1, offset + 5)
                            val uchar = digits.toIntOrNull(16)
                                ?: throw StoryException("Invalid Unicode escape character at offset ${offset - 1}")
                            sb.append(uchar.toChar())
                            offset += 4
                        }
                        else -> throw StoryException("Invalid escape character at offset ${offset - 1}")
                    }
                } else if (c == '"') {
                    break
                } else {
                    sb.append(c)
                }

                offset++
            }

            expect("\"")
            return sb.toString()
        }

        /**
         * Read JSON number → Int or Float.
         * C#: int.TryParse / float.TryParse with InvariantCulture
         * Java: Integer.parseInt / Float.parseFloat
         * Kotlin: toIntOrNull() / toFloatOrNull() — null-safe, no exceptions
         */
        private fun readNumber(): Any {
            val startOffset = offset
            var isFloat = false

            while (offset < text.length) {
                val c = text[offset]
                if (c == '.' || c == 'e' || c == 'E') isFloat = true
                if (isNumberChar(c)) {
                    offset++
                } else {
                    break
                }
            }

            val numStr = text.substring(startOffset, offset)

            if (isFloat) {
                numStr.toFloatOrNull()?.let { return it }
            } else {
                numStr.toIntOrNull()?.let { return it }
            }

            throw StoryException("Failed to parse number value: $numStr")
        }

        /**
         * Try to match exact string at current offset. Advances offset if matched.
         * All three implementations share this exact pattern.
         */
        private fun tryRead(textToRead: String): Boolean {
            if (offset + textToRead.length > text.length) return false

            for (i in textToRead.indices) {
                if (textToRead[i] != text[offset + i]) return false
            }

            offset += textToRead.length
            return true
        }

        private fun expect(expectedStr: String) {
            if (!tryRead(expectedStr)) {
                throw StoryException("Expected $expectedStr at offset $offset")
            }
        }

        private fun skipWhitespace() {
            while (offset < text.length) {
                val c = text[offset]
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                    offset++
                } else {
                    break
                }
            }
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
