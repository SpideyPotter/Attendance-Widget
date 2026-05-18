package edu.bmu.attendance.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object RelativeTime {
    fun label(millis: Long): String {
        if (millis <= 0L) return "—"
        val delta = System.currentTimeMillis() - millis
        return when {
            delta < 60_000L -> "just now"
            delta < 3_600_000L -> "${delta / 60_000L}m ago"
            delta < 86_400_000L -> "${delta / 3_600_000L}h ago"
            else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(millis))
        }
    }
}
