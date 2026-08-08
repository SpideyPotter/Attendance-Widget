package edu.bmu.attendance.data

import android.content.Context

/**
 * Orchestrates [MaitriClient], [CredentialStore], [SnapshotStore], and [TimetableStore].
 *
 * Includes a small **rate limiter** (default 10 minutes): a refresh request
 * younger than that simply returns the cached snapshots. This keeps two
 * simultaneous triggers (e.g. WorkManager periodic + a user tap on Refresh)
 * from hammering Maitri, while still respecting the user's chosen 15-minute
 * widget refresh cadence.
 */
class AttendanceRepository(
    private val credentialStore: CredentialStore,
    private val snapshotStore: SnapshotStore,
    private val timetableStore: TimetableStore,
    private val client: MaitriClient = MaitriClient(),
    private val minIntervalMillis: Long = DEFAULT_MIN_INTERVAL_MILLIS,
    private val now: () -> Long = System::currentTimeMillis,
) {
    val cachedSnapshot: AttendanceSnapshot? get() = snapshotStore.load()
    val cachedTimetable: TimetableSnapshot? get() = timetableStore.load()

    /** Result of a [refresh] / [forceRefresh] call. */
    sealed class RefreshResult {
        data class Success(
            val snapshot: AttendanceSnapshot,
            val timetable: TimetableSnapshot?,
            val networkCallMade: Boolean,
        ) : RefreshResult()
        data class Failure(val error: MaitriError) : RefreshResult()
        object MissingCredentials : RefreshResult()
    }

    /**
     * Fetch fresh attendance + timetable unless the attendance cache is younger
     * than [minIntervalMillis], in which case both caches are returned as-is.
     */
    suspend fun refresh(): RefreshResult {
        val cached = snapshotStore.load()
        if (cached != null && now() - cached.fetchedAtMillis < minIntervalMillis) {
            return RefreshResult.Success(
                snapshot = cached,
                timetable = timetableStore.load(),
                networkCallMade = false,
            )
        }
        return forceRefresh()
    }

    /** Always hit the network (one login for attendance + current-week timetable). */
    suspend fun forceRefresh(): RefreshResult {
        val creds = credentialStore.load() ?: return RefreshResult.MissingCredentials
        return try {
            val (attendance, timetable) = client.fetchAttendanceAndTimetable(creds)
            snapshotStore.save(attendance)
            if (timetable != null) {
                timetableStore.save(timetable)
            }
            RefreshResult.Success(
                snapshot = attendance,
                timetable = timetable ?: timetableStore.load(),
                networkCallMade = true,
            )
        } catch (e: MaitriError) {
            RefreshResult.Failure(e)
        } catch (e: Exception) {
            RefreshResult.Failure(MaitriError.Portal(e.message ?: "Unexpected error"))
        }
    }

    /**
     * Test the supplied credentials without persisting them or affecting the
     * cached snapshot. Used by the Settings "Test login" button.
     */
    suspend fun testCredentials(creds: Credentials): Result<AttendanceSnapshot> = runCatching {
        client.fetchAttendance(creds)
    }

    fun clearCaches() {
        snapshotStore.clear()
        timetableStore.clear()
    }

    companion object {
        const val DEFAULT_MIN_INTERVAL_MILLIS: Long = 10 * 60 * 1000L

        /** App-wide singleton. Cheap to construct, but reused across UI/widget/worker. */
        @Volatile private var instance: AttendanceRepository? = null

        fun get(context: Context): AttendanceRepository {
            return instance ?: synchronized(this) {
                instance ?: AttendanceRepository(
                    credentialStore = CredentialStore(context),
                    snapshotStore = SnapshotStore(context),
                    timetableStore = TimetableStore(context),
                ).also { instance = it }
            }
        }
    }
}
