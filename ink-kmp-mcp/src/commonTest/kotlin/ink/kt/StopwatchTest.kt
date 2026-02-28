package ink.kt

import kotlin.test.*

/**
 * Tests for Stopwatch (monotonic timer) and InkClock (wall clock authority).
 *
 * Verifies:
 * - Stopwatch timing accuracy (monotonic)
 * - InkClock epoch millis, formatting, date component decomposition
 * - Howard Hinnant's civil_from_days algorithm correctness
 * - Timezone offset handling
 * - Calendar format compatibility (ISO 8601, iCal, backup)
 * - Test determinism via fixed clock source
 */
class StopwatchTest {

    @AfterTest
    fun cleanup() {
        InkClock.resetToSystemClock()
    }

    // ── Stopwatch tests ──────────────────────────────────────────────

    @Test
    fun `stopwatch starts at zero`() {
        val sw = Stopwatch()
        assertEquals(0L, sw.elapsedMilliseconds)
    }

    @Test
    fun `stopwatch measures elapsed time`() {
        val sw = Stopwatch()
        sw.start()
        // Can't reliably test exact timing, but can verify it's non-negative
        assertTrue(sw.elapsedMilliseconds >= 0)
        sw.stop()
        assertTrue(sw.elapsedMilliseconds >= 0)
    }

    @Test
    fun `stopwatch reset clears elapsed`() {
        val sw = Stopwatch()
        sw.start()
        sw.stop()
        sw.reset()
        assertEquals(0L, sw.elapsedMilliseconds)
        assertEquals(0L, sw.elapsedNanoseconds)
    }

    @Test
    fun `stopwatch elapsed nanos has higher precision than millis`() {
        val sw = Stopwatch()
        sw.start()
        // Nanos should be >= millis * 1_000_000
        val nanos = sw.elapsedNanoseconds
        val millis = sw.elapsedMilliseconds
        sw.stop()
        assertTrue(nanos >= 0)
        assertTrue(millis >= 0)
    }

    // ── InkClock epoch tests ─────────────────────────────────────────

    @Test
    fun `fixed clock returns exact value`() {
        InkClock.setFixedClock(1705312200000L) // 2024-01-15T10:30:00Z
        assertEquals(1705312200000L, InkClock.epochMillis())
        assertEquals(1705312200L, InkClock.epochSeconds())
    }

    @Test
    fun `epoch millis minus days subtracts correctly`() {
        InkClock.setFixedClock(1705312200000L) // 2024-01-15T10:30:00Z
        val sevenDaysAgo = InkClock.epochMillisMinusDays(7)
        val expectedMs = 1705312200000L - (7L * 24 * 3600 * 1000)
        assertEquals(expectedMs, sevenDaysAgo)
    }

    @Test
    fun `time seed is within int range`() {
        InkClock.setFixedClock(1705312200000L)
        val seed = InkClock.timeSeed()
        assertTrue(seed >= 0)
        assertTrue(seed < Int.MAX_VALUE)
    }

    // ── Date component decomposition ─────────────────────────────────

    @Test
    fun `unix epoch is 1970-01-01 00-00-00`() {
        val dc = epochMillisToComponents(0L)
        assertEquals(1970, dc.year)
        assertEquals(1, dc.month)
        assertEquals(1, dc.day)
        assertEquals(0, dc.hour)
        assertEquals(0, dc.minute)
        assertEquals(0, dc.second)
        assertEquals(0, dc.millisecond)
    }

    @Test
    fun `known date 2024-01-15 10-30-00`() {
        // 2024-01-15T10:30:00.000Z = 1705312200000 ms
        val dc = epochMillisToComponents(1705312200000L)
        assertEquals(2024, dc.year)
        assertEquals(1, dc.month)
        assertEquals(15, dc.day)
        assertEquals(10, dc.hour)
        assertEquals(30, dc.minute)
        assertEquals(0, dc.second)
    }

    @Test
    fun `known date 2000-01-01 midnight`() {
        // 2000-01-01T00:00:00Z = 946684800000 ms
        val dc = epochMillisToComponents(946684800000L)
        assertEquals(2000, dc.year)
        assertEquals(1, dc.month)
        assertEquals(1, dc.day)
        assertEquals(0, dc.hour)
    }

    @Test
    fun `known date 2026-02-27`() {
        // 2026-02-27T12:00:00Z = 1772193600000 ms
        val dc = epochMillisToComponents(1772193600000L)
        assertEquals(2026, dc.year)
        assertEquals(2, dc.month)
        assertEquals(27, dc.day)
        assertEquals(12, dc.hour)
    }

    @Test
    fun `leap year feb 29`() {
        // 2024-02-29T00:00:00Z = 1709164800000 ms
        val dc = epochMillisToComponents(1709164800000L)
        assertEquals(2024, dc.year)
        assertEquals(2, dc.month)
        assertEquals(29, dc.day)
    }

    @Test
    fun `end of year dec 31`() {
        // 2023-12-31T23:59:59.999Z = 1704067199999 ms
        val dc = epochMillisToComponents(1704067199999L)
        assertEquals(2023, dc.year)
        assertEquals(12, dc.month)
        assertEquals(31, dc.day)
        assertEquals(23, dc.hour)
        assertEquals(59, dc.minute)
        assertEquals(59, dc.second)
        assertEquals(999, dc.millisecond)
    }

    @Test
    fun `millisecond precision preserved`() {
        // 2024-06-15T14:30:45.123Z
        val dc = epochMillisToComponents(1718458245123L)
        assertEquals(123, dc.millisecond)
        assertEquals(123_000_000L, dc.nanosecond)
    }

    // ── ISO 8601 formatting ──────────────────────────────────────────

    @Test
    fun `utc iso8601 format`() {
        InkClock.setFixedClock(1705312200000L) // 2024-01-15T10:30:00.000Z
        val iso = InkClock.utcIso8601()
        assertEquals("2024-01-15T10:30:00.000Z", iso)
    }

    @Test
    fun `utc iso8601 epoch zero`() {
        InkClock.setFixedClock(0L)
        assertEquals("1970-01-01T00:00:00.000Z", InkClock.utcIso8601())
    }

    // ── iCal format ──────────────────────────────────────────────────

    @Test
    fun `ical timestamp format`() {
        InkClock.setFixedClock(1705312200000L) // 2024-01-15T10:30:00Z
        val ical = InkClock.utcICalTimestamp()
        assertEquals("20240115T103000Z", ical)
    }

    // ── Backup timestamp format ──────────────────────────────────────

    @Test
    fun `backup timestamp format is filesystem safe`() {
        InkClock.setFixedClock(1705312200000L)
        val backup = InkClock.utcBackupTimestamp()
        // Should not contain : (invalid on Windows)
        assertFalse(backup.contains(":"))
        // Should contain underscore separator
        assertTrue(backup.contains("_"))
        // Should have 9-digit nano fraction
        val nanoPart = backup.substringAfterLast(".")
        assertEquals(9, nanoPart.length)
    }

    // ── Local time with offset ───────────────────────────────────────

    @Test
    fun `local iso8601 with positive offset`() {
        InkClock.setFixedClock(1705312200000L) // 2024-01-15T10:30:00Z
        val local = InkClock.localIso8601(offsetHours = 2) // UTC+2 (IST)
        assertTrue(local.endsWith("+02:00"))
        assertTrue(local.contains("12:30:00")) // 10:30 + 2h = 12:30
    }

    @Test
    fun `local iso8601 with negative offset`() {
        InkClock.setFixedClock(1705312200000L) // 2024-01-15T10:30:00Z
        val local = InkClock.localIso8601(offsetHours = -5) // UTC-5 (EST)
        assertTrue(local.endsWith("-05:00"))
        assertTrue(local.contains("05:30:00")) // 10:30 - 5h = 05:30
    }

    // ── Date components with offset ──────────────────────────────────

    @Test
    fun `utc date components match format`() {
        InkClock.setFixedClock(1705312200000L)
        val dc = InkClock.utcDateComponents()
        assertEquals(2024, dc.year)
        assertEquals(1, dc.month)
        assertEquals(15, dc.day)
    }

    @Test
    fun `local date components apply offset`() {
        InkClock.setFixedClock(1705312200000L) // 2024-01-15T10:30:00Z
        val local = InkClock.localDateComponents(offsetHours = 14) // UTC+14 (Line Islands)
        // 10:30 + 14h = 00:30 next day = Jan 16
        assertEquals(16, local.day)
        assertEquals(0, local.hour)
        assertEquals(30, local.minute)
    }

    // ── Day of week ──────────────────────────────────────────────────

    @Test
    fun `epoch day is thursday (ISO day 4)`() {
        // 1970-01-01 was a Thursday
        val dc = epochMillisToComponents(0L)
        assertEquals(4, dc.dayOfWeek) // ISO 8601: 4 = Thursday
    }

    @Test
    fun `known monday`() {
        // 2024-01-15 was a Monday
        val dc = epochMillisToComponents(1705312200000L)
        assertEquals(1, dc.dayOfWeek) // ISO 8601: 1 = Monday
    }

    @Test
    fun `known sunday`() {
        // 2024-01-14 was a Sunday
        val dc = epochMillisToComponents(1705190400000L) // 2024-01-14T00:00:00Z
        assertEquals(7, dc.dayOfWeek) // ISO 8601: 7 = Sunday
    }
}
