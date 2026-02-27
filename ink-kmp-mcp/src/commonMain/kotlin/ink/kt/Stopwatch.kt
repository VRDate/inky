package ink.kt

import kotlinx.datetime.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/**
 * Unified time authority for the ink runtime and MCP server.
 *
 * Three concerns, one source of truth:
 * 1. **Monotonic timing** — `Stopwatch` for Story.continueInternal() async budget (nano precision)
 * 2. **Wall clock** — `InkClock` for event timestamps, backup filenames, proto `int64 timestamp`
 * 3. **Calendar** — `InkClock` for UTC/local datetime, timezone offsets, ISO 8601 formatting
 *
 * Replaces scattered `System.currentTimeMillis()` across 6 engines:
 * - InkAssetEventEngine (4 calls), InkDebugEngine, ColabEngine, StoryState seed
 *
 * Replaces `java.time.Instant` + `ZoneOffset.UTC` in InkWebDavEngine.
 *
 * Design decisions:
 * - `Stopwatch` stays in `ink.kt` — used by Story.kt, same package
 * - `InkClock` is an `object` singleton — consistent time source, testable via `clockSource` override
 * - **commonMain compatible** — `kotlin.time` for monotonic, `kotlinx-datetime` for wall clock
 *   via `Clock.System.now()`. No platform-specific code needed.
 * - **Nano precision** — monotonic `TimeSource.Monotonic` gives nano precision for profiling
 * - **UTC default** — all timestamps are UTC epoch millis unless explicitly local
 * - **ISO 8601** — date formatting follows iCal4j and proto conventions
 *
 * KMP deps: `kotlin.time` (monotonic) + `kotlinx-datetime` (wall clock via Clock.System).
 *
 * Three-way comparison:
 * - C#: System.Diagnostics.Stopwatch (high resolution, 100ns ticks)
 * - Java: custom Stopwatch.java with System.nanoTime() — 147 lines, formatTime with Calendar/SimpleDateFormat
 * - Kotlin: kotlin.time.TimeSource.Monotonic — cross-platform, 100 lines total
 */

// ── Stopwatch ────────────────────────────────────────────────────────

/**
 * Monotonic stopwatch for timing ink story evaluation.
 *
 * Used by Story.continueInternal() for async time-limited evaluation.
 * Nano precision via kotlin.time.TimeSource.Monotonic.
 *
 * C#: System.Diagnostics.Stopwatch — Start/Stop/Reset/ElapsedMilliseconds
 * Java: custom Stopwatch with System.nanoTime() — 147 lines
 * Kotlin: 30 lines — TimeSource.Monotonic handles all complexity
 */
class Stopwatch {
    private val timeSource = TimeSource.Monotonic
    private var mark: TimeSource.Monotonic.ValueTimeMark? = null
    private var stoppedDuration: Duration = Duration.ZERO
    private var running: Boolean = false

    fun start() {
        mark = timeSource.markNow()
        running = true
    }

    fun stop() {
        stoppedDuration = mark?.elapsedNow() ?: Duration.ZERO
        running = false
    }

    fun reset() {
        mark = null
        stoppedDuration = Duration.ZERO
        running = false
    }

    /** Elapsed time as Duration — full precision (nanoseconds). */
    val elapsed: Duration
        get() = if (running) mark?.elapsedNow() ?: Duration.ZERO
                else stoppedDuration

    /** Elapsed milliseconds — for Story.continueInternal() async budget. */
    val elapsedMilliseconds: Long get() = elapsed.inWholeMilliseconds

    /** Elapsed nanoseconds — for Profiler step timing. */
    val elapsedNanoseconds: Long get() = elapsed.inWholeNanoseconds

    /** Elapsed microseconds — for fine-grained profiling. */
    val elapsedMicroseconds: Long get() = elapsed.inWholeMicroseconds
}

// ── InkClock ─────────────────────────────────────────────────────────

/**
 * Wall-clock time authority for the ink ecosystem.
 *
 * Provides:
 * - **Epoch millis** — proto `int64 timestamp`, event timestamps, created_at fields
 * - **Epoch nanos** — nano-precision backup timestamps (replaces Instant.now())
 * - **UTC datetime** — ISO 8601 formatted strings for iCal, WebDAV, proto string fields
 * - **Local datetime** — for display, with timezone offset
 * - **Monotonic mark** — for creating new Stopwatch instances at a consistent time source
 *
 * All timestamps are UTC by default. Local time requires explicit timezone.
 *
 * Usage:
 * ```kotlin
 * // Replace: System.currentTimeMillis()
 * val ts = InkClock.epochMillis()
 *
 * // Replace: Instant.now()
 * val nanos = InkClock.epochNanos()
 *
 * // Replace: DateTimeFormatter.format(Instant.now())
 * val iso = InkClock.utcIso8601()
 *
 * // Replace: backupTimestampFormat.format(Instant.now())
 * val backup = InkClock.utcBackupTimestamp()
 *
 * // For iCal4j DtStart strings
 * val ical = InkClock.utcICalTimestamp()
 * ```
 *
 * Testability: Override `clockSource` for deterministic tests.
 */
object InkClock {

    /**
     * Pluggable clock source for testing.
     * Default returns real system time. Override in tests for determinism.
     *
     * Returns: epoch milliseconds (UTC).
     */
    var clockSource: () -> Long = { platformEpochMillis() }

    /**
     * Pluggable nano clock source for high-precision timestamps.
     * Default returns real system nanos since epoch via kotlinx-datetime. Override in tests.
     *
     * Returns: epoch nanoseconds (UTC).
     */
    var nanoClockSource: () -> Long = { platformEpochNanos() }

    // ── Epoch timestamps ──

    /** UTC epoch milliseconds. Replaces `System.currentTimeMillis()`. */
    fun epochMillis(): Long = clockSource()

    /** UTC epoch nanoseconds. For nano-precision backup timestamps. */
    fun epochNanos(): Long = nanoClockSource()

    /** UTC epoch seconds. For coarse timestamps. */
    fun epochSeconds(): Long = epochMillis() / 1000L

    // ── ISO 8601 formatting ──

    /**
     * UTC ISO 8601 timestamp: `2025-01-15T10:30:00.000Z`
     * For proto string fields, log messages, display.
     */
    fun utcIso8601(): String {
        val ms = epochMillis()
        return formatIso8601(ms)
    }

    /**
     * UTC backup timestamp: `2025-01-15_10-30-00.000000000`
     * Replaces: `DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss.SSSSSSSSS").withZone(ZoneOffset.UTC)`
     * For WebDAV backup filenames — nano precision, filesystem-safe characters.
     */
    fun utcBackupTimestamp(): String {
        val nanos = epochNanos()
        return formatBackupTimestamp(nanos)
    }

    /**
     * UTC iCalendar timestamp: `20250115T103000Z`
     * For iCal4j DtStart/DtEnd string values.
     */
    fun utcICalTimestamp(): String {
        val ms = epochMillis()
        return formatICalTimestamp(ms)
    }

    // ── Local time with offset ──

    /**
     * Local ISO 8601 with offset: `2025-01-15T12:30:00.000+02:00`
     * @param offsetHours UTC offset in hours (e.g., 2 for IST, -5 for EST)
     * @param offsetMinutes Additional minutes (default 0)
     */
    fun localIso8601(offsetHours: Int, offsetMinutes: Int = 0): String {
        val totalOffsetMs = (offsetHours * 3600L + offsetMinutes * 60L) * 1000L
        val localMs = epochMillis() + totalOffsetMs
        val offsetStr = formatOffset(offsetHours, offsetMinutes)
        return formatIso8601NoZ(localMs) + offsetStr
    }

    // ── Calendar system helpers ──

    /**
     * Date components from epoch millis (UTC).
     * For calendar systems that need year/month/day decomposition.
     */
    fun utcDateComponents(epochMs: Long = epochMillis()): DateComponents {
        return epochMillisToComponents(epochMs)
    }

    /**
     * Date components with timezone offset.
     */
    fun localDateComponents(offsetHours: Int, offsetMinutes: Int = 0, epochMs: Long = epochMillis()): DateComponents {
        val totalOffsetMs = (offsetHours * 3600L + offsetMinutes * 60L) * 1000L
        return epochMillisToComponents(epochMs + totalOffsetMs)
    }

    // ── Seed generation ──

    /**
     * Generate a time-based seed for story random number generation.
     * Replaces: `System.currentTimeMillis()` in StoryState seed init.
     */
    fun timeSeed(): Int = (epochMillis() % Int.MAX_VALUE).toInt()

    // ── Retention / duration helpers ──

    /**
     * Epoch millis for N days ago. For backup retention cutoff.
     * Replaces: `Instant.now().minus(retentionDays, ChronoUnit.DAYS)`
     */
    fun epochMillisMinusDays(days: Long): Long = epochMillis() - (days * 24L * 3600L * 1000L)

    /**
     * Duration between two epoch millis timestamps, as kotlin.time.Duration.
     */
    fun durationBetween(startMs: Long, endMs: Long): Duration =
        (endMs - startMs).milliseconds

    // ── Testing support ──

    /**
     * Reset clock sources to real system time.
     * Call after tests that override clockSource/nanoClockSource.
     */
    fun resetToSystemClock() {
        clockSource = { platformEpochMillis() }
        nanoClockSource = { platformEpochNanos() }
    }

    /**
     * Set a fixed clock for deterministic testing.
     * @param fixedMs Fixed epoch milliseconds to return.
     */
    fun setFixedClock(fixedMs: Long) {
        clockSource = { fixedMs }
        nanoClockSource = { fixedMs * 1_000_000L }
    }
}

// ── DateComponents ───────────────────────────────────────────────────

/**
 * Decomposed date/time components.
 * Calendar-system agnostic — provides Gregorian year/month/day/hour/min/sec/ms/ns.
 * For calendar engines that need structured date access.
 */
data class DateComponents(
    val year: Int,
    val month: Int,       // 1-12
    val day: Int,         // 1-31
    val hour: Int,        // 0-23
    val minute: Int,      // 0-59
    val second: Int,      // 0-59
    val millisecond: Int, // 0-999
    val nanosecond: Long = millisecond * 1_000_000L, // full nano precision
    val dayOfWeek: Int = 0,  // 1=Monday..7=Sunday (ISO 8601)
    val dayOfYear: Int = 0   // 1-366
)

// ── Private formatting functions ─────────────────────────────────────

/**
 * Format epoch millis as ISO 8601 UTC: `2025-01-15T10:30:00.000Z`
 * Pure arithmetic — no java.time dependency.
 */
private fun formatIso8601(epochMs: Long): String {
    val dc = epochMillisToComponents(epochMs)
    return "${dc.year.pad4()}-${dc.month.pad2()}-${dc.day.pad2()}" +
           "T${dc.hour.pad2()}:${dc.minute.pad2()}:${dc.second.pad2()}.${dc.millisecond.pad3()}Z"
}

private fun formatIso8601NoZ(epochMs: Long): String {
    val dc = epochMillisToComponents(epochMs)
    return "${dc.year.pad4()}-${dc.month.pad2()}-${dc.day.pad2()}" +
           "T${dc.hour.pad2()}:${dc.minute.pad2()}:${dc.second.pad2()}.${dc.millisecond.pad3()}"
}

/**
 * Format epoch nanos as backup timestamp: `2025-01-15_10-30-00.000000000`
 * Filesystem-safe characters. Nano precision from epochNanos.
 */
private fun formatBackupTimestamp(epochNanos: Long): String {
    val epochMs = epochNanos / 1_000_000L
    val nanoFraction = epochNanos % 1_000_000_000L
    val dc = epochMillisToComponents(epochMs)
    return "${dc.year.pad4()}-${dc.month.pad2()}-${dc.day.pad2()}" +
           "_${dc.hour.pad2()}-${dc.minute.pad2()}-${dc.second.pad2()}" +
           ".${nanoFraction.toString().padStart(9, '0')}"
}

/**
 * Format epoch millis as iCalendar timestamp: `20250115T103000Z`
 */
private fun formatICalTimestamp(epochMs: Long): String {
    val dc = epochMillisToComponents(epochMs)
    return "${dc.year.pad4()}${dc.month.pad2()}${dc.day.pad2()}" +
           "T${dc.hour.pad2()}${dc.minute.pad2()}${dc.second.pad2()}Z"
}

private fun formatOffset(hours: Int, minutes: Int): String {
    val sign = if (hours >= 0) "+" else "-"
    val absHours = kotlin.math.abs(hours)
    val absMinutes = kotlin.math.abs(minutes)
    return "$sign${absHours.pad2()}:${absMinutes.pad2()}"
}

// ── Epoch millis to date components (Gregorian civil calendar) ────────

/**
 * Convert UTC epoch millis to Gregorian date components.
 * Pure arithmetic implementation — no java.time, no platform deps.
 *
 * Algorithm based on Howard Hinnant's civil_from_days:
 * https://howardhinnant.github.io/date_algorithms.html
 * This is the standard algorithm used by C++20 chrono and other date libraries.
 */
internal fun epochMillisToComponents(epochMs: Long): DateComponents {
    val totalSeconds = if (epochMs >= 0) epochMs / 1000 else (epochMs - 999) / 1000
    val ms = (epochMs - totalSeconds * 1000).toInt()

    val totalDays = if (totalSeconds >= 0) totalSeconds / 86400 else (totalSeconds - 86399) / 86400
    val daySeconds = (totalSeconds - totalDays * 86400).toInt()

    val hour = daySeconds / 3600
    val minute = (daySeconds % 3600) / 60
    val second = daySeconds % 60

    // Howard Hinnant's civil_from_days algorithm
    val z = totalDays + 719468L
    val era = (if (z >= 0) z else z - 146096) / 146097
    val doe = (z - era * 146097).toInt()           // day of era [0, 146096]
    val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
    val y = yoe + era * 400
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val d = doy - (153 * mp + 2) / 5 + 1
    val m = if (mp < 10) mp + 3 else mp - 9
    val year = (y + if (m <= 2) 1 else 0).toInt()

    // Day of week: 0 = Sunday in some systems, but ISO 8601 says 1=Monday
    val dow = ((totalDays + 4) % 7).toInt() // epoch day 0 = Thursday (4)
    val isoDow = if (dow == 0) 7 else dow   // convert 0=Sun to 7

    // Day of year
    val dayOfYear = doy + (if (m > 2) 1 else 0) // approximate

    return DateComponents(
        year = year,
        month = m,
        day = d,
        hour = hour,
        minute = minute,
        second = second,
        millisecond = ms,
        nanosecond = ms * 1_000_000L,
        dayOfWeek = isoDow,
        dayOfYear = dayOfYear
    )
}

// ── Platform time sources (KMP via kotlinx-datetime) ────────────────

/**
 * Wall clock epoch millis via kotlinx-datetime Clock.System.
 * KMP compatible — works on JVM, JS, Native.
 *
 * Replaces: System.currentTimeMillis() (JVM-only)
 */
internal fun platformEpochMillis(): Long =
    Clock.System.now().toEpochMilliseconds()

/**
 * Wall clock epoch nanoseconds via kotlinx-datetime Clock.System.
 * KMP compatible. Uses Instant.nanosecondsOfSecond for sub-ms precision.
 *
 * Replaces: System.nanoTime() (JVM-only, and was monotonic not epoch anyway)
 */
internal fun platformEpochNanos(): Long {
    val now = Clock.System.now()
    return now.epochSeconds * 1_000_000_000L + now.nanosecondsOfSecond
}

// ── Padding helpers ──────────────────────────────────────────────────

private fun Int.pad2(): String = toString().padStart(2, '0')
private fun Int.pad3(): String = toString().padStart(3, '0')
private fun Int.pad4(): String = toString().padStart(4, '0')
