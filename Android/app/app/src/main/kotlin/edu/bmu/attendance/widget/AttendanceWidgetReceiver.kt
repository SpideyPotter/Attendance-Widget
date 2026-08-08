package edu.bmu.attendance.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import edu.bmu.attendance.work.RefreshWorker

class AttendanceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AttendanceWidget()

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        // When a fresh widget is placed (or the system asks for an update),
        // make sure our periodic refresh job is alive and trigger a one-shot
        // so the new widget instance shows real data ASAP.
        RefreshWorker.schedulePeriodic(context)
        RefreshWorker.enqueueOneShot(context)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        RefreshWorker.schedulePeriodic(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        cancelPeriodicIfNoWidgetsRemain(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
    }
}

class CompactAttendanceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CompactAttendanceWidget()

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        RefreshWorker.schedulePeriodic(context)
        RefreshWorker.enqueueOneShot(context)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        RefreshWorker.schedulePeriodic(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        cancelPeriodicIfNoWidgetsRemain(context)
    }
}

class TransparentAttendanceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TransparentAttendanceWidget()

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        RefreshWorker.schedulePeriodic(context)
        RefreshWorker.enqueueOneShot(context)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        RefreshWorker.schedulePeriodic(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        cancelPeriodicIfNoWidgetsRemain(context)
    }
}

/**
 * Periodic refresh is shared by all widget sizes. Only cancel when the last
 * widget of *any* type has been removed.
 */
private fun cancelPeriodicIfNoWidgetsRemain(context: Context) {
    val manager = AppWidgetManager.getInstance(context)
    val receivers = listOf(
        AttendanceWidgetReceiver::class.java,
        CompactAttendanceWidgetReceiver::class.java,
        TransparentAttendanceWidgetReceiver::class.java,
    )
    val anyLeft = receivers.any { cls ->
        manager.getAppWidgetIds(ComponentName(context, cls)).isNotEmpty()
    }
    if (!anyLeft) {
        RefreshWorker.cancelPeriodic(context)
    }
}
