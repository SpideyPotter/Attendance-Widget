package edu.bmu.attendance

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.glance.appwidget.updateAll
import edu.bmu.attendance.data.ThemeStore
import edu.bmu.attendance.ui.AppRootView
import edu.bmu.attendance.ui.theme.AppThemeSpec
import edu.bmu.attendance.widget.AttendanceWidget
import edu.bmu.attendance.widget.CompactAttendanceWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val themeStore = ThemeStore.get(this)
            val themeId by themeStore.themeId.collectAsState()
            SystemBarStyle(themeId)
            RefreshHomeScreenWidgets()
            AppRootView()
        }
    }
}

@Composable
private fun SystemBarStyle(themeId: edu.bmu.attendance.ui.theme.AppThemeId) {
    val activity = LocalContext.current as? ComponentActivity ?: return
    val systemDark = isSystemInDarkTheme()
    val darkChrome = AppThemeSpec.usesDarkChrome(themeId, systemDark)
    LaunchedEffect(darkChrome) {
        WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
            isAppearanceLightStatusBars = !darkChrome
            isAppearanceLightNavigationBars = !darkChrome
        }
        activity.window.statusBarColor = android.graphics.Color.TRANSPARENT
        activity.window.navigationBarColor = android.graphics.Color.TRANSPARENT
    }
}

/** Push latest Glance layout to every placed widget after each app open / reinstall. */
@Composable
private fun RefreshHomeScreenWidgets() {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            AttendanceWidget().updateAll(context)
            CompactAttendanceWidget().updateAll(context)
        }
    }
}
