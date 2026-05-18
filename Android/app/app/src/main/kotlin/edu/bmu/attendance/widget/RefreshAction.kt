package edu.bmu.attendance.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import edu.bmu.attendance.work.RefreshWorker

/**
 * Glance click handler for the widget's refresh button. Enqueues a one-shot
 * [RefreshWorker]; the worker calls back into the widget when it's done so
 * Glance re-renders with fresh data.
 */
class RefreshAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        RefreshWorker.enqueueOneShot(context)
    }
}
