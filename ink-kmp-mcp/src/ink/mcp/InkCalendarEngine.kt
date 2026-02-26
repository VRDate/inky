package ink.mcp

import net.fortuna.ical4j.data.CalendarBuilder
import net.fortuna.ical4j.data.CalendarOutputter
import net.fortuna.ical4j.model.Calendar
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.*
import net.fortuna.ical4j.model.property.immutable.ImmutableCalScale
import net.fortuna.ical4j.model.property.immutable.ImmutableVersion
import org.slf4j.LoggerFactory
import java.io.StringReader
import java.io.StringWriter
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * iCal4j-based calendar engine for managing game/story events.
 *
 * Manages ICS calendar files for:
 *   - Story milestones (chapter completions, plot points)
 *   - Game sessions (play dates, session durations)
 *   - Deadlines (quest timers, narrative deadlines)
 *   - Scheduled events (NPC events, world events)
 *
 * Each calendar is identified by a string ID (typically matching a story/doc ID).
 */
class InkCalendarEngine {

    private val log = LoggerFactory.getLogger(InkCalendarEngine::class.java)

    /** In-memory calendar store: calendarId -> Calendar */
    private val calendars = ConcurrentHashMap<String, Calendar>()

    data class InkEvent(
        val uid: String = UUID.randomUUID().toString(),
        val summary: String,
        val description: String? = null,
        val dtStart: String,
        val dtEnd: String? = null,
        val category: String? = null,
        val location: String? = null,
        val status: String? = null
    )

    /** Create a game event in a calendar */
    fun createEvent(calendarId: String, event: InkEvent): Map<String, Any> {
        val calendar = getOrCreateCalendar(calendarId)

        val vEvent = VEvent()
        vEvent.add<VEvent>(Summary(event.summary))
        vEvent.add<VEvent>(Uid(event.uid))
        vEvent.add<VEvent>(DtStart<java.time.temporal.Temporal>(event.dtStart))
        event.dtEnd?.let { vEvent.add<VEvent>(DtEnd<java.time.temporal.Temporal>(it)) }
        event.description?.let { vEvent.add<VEvent>(Description(it)) }
        event.category?.let { vEvent.add<VEvent>(Categories(it)) }
        event.location?.let { vEvent.add<VEvent>(Location(it)) }
        event.status?.let { vEvent.add<VEvent>(Status(it)) }

        calendar.add<Calendar>(vEvent)
        log.info("Created event '{}' in calendar '{}'", event.summary, calendarId)

        return mapOf(
            "ok" to true,
            "calendar_id" to calendarId,
            "event_uid" to event.uid,
            "summary" to event.summary,
            "dt_start" to event.dtStart
        )
    }

    /** List events in a calendar, optionally filtered by category */
    fun listEvents(calendarId: String, category: String? = null): List<Map<String, Any>> {
        val calendar = calendars[calendarId]
            ?: return emptyList()

        val events: List<VEvent> = calendar.getComponents("VEVENT")
        return events
            .filter { event ->
                if (category == null) true
                else event.getCategories()
                    .map { it.value }
                    .orElse("")
                    .contains(category, ignoreCase = true)
            }
            .map { vEventToMap(it) }
    }

    /** Export a calendar as ICS string */
    fun exportIcs(calendarId: String): String {
        val calendar = calendars[calendarId]
            ?: throw IllegalArgumentException("Calendar not found: $calendarId")

        val writer = StringWriter()
        CalendarOutputter().output(calendar, writer)
        return writer.toString()
    }

    /** Import events from ICS content into a calendar */
    fun importIcs(calendarId: String, icsContent: String): Map<String, Any> {
        val imported = CalendarBuilder().build(StringReader(icsContent))
        val calendar = getOrCreateCalendar(calendarId)

        val events: List<VEvent> = imported.getComponents("VEVENT")
        events.forEach { calendar.add<Calendar>(it) }

        log.info("Imported {} events into calendar '{}'", events.size, calendarId)
        return mapOf(
            "ok" to true,
            "calendar_id" to calendarId,
            "imported_count" to events.size
        )
    }

    /** List all calendars */
    fun listCalendars(): List<Map<String, Any>> {
        return calendars.map { (id, cal) ->
            val events: List<VEvent> = cal.getComponents("VEVENT")
            mapOf(
                "calendar_id" to id,
                "event_count" to events.size
            )
        }
    }

    private fun getOrCreateCalendar(calendarId: String): Calendar {
        return calendars.getOrPut(calendarId) {
            Calendar().apply {
                add<Calendar>(ProdId("-//Inky MCP//Game Events//EN"))
                add<Calendar>(ImmutableVersion.VERSION_2_0)
                add<Calendar>(ImmutableCalScale.GREGORIAN)
            }.also {
                log.info("Created calendar: {}", calendarId)
            }
        }
    }

    private fun vEventToMap(event: VEvent): Map<String, Any> {
        val map = mutableMapOf<String, Any>()
        event.getSummary().ifPresent { map["summary"] = it.value }
        event.getProperty<Uid>("UID").ifPresent { map["uid"] = it.value }
        event.getProperty<DtStart<*>>("DTSTART").ifPresent { map["dt_start"] = it.value }
        event.getProperty<DtEnd<*>>("DTEND").ifPresent { map["dt_end"] = it.value }
        event.getDescription().ifPresent { map["description"] = it.value }
        event.getCategories().ifPresent { map["category"] = it.value }
        event.getLocation().ifPresent { map["location"] = it.value }
        event.getStatus().ifPresent { map["status"] = it.value }
        return map
    }
}
