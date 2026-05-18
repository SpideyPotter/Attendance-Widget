package edu.bmu.attendance.data

import android.content.Context
import edu.bmu.attendance.ui.theme.AppThemeId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ThemeStore private constructor(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _themeId = MutableStateFlow(load())
    val themeId: StateFlow<AppThemeId> = _themeId.asStateFlow()

    fun current(): AppThemeId = _themeId.value

    fun setTheme(id: AppThemeId) {
        prefs.edit().putString(KEY_THEME, id.name).apply()
        _themeId.value = id
    }

    private fun load(): AppThemeId {
        val raw = prefs.getString(KEY_THEME, AppThemeId.DEFAULT.name) ?: return AppThemeId.DEFAULT
        return runCatching { AppThemeId.valueOf(raw) }.getOrDefault(AppThemeId.DEFAULT)
    }

    companion object {
        private const val PREFS_NAME = "bmu_attendance_theme"
        private const val KEY_THEME = "app_theme_id"

        @Volatile
        private var instance: ThemeStore? = null

        fun get(context: Context): ThemeStore {
            return instance ?: synchronized(this) {
                instance ?: ThemeStore(context.applicationContext).also { instance = it }
            }
        }
    }
}
