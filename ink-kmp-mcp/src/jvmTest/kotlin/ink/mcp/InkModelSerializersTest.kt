package ink.mcp

import ink.model.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.*

/**
 * Tests for InkModelSerializers — multi-format proto serialization.
 *
 * Verifies round-trip: proto → JSON → proto, proto → msgpack → JSON → proto.
 * Covers: story, table (flexible columns), asset, faker config messages.
 */
class InkModelSerializersTest {

    // ── Story types round-trip ────────────────────────────────────

    @Test
    fun `Choice round-trip JSON`() {
        val choice = Choice.newBuilder()
            .setIndex(0)
            .setText("Go north")
            .addTags("# mesh:🗡️")
            .build()

        val json = InkModelSerializers.toJson(choice)
        assertTrue(json.contains("Go north"))
        assertTrue(json.contains("# mesh:🗡️"))

        val rebuilt = InkModelSerializers.fromJson(json, Choice.newBuilder()).build()
        assertEquals(choice, rebuilt)
    }

    @Test
    fun `StoryState round-trip JSON`() {
        val state = StoryState.newBuilder()
            .setText("You stand at the crossroads.")
            .setCanContinue(true)
            .addChoices(Choice.newBuilder().setIndex(0).setText("Go north").build())
            .addChoices(Choice.newBuilder().setIndex(1).setText("Go south").build())
            .addTags("# anim:idle")
            .build()

        val json = InkModelSerializers.toJson(state)
        val rebuilt = InkModelSerializers.fromJson(json, StoryState.newBuilder()).build()
        assertEquals(state, rebuilt)
    }

    @Test
    fun `CompileResult round-trip JSON`() {
        val result = CompileResult.newBuilder()
            .setSuccess(true)
            .setJson("{\"inkVersion\":21}")
            .addWarnings("Unused knot: intro")
            .build()

        val json = InkModelSerializers.toJson(result)
        val rebuilt = InkModelSerializers.fromJson(json, CompileResult.newBuilder()).build()
        assertEquals(result, rebuilt)
    }

    // ── Flexible table types ──────────────────────────────────────

    @Test
    fun `MdTable with flexible columns round-trip`() {
        val table = MdTable.newBuilder()
            .setName("weapons")
            .addColumns("emoji")
            .addColumns("name")
            .addColumns("base_dmg")
            .addColumns("total_dmg")
            .addRows(MdRow.newBuilder()
                .putCells("emoji", MdCell.newBuilder().setValue("🗡️").setType(CellType.EMOJI).build())
                .putCells("name", MdCell.newBuilder().setValue("Excalibur").setType(CellType.STRING).build())
                .putCells("base_dmg", MdCell.newBuilder().setValue("15").setType(CellType.INT).build())
                .putCells("total_dmg", MdCell.newBuilder()
                    .setFormula("=C2+C2*0.5")
                    .setEvaluated("22.5")
                    .setType(CellType.FORMULA)
                    .build())
                .build())
            .build()

        val json = InkModelSerializers.toJson(table)
        assertTrue(json.contains("weapons"))
        assertTrue(json.contains("Excalibur"))
        assertTrue(json.contains("=C2+C2*0.5"))

        val rebuilt = InkModelSerializers.fromJson(json, MdTable.newBuilder()).build()
        assertEquals(table.name, rebuilt.name)
        assertEquals(table.columnsList, rebuilt.columnsList)
        assertEquals(table.rowsCount, rebuilt.rowsCount)

        val cell = rebuilt.getRows(0).cellsMap["total_dmg"]!!
        assertEquals(CellType.FORMULA, cell.type)
        assertEquals("=C2+C2*0.5", cell.formula)
        assertEquals("22.5", cell.evaluated)
    }

    @Test
    fun `MdCell types cover all CellType values`() {
        val types = CellType.values().filter { it != CellType.UNRECOGNIZED }
        for (type in types) {
            val cell = MdCell.newBuilder().setValue("test").setType(type).build()
            val json = InkModelSerializers.toJson(cell)
            val rebuilt = InkModelSerializers.fromJson(json, MdCell.newBuilder()).build()
            assertEquals(type, rebuilt.type)
        }
    }

    // ── Asset types ──────────────────────────────────────────────

    @Test
    fun `AssetCategory round-trip`() {
        val category = AssetCategory.newBuilder()
            .setEmoji("🗡️")
            .setName("sword")
            .setType("weapon")
            .setAnimSet("sword_1h")
            .setGripType("main_hand")
            .setMeshPrefix("weapon_sword")
            .setAudioCategory("sfx_metal")
            .build()

        val json = InkModelSerializers.toJson(category)
        val rebuilt = InkModelSerializers.fromJson(json, AssetCategory.newBuilder()).build()
        assertEquals(category, rebuilt)
    }

    @Test
    fun `AssetRef with metadata map round-trip`() {
        val ref = AssetRef.newBuilder()
            .setEmoji("🧙")
            .setCategory(AssetCategory.newBuilder()
                .setEmoji("🧙").setName("wizard").setType("character").build())
            .setMeshPath("char_wizard_01.glb")
            .setAnimSetId("cast")
            .setVoiceRef(VoiceRef.newBuilder()
                .setCharacterId("gandalf")
                .setLanguage("en")
                .setFlacPath("voices/gandalf_en.flac")
                .build())
            .putMetadata("dnd_class", "wizard")
            .putMetadata("level", "10")
            .build()

        val json = InkModelSerializers.toJson(ref)
        val rebuilt = InkModelSerializers.fromJson(json, AssetRef.newBuilder()).build()
        assertEquals(ref, rebuilt)
        assertEquals("wizard", rebuilt.metadataMap["dnd_class"])
    }

    // ── Faker config ─────────────────────────────────────────────

    @Test
    fun `FakerConfig round-trip`() {
        val config = FakerConfig.newBuilder()
            .setSeed(42)
            .setLocale("en")
            .setCount(5)
            .setLevel(3)
            .addCategories("sword")
            .addCategories("potion")
            .build()

        val json = InkModelSerializers.toJson(config)
        val rebuilt = InkModelSerializers.fromJson(json, FakerConfig.newBuilder()).build()
        assertEquals(config, rebuilt)
    }

    // ── msgpack round-trip ───────────────────────────────────────

    @Test
    fun `StoryState msgpack round-trip`() {
        val state = StoryState.newBuilder()
            .setText("Hello world")
            .setCanContinue(false)
            .build()

        val msgpack = InkModelSerializers.toMsgpack(state)
        assertTrue(msgpack.isNotEmpty())

        // msgpack → JSON → proto
        val json = InkModelSerializers.msgpackToJson(msgpack)
        val rebuilt = InkModelSerializers.fromJson(json, StoryState.newBuilder()).build()
        assertEquals(state.text, rebuilt.text)
        assertEquals(state.canContinue, rebuilt.canContinue)
    }

    // ── Binary protobuf ──────────────────────────────────────────

    @Test
    fun `Choice binary round-trip`() {
        val choice = Choice.newBuilder()
            .setIndex(2)
            .setText("Attack")
            .addTags("# anim:sword_slash")
            .build()

        val bytes = InkModelSerializers.toBytes(choice)
        val rebuilt = Choice.parseFrom(bytes)
        assertEquals(choice, rebuilt)
    }

    // ── JSON Schema ──────────────────────────────────────────────

    @Test
    fun `toJsonSchema produces valid JSON Schema for FakerConfig`() {
        val schema = InkModelSerializers.toJsonSchema(FakerConfig.getDescriptor())
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
        val choice = Choice.newBuilder()
            .setIndex(0)
            .setText("Run away")
            .build()

        val element = InkModelSerializers.toJsonElement(choice)
        assertTrue(element is JsonObject)
        assertEquals("Run away", element.jsonObject["text"]?.jsonPrimitive?.content)
    }
}
