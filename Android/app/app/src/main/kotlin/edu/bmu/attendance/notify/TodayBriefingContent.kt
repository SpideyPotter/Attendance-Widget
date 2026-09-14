package edu.bmu.attendance.notify

import edu.bmu.attendance.data.TimetableQueries
import edu.bmu.attendance.data.TimetableSession
import edu.bmu.attendance.data.TimetableSnapshot
import edu.bmu.attendance.data.VenueStore
import java.time.LocalDate

/** Pure helpers for the morning “today’s classes” notification body. */
object TodayBriefingContent {
    data class Payload(
        val title: String,
        val body: String,
    )

    fun build(
        snapshot: TimetableSnapshot?,
        resolveVenue: (TimetableSession) -> String,
        today: LocalDate = LocalDate.now(),
    ): Payload {
        if (snapshot == null) {
            return Payload(
                title = "Today's classes",
                body = "Refresh attendance to load this week’s timetable.",
            )
        }
        val sessions = TimetableQueries.sessionsForDate(snapshot, today)
            .sortedWith(
                compareBy(
                    { TimetableQueries.parseTime(it.startTime) },
                    { it.subjectName },
                ),
            )
        if (sessions.isEmpty()) {
            return Payload(
                title = "Today's classes",
                body = "No classes scheduled today.",
            )
        }
        val title = when (sessions.size) {
            1 -> "1 class today"
            else -> "${sessions.size} classes today"
        }
        val body = sessions.joinToString(separator = "\n") { session ->
            line(session, resolveVenue(session))
        }
        return Payload(title = title, body = body)
    }

    fun line(session: TimetableSession, venue: String): String {
        val time = TimetableQueries.formatClockRange(session)
        val room = venue.trim().takeIf { it.isNotEmpty() && it != "-" && it != VenueStore.TBA }
        return if (room != null) {
            "$time · ${session.subjectName} · $room"
        } else {
            "$time · ${session.subjectName}"
        }
    }
}
