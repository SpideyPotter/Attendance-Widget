package edu.bmu.attendance.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Percent
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.activity.compose.BackHandler
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import edu.bmu.attendance.data.SubjectAliasStore
import edu.bmu.attendance.data.TimetableQueries
import edu.bmu.attendance.data.TimetableSession
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

private enum class RootDestination { Today, Weekly, Attendance }
private enum class OverlayDestination { None, Labels, Account }

@Composable
fun HomeView(viewModel: SettingsViewModel) {
    var tab by rememberSaveable { mutableStateOf(RootDestination.Today) }
    var overlay by rememberSaveable { mutableStateOf(OverlayDestination.None) }

    BackHandler(enabled = overlay != OverlayDestination.None) {
        overlay = when (overlay) {
            OverlayDestination.Labels -> OverlayDestination.Account
            OverlayDestination.Account -> OverlayDestination.None
            OverlayDestination.None -> OverlayDestination.None
        }
    }

    when (overlay) {
        OverlayDestination.Labels -> LabelsView(
            settingsViewModel = viewModel,
            onNavigateBack = { overlay = OverlayDestination.Account },
        )
        OverlayDestination.Account -> AccountView(
            viewModel = viewModel,
            onNavigateBack = { overlay = OverlayDestination.None },
            onOpenLabels = { overlay = OverlayDestination.Labels },
            onCredentialsCleared = { overlay = OverlayDestination.None },
        )
        OverlayDestination.None -> MainScaffold(
            viewModel = viewModel,
            tab = tab,
            onTabChange = { tab = it },
            onOpenAccount = { overlay = OverlayDestination.Account },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScaffold(
    viewModel: SettingsViewModel,
    tab: RootDestination,
    onTabChange: (RootDestination) -> Unit,
    onOpenAccount: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val scope = rememberCoroutineScope()
    var didAutoRefresh by rememberSaveable { mutableStateOf(false) }
    val title = when (tab) {
        RootDestination.Today -> "Today"
        RootDestination.Weekly -> "Weekly"
        RootDestination.Attendance -> "Attendance"
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            if (tab != RootDestination.Weekly) {
                TopAppBar(
                    title = {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge,
                        )
                    },
                    actions = {
                        FilledTonalIconButton(
                            onClick = { scope.launch { viewModel.refreshFromHome() } },
                            enabled = !state.isBusy,
                        ) {
                            Icon(Icons.Outlined.Refresh, contentDescription = "Refresh")
                        }
                        IconButton(onClick = onOpenAccount) {
                            Icon(Icons.Outlined.AccountCircle, contentDescription = "Account")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
            }
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == RootDestination.Today,
                    onClick = { onTabChange(RootDestination.Today) },
                    icon = { Icon(Icons.Outlined.CalendarToday, contentDescription = null) },
                    label = { Text("Today") },
                )
                NavigationBarItem(
                    selected = tab == RootDestination.Weekly,
                    onClick = { onTabChange(RootDestination.Weekly) },
                    icon = { Icon(Icons.Outlined.CalendarMonth, contentDescription = null) },
                    label = { Text("Weekly") },
                )
                NavigationBarItem(
                    selected = tab == RootDestination.Attendance,
                    onClick = { onTabChange(RootDestination.Attendance) },
                    icon = { Icon(Icons.Outlined.Percent, contentDescription = null) },
                    label = { Text("Attendance") },
                )
            }
        },
    ) { padding ->
        // First open after upgrade: pull timetable if attendance cache exists without it.
        LaunchedEffect(state.hasSavedCredentials, state.lastTimetable) {
            if (state.hasSavedCredentials &&
                state.lastTimetable == null &&
                !state.isBusy &&
                !didAutoRefresh
            ) {
                didAutoRefresh = true
                viewModel.refreshFromHome()
            }
        }

        when (tab) {
            RootDestination.Today -> TodayScreen(
                viewModel = viewModel,
                modifier = Modifier.padding(padding),
            )
            RootDestination.Weekly -> WeeklyTimetableContent(
                snapshot = state.lastTimetable,
                isBusy = state.isBusy,
                onRefresh = { scope.launch { viewModel.refreshFromHome() } },
                onOpenAccount = onOpenAccount,
                modifier = Modifier.padding(padding),
            )
            RootDestination.Attendance -> AttendanceScreen(
                viewModel = viewModel,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun TodayScreen(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val today = remember { LocalDate.now() }
    val todaySessions = remember(state.lastTimetable, today) {
        state.lastTimetable?.let { TimetableQueries.sessionsForDate(it, today) }.orEmpty()
    }
    val next = remember(state.lastTimetable) {
        state.lastTimetable?.let { TimetableQueries.nextSession(it) }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item {
            TimetableMetaCard(
                title = today.format(DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.US)),
                subtitle = state.lastTimetable?.let {
                    "${it.termName} · ${RelativeTime.label(it.fetchedAtMillis)}"
                } ?: "Refresh to load today’s classes from Maitri.",
            )
        }

        if (next != null) {
            item { GroupedSectionHeader("Next") }
            item {
                GroupedSection {
                    NextClassRow(session = next)
                }
            }
        }

        item { GroupedSectionHeader("Classes") }
        if (state.lastTimetable == null) {
            item {
                GroupedSection {
                    Text(
                        text = "No timetable cached yet. Pull refresh from the top bar.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        } else if (todaySessions.isEmpty()) {
            item {
                GroupedSection {
                    Text(
                        text = "No classes scheduled today.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        } else {
            item {
                GroupedSection {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        todaySessions.forEachIndexed { index, session ->
                            if (index > 0) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                            SessionRow(session)
                        }
                    }
                }
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

@Composable
private fun AttendanceScreen(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val aliasStore = remember { SubjectAliasStore.get(context) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item {
            val snapshot = state.lastSnapshot
            GroupedSection {
                if (snapshot != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "%.2f%%".format(snapshot.overallPercentage),
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                            ),
                            color = AttendancePalette.rowColor(snapshot.overallPercentage),
                        )
                        Text(
                            text = "${snapshot.totalPresent} / ${snapshot.totalDelivered} lectures · " +
                                "${snapshot.termName} · ${RelativeTime.label(snapshot.fetchedAtMillis)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    Text(
                        text = "No attendance cached yet. Refresh to fetch your latest Maitri snapshot.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
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
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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

@Composable
private fun TimetableMetaCard(
    title: String,
    subtitle: String,
) {
    GroupedSection {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NextClassRow(session: TimetableSession) {
    val whenLabel = TimetableQueries.upcomingOrOngoingLabel(session)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = session.subjectName,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "${session.startTime} – ${session.endTime} · $whenLabel",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        if (session.facultyName.isNotBlank()) {
            Text(
                text = session.facultyName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SessionRow(session: TimetableSession) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(0.34f)) {
            Text(
                text = session.startTime,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = session.endTime,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(
            modifier = Modifier.weight(0.66f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = session.subjectName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
            if (session.facultyName.isNotBlank()) {
                Text(
                    text = session.facultyName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val room = session.classRoom.trim()
            if (room.isNotEmpty() && room != "-") {
                Text(
                    text = room,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
