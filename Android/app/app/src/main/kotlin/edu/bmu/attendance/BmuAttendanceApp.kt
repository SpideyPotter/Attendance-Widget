package edu.bmu.attendance

import android.app.Application
import androidx.glance.appwidget.updateAll
import edu.bmu.attendance.data.AttendanceRepository
import edu.bmu.attendance.widget.AttendanceWidget
import edu.bmu.attendance.widget.CompactAttendanceWidget
import edu.bmu.attendance.work.RefreshWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BmuAttendanceApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // Eagerly construct the repository (which warms EncryptedSharedPreferences,
        // which can be slow on first launch).
        AttendanceRepository.get(this)

        // Schedule the periodic refresh; safe to call repeatedly because
        // WorkManager dedupes by unique-name (see RefreshWorker).
        RefreshWorker.schedulePeriodic(this)

        // Repair any existing launcher widget instances after app reinstall
        // or process restart, before the next worker refresh has a chance to run.
        appScope.launch {
            AttendanceWidget().updateAll(this@BmuAttendanceApp)
            CompactAttendanceWidget().updateAll(this@BmuAttendanceApp)
        }
    }
}
