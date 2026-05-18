package edu.bmu.attendance.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountView(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    onCredentialsCleared: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val themeId by viewModel.themeId.collectAsState()
    val scope = rememberCoroutineScope()

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
