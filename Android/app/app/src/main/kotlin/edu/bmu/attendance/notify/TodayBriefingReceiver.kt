package edu.bmu.attendance.notify

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import edu.bmu.attendance.MainActivity
import edu.bmu.attendance.R
import edu.bmu.attendance.data.NotificationPrefsStore
import edu.bmu.attendance.data.TimetableStore
import edu.bmu.attendance.data.VenueStore

/**
 * Fires the morning briefing and reschedules the next day.
 * Also re-arms after boot / time changes when the feature is enabled.
 */
class TodayBriefingReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val app = context.applicationContext
        val action = intent?.action
        val prefs = NotificationPrefsStore.get(app)

        when (action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            -> {
                if (prefs.isMorningBriefingEnabled()) {
                    TodayBriefingScheduler.resync(app)
                }
                return
            }
            TodayBriefingScheduler.ACTION_FIRE -> {
                if (!prefs.isMorningBriefingEnabled()) return
                postBriefing(app)
                TodayBriefingScheduler.resync(app)
            }
        }
    }

    private fun postBriefing(context: Context) {
        if (!canPostNotifications(context)) return
        NotificationChannels.ensureCreated(context)
        val venueStore = VenueStore.get(context)
        val payload = TodayBriefingContent.build(
            snapshot = TimetableStore(context).load(),
            resolveVenue = { venueStore.resolve(it) },
        )
        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, NotificationChannels.TODAY_BRIEFING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(payload.title)
            .setContentText(payload.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(payload.body))
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(context)
            .notify(TodayBriefingScheduler.NOTIFICATION_ID, notification)
    }

    private fun canPostNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }
}
