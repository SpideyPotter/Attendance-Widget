package edu.bmu.attendance.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
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
fun LoginView(viewModel: SettingsViewModel) {
    val state by viewModel.state.collectAsState()
    val scope = rememberCoroutineScope()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Set up") },
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
                    text = "Sign in with your full Maitri email to fetch attendance for the home-screen widget.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                Button(
                    onClick = { scope.launch { viewModel.saveAndRefresh() } },
                    enabled = state.username.contains('@') &&
                        state.password.isNotBlank() &&
                        !state.isBusy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Save and continue")
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
            Spacer(Modifier.padding(bottom = 24.dp))
        }
    }
}
