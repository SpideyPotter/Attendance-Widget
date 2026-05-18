package edu.bmu.attendance.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LabelsView(
    settingsViewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
) {
    val context = LocalContext.current
    val viewModel = remember { LabelsViewModel(context) }
    val settingsState by settingsViewModel.state.collectAsState()
    val rows by viewModel.rows.collectAsState()
    val statusText by viewModel.statusText.collectAsState()
    val statusIsError by viewModel.statusIsError.collectAsState()

    LaunchedEffect(settingsState.lastSnapshot) {
        viewModel.load(settingsState.lastSnapshot)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Labels") },
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            item {
                GroupedSection {
                    Text(
                        text = "Choose short labels for your courses. These apply in the app and widget on this device only.",
                        color = AttendancePalette.secondaryForeground(),
                    )
                }
            }

            if (rows.isEmpty()) {
                item {
                    GroupedSection {
                        Text(
                            text = "Refresh attendance first to load subjects.",
                            color = AttendancePalette.secondaryForeground(),
                        )
                    }
                }
            } else {
                item { GroupedSectionHeader("Courses") }
                items(rows, key = { it.id }) { row ->
                    GroupedSection {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = row.subject.name,
                                fontSize = 15.sp,
                                color = AttendancePalette.secondaryForeground(),
                            )
                            Text(
                                text = "Default: ${row.defaultLabel}",
                                fontSize = 12.sp,
                                color = AttendancePalette.secondaryForeground(),
                            )
                            Text(
                                text = "Current: ${row.currentLabel}",
                                fontSize = 12.sp,
                            )
                            OutlinedTextField(
                                value = row.draft,
                                onValueChange = { viewModel.updateDraft(row.id, it) },
                                label = { Text("Custom label") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.Characters,
                                    autoCorrect = false,
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            TextButton(onClick = { viewModel.save(row.id) }) {
                                Text("Save")
                            }
                            TextButton(onClick = { viewModel.reset(row.id) }) {
                                Text("Reset to default")
                            }
                        }
                    }
                }
                item {
                    GroupedSection {
                        TextButton(onClick = viewModel::resetAll) {
                            Text("Reset all custom labels", color = AttendancePalette.statusFailed)
                        }
                    }
                }
            }

            item {
                GroupedSection {
                    Text(
                        text = statusText,
                        color = if (statusIsError) {
                            AttendancePalette.statusFailed
                        } else {
                            AttendancePalette.secondaryForeground()
                        },
                    )
                    Text(
                        text = "Clearing your Maitri login does not remove custom labels.",
                        fontSize = 13.sp,
                        color = AttendancePalette.secondaryForeground(),
                    )
                }
            }
            item { Spacer(Modifier.padding(bottom = 24.dp)) }
        }
    }
}
