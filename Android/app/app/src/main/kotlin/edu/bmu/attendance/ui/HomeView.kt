package edu.bmu.attendance.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Abc
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import edu.bmu.attendance.data.SubjectAliasStore
import kotlinx.coroutines.launch

private enum class HomeDestination { Main, Labels, Account }

@Composable
fun HomeView(viewModel: SettingsViewModel) {
    var destination by rememberSaveable { mutableStateOf(HomeDestination.Main) }

    when (destination) {
        HomeDestination.Main -> HomeMainScreen(
            viewModel = viewModel,
            onOpenLabels = { destination = HomeDestination.Labels },
            onOpenAccount = { destination = HomeDestination.Account },
        )
        HomeDestination.Labels -> LabelsView(
            settingsViewModel = viewModel,
            onNavigateBack = { destination = HomeDestination.Main },
        )
        HomeDestination.Account -> AccountView(
            viewModel = viewModel,
            onNavigateBack = { destination = HomeDestination.Main },
            onCredentialsCleared = { destination = HomeDestination.Main },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeMainScreen(
    viewModel: SettingsViewModel,
    onOpenLabels: () -> Unit,
    onOpenAccount: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val aliasStore = remember { SubjectAliasStore.get(context) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "BMU Attendance",
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                actions = {
                    IconButton(onClick = onOpenLabels) {
                        Icon(Icons.Outlined.Abc, contentDescription = "Labels")
                    }
                    IconButton(onClick = onOpenAccount) {
                        Icon(Icons.Outlined.AccountCircle, contentDescription = "Account")
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
                .padding(padding)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item {
                val snapshot = state.lastSnapshot
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (snapshot != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = "%.2f%%".format(snapshot.overallPercentage),
                                    style = MaterialTheme.typography.headlineLarge.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                    ),
                                    color = AttendancePalette.rowColor(snapshot.overallPercentage),
                                )
                                FilledTonalIconButton(
                                    onClick = { scope.launch { viewModel.refreshFromHome() } },
                                    enabled = !state.isBusy,
                                ) {
                                    Icon(
                                        Icons.Outlined.Refresh,
                                        contentDescription = "Refresh",
                                    )
                                }
                            }
                            Text(
                                text = "${snapshot.totalPresent} / ${snapshot.totalDelivered} lectures · " +
                                    "${snapshot.termName} · ${RelativeTime.label(snapshot.fetchedAtMillis)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        } else {
                            Text(
                                text = "No attendance cached yet. Refresh to fetch your latest Maitri snapshot.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                }
            }

            state.lastSnapshot?.let { snapshot ->
                item { GroupedSectionHeader("Subjects") }
                val labelWidth = SubjectRowLayout.labelColumnWidth(
                    subjects = snapshot.subjects,
                    fontSize = 15.sp,
                    aliasStore = aliasStore,
                )
                val colonWidth = SubjectRowLayout.colonColumnWidth(15.sp)
                item {
                    GroupedSection {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            snapshot.subjects.forEachIndexed { index, subject ->
                                if (index > 0) {
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.outlineVariant,
                                    )
                                }
                                SubjectAttendanceRow(
                                    subject = subject,
                                    labelColumnWidth = labelWidth,
                                    colonColumnWidth = colonWidth,
                                    fontSize = 15.sp,
                                    color = AttendancePalette.rowColor(subject.percentage),
                                    aliasStore = aliasStore,
                                )
                            }
                        }
                    }
                }
            }

            item {
                Button(
                    onClick = { scope.launch { viewModel.refreshFromHome() } },
                    enabled = !state.isBusy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Icon(Icons.Outlined.Refresh, contentDescription = null)
                    Spacer(Modifier.padding(horizontal = 4.dp))
                    Text("Refresh attendance")
                }
            }

            item { GroupedSectionHeader("Status") }
            item {
                GroupedSection {
                    StatusText(
                        text = state.statusText,
                        status = state.status,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
