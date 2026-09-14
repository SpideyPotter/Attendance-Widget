package edu.bmu.attendance.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

object NotificationChannels {
    const val TODAY_BRIEFING = "today_briefing"

    fun ensureCreated(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(TODAY_BRIEFING) != null) return
        val channel = NotificationChannel(
            TODAY_BRIEFING,
            "Today's classes",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Morning summary of today’s timetable"
        }
        manager.createNotificationChannel(channel)
    }
}
