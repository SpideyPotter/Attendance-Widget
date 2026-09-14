package edu.bmu.attendance.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import edu.bmu.attendance.MainActivity
import edu.bmu.attendance.data.NotificationPrefsStore
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** Schedules the once-per-day “today’s classes” local notification. */
object TodayBriefingScheduler {
    const val ACTION_FIRE = "edu.bmu.attendance.action.TODAY_BRIEFING"
    const val NOTIFICATION_ID = 1001
    private const val REQUEST_CODE = 2001

    fun resync(context: Context) {
        val app = context.applicationContext
        NotificationChannels.ensureCreated(app)
        val prefs = NotificationPrefsStore.get(app)
        if (!prefs.isMorningBriefingEnabled()) {
            cancel(app)
            return
        }
        scheduleNext(app, prefs.morningBriefingHour(), prefs.morningBriefingMinute())
    }

    fun cancel(context: Context) {
        val app = context.applicationContext
        val alarmManager = app.getSystemService(AlarmManager::class.java) ?: return
        alarmManager.cancel(pendingIntent(app))
    }

    fun nextTriggerMillis(
        hour: Int,
        minute: Int,
        now: LocalDateTime = LocalDateTime.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Long {
        var target = LocalDateTime.of(now.toLocalDate(), LocalTime.of(hour, minute))
        if (!target.isAfter(now)) {
            target = target.plusDays(1)
        }
        return target.atZone(zone).toInstant().toEpochMilli()
    }

    private fun scheduleNext(context: Context, hour: Int, minute: Int) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val triggerAt = nextTriggerMillis(hour, minute)
        val pi = pendingIntent(context)
        val showIntent = PendingIntent.getActivity(
            context,
            REQUEST_CODE + 1,
            context.packageManager.getLaunchIntentForPackage(context.packageName)
                ?: Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        try {
            alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, showIntent), pi)
        } catch (_: SecurityException) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            } else {
                @Suppress("DEPRECATION")
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
        }
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, TodayBriefingReceiver::class.java).setAction(ACTION_FIRE)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
