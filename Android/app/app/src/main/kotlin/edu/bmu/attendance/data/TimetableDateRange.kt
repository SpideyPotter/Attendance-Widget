package edu.bmu.attendance.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/** Portal date labels for `stu_getBetweenDatesTimetableForStudentSP.json`. */
data class TimetableDateRange(
    val start: LocalDate,
    val end: LocalDate,
) {
    val startLabel: String get() = formatPortalDate(start)
    val endLabel: String get() = formatPortalDate(end)

    companion object {
        private val PORTAL_DATE: DateTimeFormatter =
            DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)

        fun formatPortalDate(date: LocalDate): String = date.format(PORTAL_DATE)

        /**
         * Current week Sunday…Saturday, matching the Maitri "Weeks / Current Week" filter.
         * Optional [startLabel]/[endLabel] override either bound when already portal-formatted.
         */
        fun currentWeek(
            startLabel: String? = null,
            endLabel: String? = null,
            today: LocalDate = LocalDate.now(),
        ): TimetableDateRange {
            val sunday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
            val saturday = sunday.plusDays(6)
            val start = startLabel?.let { parsePortalDate(it) } ?: sunday
            val end = endLabel?.let { parsePortalDate(it) } ?: saturday
            return TimetableDateRange(start = start, end = end)
        }

        fun parsePortalDate(label: String): LocalDate =
            LocalDate.parse(label.trim(), PORTAL_DATE)
    }
}
