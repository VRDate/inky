package com.bladecoder.ink.runtime

import kotlin.test.*

/**
 * Tests for SimpleJson — pure Kotlin JSON reader/writer.
 *
 * Verifies three-way compatibility: C# (inkle/ink), Java (blade-ink), JS (inkjs).
 * All tests are round-trip: write → string → read → verify.
 */
class SimpleJsonTest {

    // ── Reader Tests ──────────────────────────────────────────────

    @Test
    fun `read empty object`() {
        val result = SimpleJson.textToDictionary("{}")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `read empty array`() {
        val result = SimpleJson.textToArray("[]")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `read string value`() {
        val result = SimpleJson.textToDictionary("""{"key":"value"}""")
        assertEquals("value", result["key"])
    }

    @Test
    fun `read integer value`() {
        val result = SimpleJson.textToDictionary("""{"x":42}""")
        assertEquals(42, result["x"])
    }

    @Test
    fun `read negative integer`() {
        val result = SimpleJson.textToDictionary("""{"x":-7}""")
        assertEquals(-7, result["x"])
    }

    @Test
    fun `read float value`() {
        val result = SimpleJson.textToDictionary("""{"x":3.14}""")
        val value = result["x"] as Float
        assertEquals(3.14f, value, 0.001f)
    }

    @Test
    fun `read float with exponent`() {
        val result = SimpleJson.textToDictionary("""{"x":1.5E2}""")
        val value = result["x"] as Float
        assertEquals(150.0f, value, 0.01f)
    }

    @Test
    fun `read float zero point zero`() {
        // Critical for ink runtime — "0.0" must parse as Float, not Int
        val result = SimpleJson.textToDictionary("""{"x":0.0}""")
        assertTrue(result["x"] is Float, "0.0 should parse as Float")
        assertEquals(0.0f, result["x"] as Float)
    }

    @Test
    fun `read boolean true`() {
        val result = SimpleJson.textToDictionary("""{"flag":true}""")
        assertEquals(true, result["flag"])
    }

    @Test
    fun `read boolean false`() {
        val result = SimpleJson.textToDictionary("""{"flag":false}""")
        assertEquals(false, result["flag"])
    }

    @Test
    fun `read null value`() {
        val result = SimpleJson.textToDictionary("""{"x":null}""")
        assertNull(result["x"])
        assertTrue(result.containsKey("x"))
    }

    @Test
    fun `read nested object`() {
        val result = SimpleJson.textToDictionary("""{"outer":{"inner":1}}""")
        @Suppress("UNCHECKED_CAST")
        val inner = result["outer"] as Map<String, Any?>
        assertEquals(1, inner["inner"])
    }

    @Test
    fun `read array of ints`() {
        val result = SimpleJson.textToArray("[1,2,3]")
        assertEquals(listOf(1, 2, 3), result)
    }

    @Test
    fun `read array of mixed types`() {
        val result = SimpleJson.textToArray("""[1,"two",true,null,3.5]""")
        assertEquals(5, result.size)
        assertEquals(1, result[0])
        assertEquals("two", result[1])
        assertEquals(true, result[2])
        assertNull(result[3])
        assertEquals(3.5f, result[4] as Float, 0.01f)
    }

    @Test
    fun `read string with escape sequences`() {
        val result = SimpleJson.textToDictionary("""{"s":"hello\nworld\t!"}""")
        assertEquals("hello\nworld\t!", result["s"])
    }

    @Test
    fun `read string with escaped quotes`() {
        val result = SimpleJson.textToDictionary("""{"s":"he said \"hi\""}""")
        assertEquals("he said \"hi\"", result["s"])
    }

    @Test
    fun `read string with escaped backslash`() {
        val result = SimpleJson.textToDictionary("""{"s":"path\\to\\file"}""")
        assertEquals("path\\to\\file", result["s"])
    }

    @Test
    fun `read string with unicode escape`() {
        val result = SimpleJson.textToDictionary("""{"s":"\u0041\u0042"}""")
        assertEquals("AB", result["s"])
    }

    @Test
    fun `read string with escaped forward slash`() {
        val result = SimpleJson.textToDictionary("""{"s":"a\/b"}""")
        assertEquals("a/b", result["s"])
    }

    @Test
    fun `read whitespace is skipped`() {
        val json = """
            {
                "key" : "value" ,
                "num" : 42
            }
        """.trimIndent()
        val result = SimpleJson.textToDictionary(json)
        assertEquals("value", result["key"])
        assertEquals(42, result["num"])
    }

    @Test
    fun `read preserves insertion order`() {
        // LinkedHashMap must preserve key order — critical for ink runtime
        val json = """{"c":3,"a":1,"b":2}"""
        val result = SimpleJson.textToDictionary(json)
        val keys = result.keys.toList()
        assertEquals(listOf("c", "a", "b"), keys)
    }

    @Test
    fun `read complex ink state json`() {
        // Simulates a typical ink story state JSON structure
        val json = """{"inkVersion":21,"root":[["\n","done",{"#n":"g-0"}],null],"listDefs":{}}"""
        val result = SimpleJson.textToDictionary(json)
        assertEquals(21, result["inkVersion"])
        @Suppress("UNCHECKED_CAST")
        val root = result["root"] as List<Any?>
        assertEquals(3, root.size)
        @Suppress("UNCHECKED_CAST")
        val listDefs = result["listDefs"] as Map<String, Any?>
        assertTrue(listDefs.isEmpty())
    }

    @Test
    fun `read throws on invalid json`() {
        assertFailsWith<StoryException> {
            SimpleJson.textToDictionary("not json")
        }
    }

    @Test
    fun `read throws on truncated string`() {
        assertFailsWith<StoryException> {
            SimpleJson.textToDictionary("""{"key":"unterminated""")
        }
    }

    // ── Writer Tests ──────────────────────────────────────────────

    @Test
    fun `write empty object`() {
        val w = SimpleJson.Writer()
        w.writeObjectStart()
        w.writeObjectEnd()
        assertEquals("{}", w.toString())
    }

    @Test
    fun `write empty array`() {
        val w = SimpleJson.Writer()
        w.writeObject { writer ->
            writer.writeProperty("arr") { inner ->
                inner.writeArrayStart()
                inner.writeArrayEnd()
            }
        }
        assertEquals("""{"arr":[]}""", w.toString())
    }

    @Test
    fun `write string property`() {
        val w = SimpleJson.Writer()
        w.writeObjectStart()
        w.writeProperty("name", "ink")
        w.writeObjectEnd()
        assertEquals("""{"name":"ink"}""", w.toString())
    }

    @Test
    fun `write int property`() {
        val w = SimpleJson.Writer()
        w.writeObjectStart()
        w.writeProperty("ver", 21)
        w.writeObjectEnd()
        assertEquals("""{"ver":21}""", w.toString())
    }

    @Test
    fun `write bool property`() {
        val w = SimpleJson.Writer()
        w.writeObjectStart()
        w.writeProperty("ok", true)
        w.writeObjectEnd()
        assertEquals("""{"ok":true}""", w.toString())
    }

    @Test
    fun `write float value`() {
        val w = SimpleJson.Writer()
        w.writeObjectStart()
        w.writeProperty("x") { it.write(3.14f) }
        w.writeObjectEnd()
        // Float.toString may give "3.14" — verify it parses back
        val result = SimpleJson.textToDictionary(w.toString())
        assertEquals(3.14f, result["x"] as Float, 0.01f)
    }

    @Test
    fun `write float whole number gets dot zero`() {
        val w = SimpleJson.Writer()
        w.writeObjectStart()
        w.writeProperty("x") { it.write(5.0f) }
        w.writeObjectEnd()
        assertTrue(w.toString().contains(".0"), "Whole float should have .0 suffix")
    }

    @Test
    fun `write float infinity`() {
        val w = SimpleJson.Writer()
        w.writeObjectStart()
        w.writeProperty("x") { it.write(Float.POSITIVE_INFINITY) }
        w.writeObjectEnd()
        assertTrue(w.toString().contains("3.4E+38"))
    }

    @Test
    fun `write float negative infinity`() {
        val w = SimpleJson.Writer()
        w.writeObjectStart()
        w.writeProperty("x") { it.write(Float.NEGATIVE_INFINITY) }
        w.writeObjectEnd()
        assertTrue(w.toString().contains("-3.4E+38"))
    }

    @Test
    fun `write float nan`() {
        val w = SimpleJson.Writer()
        w.writeObjectStart()
        w.writeProperty("x") { it.write(Float.NaN) }
        w.writeObjectEnd()
        assertTrue(w.toString().contains("0.0"))
    }

    @Test
    fun `write null`() {
        val w = SimpleJson.Writer()
        w.writeObjectStart()
        w.writeProperty("x") { it.writeNull() }
        w.writeObjectEnd()
        assertEquals("""{"x":null}""", w.toString())
    }

    @Test
    fun `write escaped string`() {
        val w = SimpleJson.Writer()
        w.writeObjectStart()
        w.writeProperty("s", "line1\nline2\ttab")
        w.writeObjectEnd()
        val json = w.toString()
        assertTrue(json.contains("\\n"))
        assertTrue(json.contains("\\t"))
        // Round-trip
        val result = SimpleJson.textToDictionary(json)
        assertEquals("line1\nline2\ttab", result["s"])
    }

    @Test
    fun `write escaped backslash and quotes`() {
        val w = SimpleJson.Writer()
        w.writeObjectStart()
        w.writeProperty("s", """path\to\"file""")
        w.writeObjectEnd()
        val result = SimpleJson.textToDictionary(w.toString())
        assertEquals("path\\to\\\"file", result["s"])
    }

    @Test
    fun `write multiple properties`() {
        val w = SimpleJson.Writer()
        w.writeObjectStart()
        w.writeProperty("a", 1)
        w.writeProperty("b", "two")
        w.writeProperty("c", true)
        w.writeObjectEnd()
        val result = SimpleJson.textToDictionary(w.toString())
        assertEquals(1, result["a"])
        assertEquals("two", result["b"])
        assertEquals(true, result["c"])
    }

    @Test
    fun `write nested objects`() {
        val w = SimpleJson.Writer()
        w.writeObjectStart()
        w.writeProperty("outer") { outer ->
            outer.writeObjectStart()
            outer.writeProperty("inner", 42)
            outer.writeObjectEnd()
        }
        w.writeObjectEnd()
        assertEquals("""{"outer":{"inner":42}}""", w.toString())
    }

    @Test
    fun `write array of ints`() {
        val w = SimpleJson.Writer()
        w.writeObjectStart()
        w.writeProperty("nums") { prop ->
            prop.writeArrayStart()
            prop.write(1)
            prop.write(2)
            prop.write(3)
            prop.writeArrayEnd()
        }
        w.writeObjectEnd()
        assertEquals("""{"nums":[1,2,3]}""", w.toString())
    }

    @Test
    fun `write integer id property`() {
        val w = SimpleJson.Writer()
        w.writeObjectStart()
        w.writeProperty(0) { it.write("zero") }
        w.writeObjectEnd()
        assertEquals("""{"0":"zero"}""", w.toString())
    }

    @Test
    fun `write string start end inner`() {
        val w = SimpleJson.Writer()
        w.writeObjectStart()
        w.writeProperty("s") { prop ->
            prop.writeStringStart()
            prop.writeStringInner("hello ")
            prop.writeStringInner("world")
            prop.writeStringEnd()
        }
        w.writeObjectEnd()
        assertEquals("""{"s":"hello world"}""", w.toString())
    }

    @Test
    fun `write property name start end inner`() {
        val w = SimpleJson.Writer()
        w.writeObjectStart()
        w.writePropertyNameStart()
        w.writePropertyNameInner("dynamic")
        w.writePropertyNameEnd()
        w.write(99)
        w.writePropertyEnd()
        w.writeObjectEnd()
        assertEquals("""{"dynamic":99}""", w.toString())
    }

    @Test
    fun `writer clear resets state`() {
        val w = SimpleJson.Writer()
        w.writeObjectStart()
        w.writeProperty("x", 1)
        w.writeObjectEnd()
        assertEquals("""{"x":1}""", w.toString())

        w.clear()
        assertEquals("", w.toString())
    }

    @Test
    fun `writeObject convenience method`() {
        val w = SimpleJson.Writer()
        w.writeObject { writer ->
            writer.writeProperty("a", 1)
            writer.writeProperty("b", 2)
        }
        assertEquals("""{"a":1,"b":2}""", w.toString())
    }

    // ── Round-Trip Tests ──────────────────────────────────────────

    @Test
    fun `round-trip ink story state`() {
        // Write a typical ink state structure
        val w = SimpleJson.Writer()
        w.writeObjectStart()
        w.writeProperty("inkVersion", 21)
        w.writeProperty("root") { root ->
            root.writeArrayStart()
            root.writeArrayStart()
            root.write("\n")
            root.write("done", false)
            root.writeObject { tag ->
                tag.writeProperty("#n", "g-0")
            }
            root.writeArrayEnd()
            root.writeNull()
            root.writeArrayEnd()
        }
        w.writeProperty("listDefs") { ld ->
            ld.writeObjectStart()
            ld.writeObjectEnd()
        }
        w.writeObjectEnd()

        // Read back
        val result = SimpleJson.textToDictionary(w.toString())
        assertEquals(21, result["inkVersion"])

        @Suppress("UNCHECKED_CAST")
        val root = result["root"] as List<Any?>
        assertEquals(2, root.size)

        @Suppress("UNCHECKED_CAST")
        val firstChild = root[0] as List<Any?>
        assertEquals("\n", firstChild[0])
        assertEquals("done", firstChild[1])

        @Suppress("UNCHECKED_CAST")
        val tag = firstChild[2] as Map<String, Any?>
        assertEquals("g-0", tag["#n"])

        assertNull(root[1])

        @Suppress("UNCHECKED_CAST")
        val listDefs = result["listDefs"] as Map<String, Any?>
        assertTrue(listDefs.isEmpty())
    }

    @Test
    fun `round-trip complex nested structure`() {
        val w = SimpleJson.Writer()
        w.writeObjectStart()
        w.writeProperty("flows") { flows ->
            flows.writeObjectStart()
            flows.writeProperty("DEFAULT_FLOW") { flow ->
                flow.writeObjectStart()
                flow.writeProperty("callstack") { cs ->
                    cs.writeArrayStart()
                    cs.writeObjectStart()
                    cs.writeProperty("type", 0)
                    cs.writeProperty("idx", 0)
                    cs.writeObjectEnd()
                    cs.writeArrayEnd()
                }
                flow.writeProperty("outputStream") { os ->
                    os.writeArrayStart()
                    os.write("\n")
                    os.writeArrayEnd()
                }
                flow.writeObjectEnd()
            }
            flows.writeObjectEnd()
        }
        w.writeObjectEnd()

        // Verify round-trip
        val json = w.toString()
        val result = SimpleJson.textToDictionary(json)
        assertTrue(result.containsKey("flows"))

        @Suppress("UNCHECKED_CAST")
        val flows = result["flows"] as Map<String, Any?>
        assertTrue(flows.containsKey("DEFAULT_FLOW"))
    }

    // ── Java Bug Fix Verification ──

    @Test
    fun `java unicode escape bug fixed — reads exactly 4 hex digits`() {
        // Java blade-ink has: text.substring(offset + 1, offset + 6) — reads 5 chars (BUG)
        // C# has: _text.SubString(_offset + 1, 4) — reads 4 chars (correct)
        // Kotlin fix: text.substring(offset + 1, offset + 5) — reads 4 chars
        val result = SimpleJson.textToDictionary("""{"s":"\u0048\u0049"}""")
        assertEquals("HI", result["s"])
    }

    @Test
    fun `java float string comparison bug fixed — uses equals not identity`() {
        // Java blade-ink uses == for String comparison on lines 414/416/418 (BUG)
        // Kotlin uses == which maps to .equals() — correct behavior
        val w = SimpleJson.Writer()
        w.writeObjectStart()
        w.writeProperty("inf") { it.write(Float.POSITIVE_INFINITY) }
        w.writeProperty("ninf") { it.write(Float.NEGATIVE_INFINITY) }
        w.writeProperty("nan") { it.write(Float.NaN) }
        w.writeObjectEnd()
        val json = w.toString()
        assertTrue(json.contains("3.4E+38"))
        assertTrue(json.contains("-3.4E+38"))
        assertTrue(json.contains("0.0"))
    }
}
