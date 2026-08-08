package edu.bmu.attendance.data

import android.content.Context
import org.json.JSONObject
import java.io.File

/**
 * Local classroom overrides. Maitri's timetable JSON leaves `classRoom` as `-`
 * for BMU, so users can set venues on-device.
 *
 * Resolution order (most specific wins):
 * 1. [VenueScope.Slot] — this exact session
 * 2. [VenueScope.WeeklySlot] — same course + weekday + start time every week
 * 3. [VenueScope.Course] — every session of the course
 * 4. Portal `classRoom` when it isn't blank / `-`
 */
class VenueStore private constructor(
    private val file: File,
) {
    enum class Scope { Course, WeeklySlot, Slot }

    data class Override(
        val scope: Scope,
        val key: String,
        val venue: String,
    )

    fun resolve(session: TimetableSession): String {
        val payload = load()
        payload.slots[slotKey(session)]?.let { return it }
        payload.weeklySlots[weeklyKey(session)]?.let { return it }
        payload.courses[courseKey(session)]?.let { return it }
        val portal = session.classRoom.trim()
        if (portal.isNotEmpty() && portal != "-") return portal
        return TBA
    }

    fun overrideFor(session: TimetableSession): Override? {
        val payload = load()
        payload.slots[slotKey(session)]?.let {
            return Override(Scope.Slot, slotKey(session), it)
        }
        payload.weeklySlots[weeklyKey(session)]?.let {
            return Override(Scope.WeeklySlot, weeklyKey(session), it)
        }
        payload.courses[courseKey(session)]?.let {
            return Override(Scope.Course, courseKey(session), it)
        }
        return null
    }

    fun setVenue(session: TimetableSession, scope: Scope, venue: String) {
        val trimmed = venue.trim()
        val payload = load()
        if (trimmed.isEmpty() || trimmed == TBA) {
            clearVenue(session, scope)
            return
        }
        when (scope) {
            Scope.Course -> payload.courses[courseKey(session)] = trimmed
            Scope.WeeklySlot -> payload.weeklySlots[weeklyKey(session)] = trimmed
            Scope.Slot -> payload.slots[slotKey(session)] = trimmed
        }
        save(payload)
    }

    fun clearVenue(session: TimetableSession, scope: Scope) {
        val payload = load()
        val removed = when (scope) {
            Scope.Course -> payload.courses.remove(courseKey(session)) != null
            Scope.WeeklySlot -> payload.weeklySlots.remove(weeklyKey(session)) != null
            Scope.Slot -> payload.slots.remove(slotKey(session)) != null
        }
        if (removed) save(payload)
    }

    fun clearAll() {
        save(Payload())
    }

    private fun load(): Payload {
        if (!file.exists()) return Payload()
        return runCatching {
            val json = JSONObject(file.readText())
            Payload(
                courses = readMap(json.optJSONObject("courses")),
                weeklySlots = readMap(json.optJSONObject("weeklySlots")),
                slots = readMap(json.optJSONObject("slots")),
            )
        }.getOrDefault(Payload())
    }

    private fun save(payload: Payload) {
        file.parentFile?.mkdirs()
        val json = JSONObject()
            .put("courses", writeMap(payload.courses))
            .put("weeklySlots", writeMap(payload.weeklySlots))
            .put("slots", writeMap(payload.slots))
        file.writeText(json.toString())
    }

    private fun readMap(obj: JSONObject?): MutableMap<String, String> {
        val out = mutableMapOf<String, String>()
        if (obj == null) return out
        obj.keys().forEach { key ->
            obj.optString(key, "").trim().takeIf { it.isNotEmpty() }?.let { out[key] = it }
        }
        return out
    }

    private fun writeMap(map: Map<String, String>): JSONObject =
        JSONObject().apply { map.forEach { (k, v) -> put(k, v) } }

    private data class Payload(
        val courses: MutableMap<String, String> = mutableMapOf(),
        val weeklySlots: MutableMap<String, String> = mutableMapOf(),
        val slots: MutableMap<String, String> = mutableMapOf(),
    )

    companion object {
        const val TBA = "TBA"
        private const val FILE_NAME = "venue_overrides.json"

        @Volatile private var instance: VenueStore? = null

        fun get(context: Context): VenueStore {
            return instance ?: synchronized(this) {
                instance ?: VenueStore(
                    File(context.applicationContext.filesDir, FILE_NAME),
                ).also { instance = it }
            }
        }

        fun courseKey(session: TimetableSession): String =
            session.subjectName.trim().lowercase()

        fun weeklyKey(session: TimetableSession): String =
            listOf(
                courseKey(session),
                session.lectureDay.trim().lowercase(),
                session.startTime.trim().lowercase(),
            ).joinToString("|")

        fun slotKey(session: TimetableSession): String = session.id
    }
}
