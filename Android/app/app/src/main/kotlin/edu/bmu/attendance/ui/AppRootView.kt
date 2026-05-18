package edu.bmu.attendance.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import edu.bmu.attendance.ui.theme.BmuAttendanceTheme

@Composable
fun AppRootView() {
    val context = LocalContext.current
    val viewModel = remember { SettingsViewModel(context) }
    val state by viewModel.state.collectAsState()
    val themeId by viewModel.themeId.collectAsState()

    BmuAttendanceTheme(themeId = themeId) {
        if (state.hasSavedCredentials) {
            HomeView(viewModel)
        } else {
            LoginView(viewModel)
        }
    }
}
