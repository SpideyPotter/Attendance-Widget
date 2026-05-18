package edu.bmu.attendance.work

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.updateAll
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import edu.bmu.attendance.data.AttendanceRepository
import edu.bmu.attendance.widget.AttendanceWidget
import edu.bmu.attendance.widget.CompactAttendanceWidget
import java.util.concurrent.TimeUnit

class RefreshWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val mode = inputData.getString(KEY_MODE) ?: MODE_RATE_LIMITED
        val repo = AttendanceRepository.get(applicationContext)

        val refreshResult = when (mode) {
            MODE_FORCE -> repo.forceRefresh()
            else -> repo.refresh()
        }

        return when (refreshResult) {
            is AttendanceRepository.RefreshResult.Success -> {
                AttendanceWidget().updateAll(applicationContext)
                CompactAttendanceWidget().updateAll(applicationContext)
                Log.i(TAG, "Refresh OK (network=${refreshResult.networkCallMade})")
                Result.success()
            }
            is AttendanceRepository.RefreshResult.Failure -> {
                Log.w(TAG, "Refresh failed: ${refreshResult.error.message}")
                AttendanceWidget().updateAll(applicationContext)
                CompactAttendanceWidget().updateAll(applicationContext)
                // Network/portal errors → retry with backoff. Auth errors don't
                // recover by retrying, so we mark them as success and let the
                // widget show the stale data with the error visible.
                if (refreshResult.error is edu.bmu.attendance.data.MaitriError.Network) {
                    Result.retry()
                } else {
                    Result.success()
                }
            }
            AttendanceRepository.RefreshResult.MissingCredentials -> {
                AttendanceWidget().updateAll(applicationContext)
                CompactAttendanceWidget().updateAll(applicationContext)
                Result.success()
            }
        }
    }

    companion object {
        private const val TAG = "RefreshWorker"

        private const val UNIQUE_PERIODIC = "bmu_attendance_periodic_refresh"
        private const val UNIQUE_ONE_SHOT = "bmu_attendance_one_shot_refresh"

        private const val KEY_MODE = "mode"
        private const val MODE_RATE_LIMITED = "rate_limited"
        private const val MODE_FORCE = "force"

        /**
         * Schedule the recurring refresh. WorkManager dedupes by unique-name,
         * so calling this multiple times is safe and a no-op after the first.
         */
        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<RefreshWorker>(
                15, TimeUnit.MINUTES, // matches user's chosen widget cadence
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        fun cancelPeriodic(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_PERIODIC)
        }

        /**
         * Trigger an immediate, force-refresh attempt (ignores rate limiter).
         * Used after settings save and on the widget's refresh button.
         */
        fun enqueueOneShot(context: Context) {
            val request = OneTimeWorkRequestBuilder<RefreshWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .setInputData(
                    androidx.work.workDataOf(KEY_MODE to MODE_FORCE),
                )
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_ONE_SHOT,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }
    }
}
