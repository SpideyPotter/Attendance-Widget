package edu.bmu.attendance.data

import android.content.Context

/**
 * Orchestrates [MaitriClient], [CredentialStore] and [SnapshotStore].
 *
 * Includes a small **rate limiter** (default 10 minutes): a refresh request
 * younger than that simply returns the cached snapshot. This keeps two
 * simultaneous triggers (e.g. WorkManager periodic + a user tap on Refresh)
 * from hammering Maitri, while still respecting the user's chosen 15-minute
 * widget refresh cadence.
 */
class AttendanceRepository(
    private val credentialStore: CredentialStore,
    private val snapshotStore: SnapshotStore,
    private val client: MaitriClient = MaitriClient(),
    private val minIntervalMillis: Long = DEFAULT_MIN_INTERVAL_MILLIS,
    private val now: () -> Long = System::currentTimeMillis,
) {
    val cachedSnapshot: AttendanceSnapshot? get() = snapshotStore.load()

    /** Result of a [refresh] / [forceRefresh] call. */
    sealed class RefreshResult {
        data class Success(val snapshot: AttendanceSnapshot, val networkCallMade: Boolean) : RefreshResult()
        data class Failure(val error: MaitriError) : RefreshResult()
        object MissingCredentials : RefreshResult()
    }

    /**
     * Fetch fresh attendance unless the cached snapshot is younger than
     * [minIntervalMillis], in which case the cache is returned as-is.
     */
    suspend fun refresh(): RefreshResult {
        val cached = snapshotStore.load()
        if (cached != null && now() - cached.fetchedAtMillis < minIntervalMillis) {
            return RefreshResult.Success(cached, networkCallMade = false)
        }
        return forceRefresh()
    }

    /** Always hit the network, ignoring the cache age. */
    suspend fun forceRefresh(): RefreshResult {
        val creds = credentialStore.load() ?: return RefreshResult.MissingCredentials
        return try {
            val fresh = client.fetchAttendance(creds)
            snapshotStore.save(fresh)
            RefreshResult.Success(fresh, networkCallMade = true)
        } catch (e: MaitriError) {
            RefreshResult.Failure(e)
        }
    }

    /**
     * Test the supplied credentials without persisting them or affecting the
     * cached snapshot. Used by the Settings "Test login" button.
     */
    suspend fun testCredentials(creds: Credentials): Result<AttendanceSnapshot> = runCatching {
        client.fetchAttendance(creds)
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
                ).also { instance = it }
            }
        }
    }
}
