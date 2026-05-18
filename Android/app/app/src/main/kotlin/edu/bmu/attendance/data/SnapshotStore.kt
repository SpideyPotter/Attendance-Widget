package edu.bmu.attendance.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persists the most recent [AttendanceSnapshot] as JSON in plain
 * SharedPreferences. The data is non-secret (it's the same percentages
 * the widget shows on the home screen), so encryption is unnecessary —
 * but we still exclude the file from cloud backup so it doesn't drift
 * across devices.
 */
class SnapshotStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        FILE_NAME, Context.MODE_PRIVATE,
    )

    fun load(): AttendanceSnapshot? {
        val raw = prefs.getString(KEY_JSON, null) ?: return null
        return runCatching { fromJson(raw) }.getOrNull()
    }

    fun save(snapshot: AttendanceSnapshot) {
        prefs.edit().putString(KEY_JSON, toJson(snapshot).toString()).apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    // ── JSON (de)serialization ──────────────────────────────────────────────

    private fun toJson(s: AttendanceSnapshot): JSONObject {
        val subjects = JSONArray().apply {
            s.subjects.forEach { sub ->
                put(
                    JSONObject()
                        .put("code", sub.code)
                        .put("name", sub.name)
                        .put("present", sub.present)
                        .put("absent", sub.absent)
                        .put("afterCapping", sub.afterCapping),
                )
            }
        }
        return JSONObject()
            .put("termName", s.termName)
            .put("termSemesterId", s.termSemesterId)
            .put("subjects", subjects)
            .put("fetchedAtMillis", s.fetchedAtMillis)
    }

    private fun fromJson(raw: String): AttendanceSnapshot {
        val o = JSONObject(raw)
        val subsArr = o.getJSONArray("subjects")
        val subjects = List(subsArr.length()) { i ->
            val so = subsArr.getJSONObject(i)
            Subject(
                code = so.optString("code", "?"),
                name = so.optString("name", "?"),
                present = so.optInt("present", 0),
                absent = so.optInt("absent", 0),
                afterCapping = so.optDouble("afterCapping", 0.0),
            )
        }
        return AttendanceSnapshot(
            termName = o.optString("termName", "?"),
            termSemesterId = o.optInt("termSemesterId", 0),
            subjects = subjects,
            fetchedAtMillis = o.optLong("fetchedAtMillis", 0L),
        )
    }

    companion object {
        private const val FILE_NAME = "bmu_snapshot"
        private const val KEY_JSON = "snapshot"
    }
}
