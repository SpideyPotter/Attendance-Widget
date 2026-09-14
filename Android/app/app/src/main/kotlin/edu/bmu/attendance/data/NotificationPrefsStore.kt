package edu.bmu.attendance.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** On-device notification preferences (no server). */
class NotificationPrefsStore private constructor(context: Context) {
    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _morningBriefingEnabled = MutableStateFlow(loadEnabled())
    val morningBriefingEnabled: StateFlow<Boolean> = _morningBriefingEnabled.asStateFlow()

    private val _morningBriefingHour = MutableStateFlow(loadHour())
    val morningBriefingHour: StateFlow<Int> = _morningBriefingHour.asStateFlow()

    private val _morningBriefingMinute = MutableStateFlow(loadMinute())
    val morningBriefingMinute: StateFlow<Int> = _morningBriefingMinute.asStateFlow()

    fun isMorningBriefingEnabled(): Boolean = _morningBriefingEnabled.value

    fun morningBriefingHour(): Int = _morningBriefingHour.value

    fun morningBriefingMinute(): Int = _morningBriefingMinute.value

    fun setMorningBriefingEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_MORNING_ENABLED, enabled).apply()
        _morningBriefingEnabled.value = enabled
    }

    fun setMorningBriefingTime(hour: Int, minute: Int) {
        val h = hour.coerceIn(0, 23)
        val m = minute.coerceIn(0, 59)
        prefs.edit()
            .putInt(KEY_MORNING_HOUR, h)
            .putInt(KEY_MORNING_MINUTE, m)
            .apply()
        _morningBriefingHour.value = h
        _morningBriefingMinute.value = m
    }

    fun clear() {
        prefs.edit().clear().apply()
        _morningBriefingEnabled.value = false
        _morningBriefingHour.value = DEFAULT_HOUR
        _morningBriefingMinute.value = DEFAULT_MINUTE
    }

    private fun loadEnabled(): Boolean = prefs.getBoolean(KEY_MORNING_ENABLED, false)

    private fun loadHour(): Int = prefs.getInt(KEY_MORNING_HOUR, DEFAULT_HOUR).coerceIn(0, 23)

    private fun loadMinute(): Int =
        prefs.getInt(KEY_MORNING_MINUTE, DEFAULT_MINUTE).coerceIn(0, 59)

    companion object {
        const val DEFAULT_HOUR = 7
        const val DEFAULT_MINUTE = 0

        private const val PREFS_NAME = "bmu_notifications"
        private const val KEY_MORNING_ENABLED = "morning_briefing_enabled"
        private const val KEY_MORNING_HOUR = "morning_briefing_hour"
        private const val KEY_MORNING_MINUTE = "morning_briefing_minute"

        @Volatile
        private var instance: NotificationPrefsStore? = null

        fun get(context: Context): NotificationPrefsStore {
            return instance ?: synchronized(this) {
                instance ?: NotificationPrefsStore(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }
}
