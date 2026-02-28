package ink.mcp

import kotlin.test.*

/**
 * Tests for InkCalendarEngine — in-memory iCal4j calendar management.
 *
 * Tests: createEvent, listEvents (with and without category filter),
 * exportIcs, importIcs, listCalendars, and full round-trip lifecycle.
 */
class InkCalendarEngineTest {

    private val engine = InkCalendarEngine()

    companion object {
        private const val TEST_CAL = "test-cal"

        private val questEvent = InkCalendarEngine.InkEvent(
            summary = "Quest Start",
            description = "Begin the enchanted forest quest",
            dtStart = "20250101T100000Z",
            dtEnd = "20250101T120000Z",
            category = "quest",
            location = "Enchanted Forest",
            status = "CONFIRMED"
        )

        private val sessionEvent = InkCalendarEngine.InkEvent(
            summary = "Game Session 1",
            dtStart = "20250201T180000Z",
            dtEnd = "20250201T210000Z",
            category = "session"
        )

        private val deadlineEvent = InkCalendarEngine.InkEvent(
            summary = "Chapter Deadline",
            dtStart = "20250315T235900Z",
            category = "quest"
        )

        private val sampleIcs = """
            BEGIN:VCALENDAR
            PRODID:-//Test//Import//EN
            VERSION:2.0
            CALSCALE:GREGORIAN
            BEGIN:VEVENT
            UID:imported-001
            SUMMARY:Imported Battle
            DTSTART:20250601T140000Z
            CATEGORIES:combat
            END:VEVENT
            BEGIN:VEVENT
            UID:imported-002
            SUMMARY:Imported Rest
            DTSTART:20250602T080000Z
            CATEGORIES:rest
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()
    }

    // ═══════════════════════════════════════════════════════════════
    // createEvent
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `createEvent returns result with uid and metadata`() {
        val result = engine.createEvent(TEST_CAL, questEvent)

        assertTrue(result["ok"] as Boolean, "createEvent should succeed")
        assertEquals(TEST_CAL, result["calendar_id"])
        assertNotNull(result["event_uid"], "Result should contain event uid")
        assertEquals("Quest Start", result["summary"])
        assertEquals("20250101T100000Z", result["dt_start"])
    }

    @Test
    fun `createEvent with minimal fields succeeds`() {
        val minimal = InkCalendarEngine.InkEvent(
            summary = "Minimal Event",
            dtStart = "20250101T000000Z"
        )
        val result = engine.createEvent(TEST_CAL, minimal)

        assertTrue(result["ok"] as Boolean)
        assertNotNull(result["event_uid"])
        assertEquals("Minimal Event", result["summary"])
    }

    // ═══════════════════════════════════════════════════════════════
    // listEvents
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `listEvents returns created events`() {
        engine.createEvent(TEST_CAL, questEvent)
        engine.createEvent(TEST_CAL, sessionEvent)

        val events = engine.listEvents(TEST_CAL)
        assertEquals(2, events.size, "Should list both created events")

        val summaries = events.map { it["summary"] as String }.toSet()
        assertTrue("Quest Start" in summaries)
        assertTrue("Game Session 1" in summaries)
    }

    @Test
    fun `listEvents returns empty list for unknown calendar`() {
        val events = engine.listEvents("nonexistent-cal")
        assertTrue(events.isEmpty(), "Unknown calendar should return empty list")
    }

    @Test
    fun `listEvents with category filter returns matching events only`() {
        engine.createEvent(TEST_CAL, questEvent)
        engine.createEvent(TEST_CAL, sessionEvent)
        engine.createEvent(TEST_CAL, deadlineEvent)

        val questEvents = engine.listEvents(TEST_CAL, category = "quest")
        assertEquals(2, questEvents.size, "Should find 2 quest events")
        assertTrue(questEvents.all { (it["category"] as String).contains("quest", ignoreCase = true) })

        val sessionEvents = engine.listEvents(TEST_CAL, category = "session")
        assertEquals(1, sessionEvents.size, "Should find 1 session event")
        assertEquals("Game Session 1", sessionEvents[0]["summary"])
    }

    // ═══════════════════════════════════════════════════════════════
    // exportIcs
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `exportIcs produces valid ICS string`() {
        engine.createEvent(TEST_CAL, questEvent)

        val ics = engine.exportIcs(TEST_CAL)
        assertTrue(ics.contains("BEGIN:VCALENDAR"), "ICS should contain VCALENDAR")
        assertTrue(ics.contains("END:VCALENDAR"), "ICS should close VCALENDAR")
        assertTrue(ics.contains("BEGIN:VEVENT"), "ICS should contain VEVENT")
        assertTrue(ics.contains("END:VEVENT"), "ICS should close VEVENT")
        assertTrue(ics.contains("Quest Start"), "ICS should contain event summary")
        assertTrue(ics.contains("VERSION:2.0"), "ICS should declare version 2.0")
    }

    @Test
    fun `exportIcs throws for unknown calendar`() {
        assertFailsWith<IllegalArgumentException> {
            engine.exportIcs("nonexistent-cal")
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // importIcs
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `importIcs imports events from ICS content`() {
        val result = engine.importIcs("import-cal", sampleIcs)

        assertTrue(result["ok"] as Boolean, "importIcs should succeed")
        assertEquals("import-cal", result["calendar_id"])
        assertEquals(2, result["imported_count"], "Should import 2 events")

        val events = engine.listEvents("import-cal")
        assertEquals(2, events.size)

        val summaries = events.map { it["summary"] as String }.toSet()
        assertTrue("Imported Battle" in summaries)
        assertTrue("Imported Rest" in summaries)
    }

    // ═══════════════════════════════════════════════════════════════
    // listCalendars
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `listCalendars returns all calendars with event counts`() {
        engine.createEvent("cal-alpha", questEvent)
        engine.createEvent("cal-alpha", sessionEvent)
        engine.createEvent("cal-beta", deadlineEvent)

        val calendars = engine.listCalendars()
        assertTrue(calendars.size >= 2, "Should list at least 2 calendars")

        val calMap = calendars.associateBy { it["calendar_id"] as String }
        assertEquals(2, calMap["cal-alpha"]?.get("event_count"))
        assertEquals(1, calMap["cal-beta"]?.get("event_count"))
    }

    // ═══════════════════════════════════════════════════════════════
    // ROUND-TRIP LIFECYCLE
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `lifecycle create then export then import round-trip preserves events`() {
        // 1) Create events in a source calendar
        engine.createEvent("source-cal", questEvent)
        engine.createEvent("source-cal", sessionEvent)

        val sourceEvents = engine.listEvents("source-cal")
        assertEquals(2, sourceEvents.size, "Source should have 2 events")

        // 2) Export to ICS
        val ics = engine.exportIcs("source-cal")
        assertTrue(ics.contains("BEGIN:VCALENDAR"))
        assertTrue(ics.contains("Quest Start"))
        assertTrue(ics.contains("Game Session 1"))

        // 3) Import into a new calendar
        val importResult = engine.importIcs("dest-cal", ics)
        assertTrue(importResult["ok"] as Boolean)
        assertEquals(2, importResult["imported_count"])

        // 4) Verify the imported calendar has the same events
        val destEvents = engine.listEvents("dest-cal")
        assertEquals(2, destEvents.size, "Destination should have 2 events")

        val destSummaries = destEvents.map { it["summary"] as String }.toSet()
        assertTrue("Quest Start" in destSummaries, "Should preserve Quest Start")
        assertTrue("Game Session 1" in destSummaries, "Should preserve Game Session 1")

        // 5) Verify both calendars appear in listCalendars
        val calendars = engine.listCalendars()
        val calIds = calendars.map { it["calendar_id"] as String }.toSet()
        assertTrue("source-cal" in calIds)
        assertTrue("dest-cal" in calIds)
    }
}
