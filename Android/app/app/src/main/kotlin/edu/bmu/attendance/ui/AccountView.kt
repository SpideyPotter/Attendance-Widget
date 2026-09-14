package edu.bmu.attendance.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountView(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    onOpenLabels: () -> Unit,
    onCredentialsCleared: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val themeId by viewModel.themeId.collectAsState()
    val morningBriefingEnabled by viewModel.morningBriefingEnabled.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            viewModel.setMorningBriefingEnabled(true)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Account") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GroupedSection {
                Text(
                    text = "Update your Maitri login or remove saved credentials from this device.",
                    color = AttendancePalette.secondaryForeground(),
                )
            }

            GroupedSectionHeader("Appearance")
            GroupedSection {
                ThemePicker(
                    selected = themeId,
                    onSelect = viewModel::setAppTheme,
                )
            }

            GroupedSectionHeader("Notifications")
            GroupedSection {
                Text(
                    text = "Get a morning summary of today’s classes from your cached timetable.",
                    color = AttendancePalette.secondaryForeground(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Today’s classes")
                    Switch(
                        checked = morningBriefingEnabled,
                        onCheckedChange = { enabled ->
                            if (!enabled) {
                                viewModel.setMorningBriefingEnabled(false)
                                return@Switch
                            }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                val granted = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.POST_NOTIFICATIONS,
                                ) == PackageManager.PERMISSION_GRANTED
                                if (!granted) {
                                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    return@Switch
                                }
                            }
                            viewModel.setMorningBriefingEnabled(true)
                        },
                    )
                }
                Text(
                    text = "Sends around 7:00 AM with today’s schedule.",
                    color = AttendancePalette.secondaryForeground(),
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            GroupedSectionHeader("Course labels")
            GroupedSection {
                Text(
                    text = "Short names for courses in attendance and widgets. Stored only on this device.",
                    color = AttendancePalette.secondaryForeground(),
                )
                TextButton(onClick = onOpenLabels) {
                    Text("Edit labels")
                }
            }

            GroupedSectionHeader("Maitri credentials")
            GroupedSection {
                CredentialsFields(
                    username = state.username,
                    password = state.password,
                    showPassword = state.showPassword,
                    onUsernameChange = viewModel::updateUsername,
                    onPasswordChange = viewModel::updatePassword,
                    onToggleShowPassword = viewModel::toggleShowPassword,
                )
            }

            GroupedSection {
                TextButton(
                    onClick = { scope.launch { viewModel.saveAndRefresh() } },
                    enabled = state.username.contains('@') &&
                        state.password.isNotBlank() &&
                        !state.isBusy,
                ) {
                    Text("Save and refresh")
                }
                TextButton(
                    onClick = { scope.launch { viewModel.testCredentials() } },
                    enabled = state.username.isNotBlank() &&
                        state.password.isNotBlank() &&
                        !state.isBusy,
                ) {
                    Text("Test login")
                }
            }

            GroupedSectionHeader("Status")
            GroupedSection {
                StatusText(text = state.statusText, status = state.status)
            }

            GroupedSection {
                TextButton(
                    onClick = {
                        scope.launch {
                            viewModel.clearCredentials()
                            onCredentialsCleared()
                        }
                    },
                    enabled = !state.isBusy,
                ) {
                    Text("Forget credentials", color = AttendancePalette.statusFailed)
                }
            }
            Spacer(Modifier.padding(bottom = 24.dp))
        }
    }
}
