package edu.bmu.attendance.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Persists the latest [TimetableSnapshot] for Today / Weekly screens. */
class TimetableStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        FILE_NAME,
        Context.MODE_PRIVATE,
    )

    fun load(): TimetableSnapshot? {
        val raw = prefs.getString(KEY_JSON, null) ?: return null
        return runCatching { fromJson(raw) }.getOrNull()
    }

    fun save(snapshot: TimetableSnapshot) {
        prefs.edit().putString(KEY_JSON, toJson(snapshot).toString()).apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun toJson(s: TimetableSnapshot): JSONObject {
        val sessions = JSONArray().apply {
            s.sessions.forEach { session ->
                put(
                    JSONObject()
                        .put("lectureDate", session.lectureDate)
                        .put("dateIso", session.dateIso)
                        .put("lectureDay", session.lectureDay)
                        .put("startTime", session.startTime)
                        .put("endTime", session.endTime)
                        .put("sessionNo", session.sessionNo)
                        .put("subjectName", session.subjectName)
                        .put("topic", session.topic)
                        .put("classRoom", session.classRoom)
                        .put("facultyName", session.facultyName)
                        .put("description", session.description),
                )
            }
        }
        return JSONObject()
            .put("termName", s.termName)
            .put("termSemesterId", s.termSemesterId)
            .put("startDate", s.startDate)
            .put("endDate", s.endDate)
            .put("sessions", sessions)
            .put("fetchedAtMillis", s.fetchedAtMillis)
    }

    private fun fromJson(raw: String): TimetableSnapshot {
        val o = JSONObject(raw)
        val arr = o.getJSONArray("sessions")
        val sessions = List(arr.length()) { i ->
            val so = arr.getJSONObject(i)
            TimetableSession(
                lectureDate = so.optString("lectureDate", ""),
                dateIso = so.optString("dateIso", ""),
                lectureDay = so.optString("lectureDay", ""),
                startTime = so.optString("startTime", ""),
                endTime = so.optString("endTime", ""),
                sessionNo = so.optString("sessionNo", ""),
                subjectName = so.optString("subjectName", ""),
                topic = so.optString("topic", "-"),
                classRoom = so.optString("classRoom", "-"),
                facultyName = so.optString("facultyName", ""),
                description = so.optString("description", "-"),
            )
        }
        return TimetableSnapshot(
            termName = o.optString("termName", "?"),
            termSemesterId = o.optInt("termSemesterId", 0),
            startDate = o.optString("startDate", ""),
            endDate = o.optString("endDate", ""),
            sessions = sessions,
            fetchedAtMillis = o.optLong("fetchedAtMillis", 0L),
        )
    }

    companion object {
        private const val FILE_NAME = "bmu_timetable"
        private const val KEY_JSON = "timetable"
    }
}
