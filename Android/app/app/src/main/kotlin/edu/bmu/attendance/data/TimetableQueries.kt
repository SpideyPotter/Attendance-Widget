package edu.bmu.attendance.data

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Pure helpers for Today / Next-class views over a [TimetableSnapshot]. */
object TimetableQueries {
    private val TIME_12H: DateTimeFormatter =
        DateTimeFormatter.ofPattern("h:mm a", Locale.US)

    fun sessionsForDate(
        snapshot: TimetableSnapshot,
        date: LocalDate = LocalDate.now(),
    ): List<TimetableSession> {
        val iso = date.toString()
        return snapshot.sessions.filter { it.dateIso == iso }
    }

    /**
     * Next upcoming session at or after [now]. Prefers remaining classes today;
     * otherwise the earliest later session in the cached week.
     */
    fun nextSession(
        snapshot: TimetableSnapshot,
        now: LocalDateTime = LocalDateTime.now(),
    ): TimetableSession? {
        val todayIso = now.toLocalDate().toString()
        val todayRemaining = snapshot.sessions
            .filter { it.dateIso == todayIso }
            .mapNotNull { session ->
                val start = parseTime(session.startTime) ?: return@mapNotNull null
                session to LocalDateTime.of(now.toLocalDate(), start)
            }
            .filter { (_, start) -> !start.isBefore(now) }
            .minByOrNull { it.second }
            ?.first
        if (todayRemaining != null) return todayRemaining

        return snapshot.sessions
            .mapNotNull { session ->
                val date = runCatching { LocalDate.parse(session.dateIso) }.getOrNull()
                    ?: return@mapNotNull null
                if (!date.isAfter(now.toLocalDate())) return@mapNotNull null
                val start = parseTime(session.startTime) ?: return@mapNotNull null
                session to LocalDateTime.of(date, start)
            }
            .minByOrNull { it.second }
            ?.first
    }

    fun parseTime(label: String): LocalTime? =
        runCatching { LocalTime.parse(label.trim().uppercase(Locale.US), TIME_12H) }.getOrNull()

    fun minutesUntilStart(
        session: TimetableSession,
        now: LocalDateTime = LocalDateTime.now(),
    ): Long? {
        val date = runCatching { LocalDate.parse(session.dateIso) }.getOrNull() ?: return null
        val start = parseTime(session.startTime) ?: return null
        val startDt = LocalDateTime.of(date, start)
        val minutes = java.time.Duration.between(now, startDt).toMinutes()
        return minutes
    }

    fun upcomingOrOngoingLabel(
        session: TimetableSession,
        now: LocalDateTime = LocalDateTime.now(),
    ): String {
        val minutes = minutesUntilStart(session, now) ?: return session.startTime
        return when {
            minutes <= 0L -> {
                val end = parseTime(session.endTime)
                val date = runCatching { LocalDate.parse(session.dateIso) }.getOrNull()
                if (end != null && date != null) {
                    val endDt = LocalDateTime.of(date, end)
                    if (now.isBefore(endDt)) "Now" else "Ended"
                } else {
                    "Now"
                }
            }
            minutes < 60L -> "in ${minutes}m"
            minutes < 24 * 60L -> {
                val hours = minutes / 60
                val rem = minutes % 60
                if (rem == 0L) "in ${hours}h" else "in ${hours}h ${rem}m"
            }
            else -> session.lectureDate
        }
    }

    /** Duration like `55m` / `1.5h` for timetable cards. */
    fun durationLabel(session: TimetableSession): String? {
        val start = parseTime(session.startTime) ?: return null
        val end = parseTime(session.endTime) ?: return null
        var minutes = java.time.Duration.between(start, end).toMinutes()
        if (minutes <= 0L) minutes += 24 * 60
        return when {
            minutes % 60L == 0L -> "${minutes / 60}h"
            minutes < 60L -> "${minutes}m"
            else -> {
                val hours = minutes / 60.0
                if (hours == hours.toLong().toDouble()) "${hours.toLong()}h"
                else String.format(Locale.US, "%.1fh", hours)
            }
        }
    }

    fun sessionKind(session: TimetableSession): String {
        val name = session.subjectName.lowercase(Locale.US)
        return when {
            "lab" in name -> "Lab"
            name.startsWith("prj") || "project" in name -> "Project"
            "tut" in name -> "Tutorial"
            else -> "Lecture"
        }
    }

    fun formatClockRange(session: TimetableSession): String {
        val start = parseTime(session.startTime)
        val end = parseTime(session.endTime)
        if (start == null || end == null) {
            return "${session.startTime} – ${session.endTime}"
        }
        val fmt = DateTimeFormatter.ofPattern("HH:mm", Locale.US)
        return "${start.format(fmt)} – ${end.format(fmt)}"
    }

    /** All calendar days in the cached week range (Sun…Sat). */
    fun weekDates(snapshot: TimetableSnapshot): List<LocalDate> {
        val start = runCatching {
            edu.bmu.attendance.data.TimetableDateRange.parsePortalDate(snapshot.startDate)
        }.getOrElse {
            snapshot.sessions.mapNotNull { runCatching { LocalDate.parse(it.dateIso) }.getOrNull() }
                .minOrNull() ?: return emptyList()
        }
        val end = runCatching {
            edu.bmu.attendance.data.TimetableDateRange.parsePortalDate(snapshot.endDate)
        }.getOrElse { start.plusDays(6) }
        val days = mutableListOf<LocalDate>()
        var cursor = start
        while (!cursor.isAfter(end)) {
            days += cursor
            cursor = cursor.plusDays(1)
        }
        return days
    }

    fun weekHeaderLabel(snapshot: TimetableSnapshot, today: LocalDate = LocalDate.now()): String {
        val dates = weekDates(snapshot)
        val anchor = dates.firstOrNull() ?: today
        val week = anchor.get(java.time.temporal.WeekFields.ISO.weekOfWeekBasedYear())
        val month = anchor.format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.US)).uppercase(Locale.US)
        return "WEEK $week · $month"
    }

    fun isNextClass(snapshot: TimetableSnapshot, session: TimetableSession): Boolean =
        nextSession(snapshot)?.id == session.id
}
