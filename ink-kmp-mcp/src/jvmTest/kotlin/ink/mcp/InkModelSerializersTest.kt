package ink.mcp

import ink.model.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.*

/**
 * Tests for InkModelSerializers — multi-format Wire message serialization.
 *
 * Verifies round-trip: Wire → JSON → Wire, Wire → msgpack → JSON → Wire.
 * Covers: story, table (flexible columns), asset, faker config messages.
 */
class InkModelSerializersTest {

    // ── Story types round-trip ────────────────────────────────────

    @Test
    fun `Choice round-trip JSON`() {
        val choice = Choice(
            index = 0,
            text = "Go north",
            tags = listOf("# mesh:🗡️")
        )

        val json = InkModelSerializers.toJson(choice)
        assertTrue(json.contains("Go north"))
        assertTrue(json.contains("# mesh:🗡️"))

        val rebuilt = InkModelSerializers.fromJson<Choice>(json)
        assertEquals(choice, rebuilt)
    }

    @Test
    fun `StoryState round-trip JSON`() {
        val state = StoryState(
            text = "You stand at the crossroads.",
            can_continue = true,
            choices = listOf(
                Choice(index = 0, text = "Go north"),
                Choice(index = 1, text = "Go south")
            ),
            tags = listOf("# anim:idle")
        )

        val json = InkModelSerializers.toJson(state)
        val rebuilt = InkModelSerializers.fromJson<StoryState>(json)
        assertEquals(state, rebuilt)
    }

    @Test
    fun `CompileResult round-trip JSON`() {
        val result = CompileResult(
            success = true,
            json = """{"inkVersion":21}""",
            warnings = listOf("Unused knot: intro")
        )

        val json = InkModelSerializers.toJson(result)
        val rebuilt = InkModelSerializers.fromJson<CompileResult>(json)
        assertEquals(result, rebuilt)
    }

    // ── Flexible table types ──────────────────────────────────────

    @Test
    fun `MdTable with flexible columns round-trip`() {
        val table = MdTable(
            name = "weapons",
            columns = listOf("emoji", "name", "base_dmg", "total_dmg"),
            rows = listOf(
                MdRow(cells = mapOf(
                    "emoji" to MdCell(value_ = "🗡️", type = CellType.EMOJI),
                    "name" to MdCell(value_ = "Excalibur", type = CellType.STRING),
                    "base_dmg" to MdCell(value_ = "15", type = CellType.INT),
                    "total_dmg" to MdCell(
                        formula = "=C2+C2*0.5",
                        evaluated = "22.5",
                        type = CellType.FORMULA
                    )
                ))
            )
        )

        val json = InkModelSerializers.toJson(table)
        assertTrue(json.contains("weapons"))
        assertTrue(json.contains("Excalibur"))
        assertTrue(json.contains("=C2+C2*0.5"))

        val rebuilt = InkModelSerializers.fromJson<MdTable>(json)
        assertEquals(table.name, rebuilt.name)
        assertEquals(table.columns, rebuilt.columns)
        assertEquals(table.rows.size, rebuilt.rows.size)

        val cell = rebuilt.rows[0].cells["total_dmg"]!!
        assertEquals(CellType.FORMULA, cell.type)
        assertEquals("=C2+C2*0.5", cell.formula)
        assertEquals("22.5", cell.evaluated)
    }

    @Test
    fun `MdCell types cover all CellType values`() {
        val types = CellType.values().toList()
        for (type in types) {
            val cell = MdCell(value_ = "test", type = type)
            val json = InkModelSerializers.toJson(cell)
            val rebuilt = InkModelSerializers.fromJson<MdCell>(json)
            assertEquals(type, rebuilt.type)
        }
    }

    // ── Asset types ──────────────────────────────────────────────

    @Test
    fun `AssetCategory round-trip`() {
        val category = AssetCategory(
            emoji = "🗡️",
            name = "sword",
            type = "weapon",
            anim_set = "sword_1h",
            grip_type = "main_hand",
            mesh_prefix = "weapon_sword",
            audio_category = "sfx_metal"
        )

        val json = InkModelSerializers.toJson(category)
        val rebuilt = InkModelSerializers.fromJson<AssetCategory>(json)
        assertEquals(category, rebuilt)
    }

    @Test
    fun `AssetRef with metadata map round-trip`() {
        val ref = AssetRef(
            emoji = "🧙",
            category = AssetCategory(
                emoji = "🧙", name = "wizard", type = "character"
            ),
            mesh_path = "char_wizard_01.glb",
            anim_set_id = "cast",
            voice_ref = VoiceRef(
                character_id = "gandalf",
                language = "en",
                flac_path = "voices/gandalf_en.flac"
            ),
            metadata = mapOf("dnd_class" to "wizard", "level" to "10")
        )

        val json = InkModelSerializers.toJson(ref)
        val rebuilt = InkModelSerializers.fromJson<AssetRef>(json)
        assertEquals(ref, rebuilt)
        assertEquals("wizard", rebuilt.metadata["dnd_class"])
    }

    // ── Faker config ─────────────────────────────────────────────

    @Test
    fun `FakerConfig round-trip`() {
        val config = FakerConfig(
            seed = 42,
            locale = "en",
            count = 5,
            level = 3,
            categories = listOf("sword", "potion")
        )

        val json = InkModelSerializers.toJson(config)
        val rebuilt = InkModelSerializers.fromJson<FakerConfig>(json)
        assertEquals(config, rebuilt)
    }

    // ── msgpack round-trip ───────────────────────────────────────

    @Test
    fun `StoryState msgpack round-trip`() {
        val state = StoryState(
            text = "Hello world",
            can_continue = false
        )

        val msgpack = InkModelSerializers.toMsgpack(state)
        assertTrue(msgpack.isNotEmpty())

        // msgpack → JSON → Wire
        val json = InkModelSerializers.msgpackToJson(msgpack)
        val rebuilt = InkModelSerializers.fromJson<StoryState>(json)
        assertEquals(state.text, rebuilt.text)
        assertEquals(state.can_continue, rebuilt.can_continue)
    }

    // ── Binary protobuf ──────────────────────────────────────────

    @Test
    fun `Choice binary round-trip`() {
        val choice = Choice(
            index = 2,
            text = "Attack",
            tags = listOf("# anim:sword_slash")
        )

        val bytes = InkModelSerializers.toBytes(choice)
        val rebuilt = Choice.ADAPTER.decode(bytes)
        assertEquals(choice, rebuilt)
    }

    // ── JSON Schema ──────────────────────────────────────────────

    @Test
    fun `toJsonSchema produces valid JSON Schema for FakerConfig`() {
        val schema = InkModelSerializers.toJsonSchema(FakerConfig::class.java)
        assertEquals("object", schema["type"]?.jsonPrimitive?.content)
        val props = schema["properties"]?.jsonObject
        assertNotNull(props)
        assertTrue(props.contains("seed"))
        assertTrue(props.contains("locale"))
        assertTrue(props.contains("count"))
        assertTrue(props.contains("level"))
        assertTrue(props.contains("categories"))
    }

    // ── kotlinx JsonElement bridge ───────────────────────────────

    @Test
    fun `toJsonElement produces JsonObject`() {
        val choice = Choice(
            index = 0,
            text = "Run away"
        )

        val element = InkModelSerializers.toJsonElement(choice)
        assertTrue(element is JsonObject)
        assertEquals("Run away", element.jsonObject["text"]?.jsonPrimitive?.content)
    }
}
