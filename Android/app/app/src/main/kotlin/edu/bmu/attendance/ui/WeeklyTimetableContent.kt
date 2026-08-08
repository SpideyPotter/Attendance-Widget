package edu.bmu.attendance.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import edu.bmu.attendance.data.TimetableQueries
import edu.bmu.attendance.data.TimetableSession
import edu.bmu.attendance.data.TimetableSnapshot
import edu.bmu.attendance.data.VenueStore
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun WeeklyTimetableContent(
    snapshot: TimetableSnapshot?,
    isBusy: Boolean,
    onRefresh: () -> Unit,
    onOpenAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val venueStore = remember { VenueStore.get(context) }
    var venueRevision by remember { mutableIntStateOf(0) }
    var editingSession by remember { mutableStateOf<TimetableSession?>(null) }

    val weekDates = remember(snapshot) { snapshot?.let { TimetableQueries.weekDates(it) }.orEmpty() }
    val today = remember { LocalDate.now() }
    var selectedIso by rememberSaveable(snapshot?.fetchedAtMillis) {
        val initial = when {
            snapshot == null -> today.toString()
            weekDates.any { it == today } -> today.toString()
            else -> weekDates.firstOrNull { d ->
                snapshot.sessions.any { it.dateIso == d.toString() }
            }?.toString() ?: weekDates.firstOrNull()?.toString() ?: today.toString()
        }
        mutableStateOf(initial)
    }

    LaunchedEffect(weekDates) {
        if (weekDates.isNotEmpty() && weekDates.none { it.toString() == selectedIso }) {
            selectedIso = weekDates.first().toString()
        }
    }

    val daySessions = remember(snapshot, selectedIso) {
        snapshot?.sessions?.filter { it.dateIso == selectedIso }.orEmpty()
    }
    val nextId = remember(snapshot) { snapshot?.let { TimetableQueries.nextSession(it)?.id } }

    editingSession?.let { session ->
        val current = remember(session, venueRevision) { venueStore.resolve(session) }
        val existing = remember(session, venueRevision) { venueStore.overrideFor(session) }
        VenueEditDialog(
            session = session,
            initialVenue = current,
            initialScope = existing?.scope ?: VenueStore.Scope.Course,
            onDismiss = { editingSession = null },
            onSave = { venue, scope ->
                venueStore.setVenue(session, scope, venue)
                venueRevision += 1
                editingSession = null
            },
            onClear = { scope ->
                venueStore.clearVenue(session, scope)
                venueRevision += 1
                editingSession = null
            },
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            WeeklyHeader(
                snapshot = snapshot,
                isBusy = isBusy,
                onRefresh = onRefresh,
                onOpenAccount = onOpenAccount,
            )
        }

        if (weekDates.isNotEmpty()) {
            item {
                WeekDayStrip(
                    dates = weekDates,
                    selectedIso = selectedIso,
                    sessions = snapshot?.sessions.orEmpty(),
                    onSelect = { selectedIso = it.toString() },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }

        when {
            snapshot == null -> {
                item {
                    EmptyTimetableHint(
                        text = "Refresh to load your weekly timetable from Maitri.",
                    )
                }
            }
            daySessions.isEmpty() -> {
                item {
                    EmptyTimetableHint(text = "No classes on this day.")
                }
            }
            else -> {
                items(daySessions, key = { it.id }) { session ->
                    // venueRevision forces recomposition after edits.
                    val venue = remember(session, venueRevision) { venueStore.resolve(session) }
                    TimetableSessionCard(
                        session = session,
                        venue = venue,
                        isNext = session.id == nextId,
                        accent = accentForSubject(session.subjectName),
                        onVenueClick = { editingSession = session },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun WeeklyHeader(
    snapshot: TimetableSnapshot?,
    isBusy: Boolean,
    onRefresh: () -> Unit,
    onOpenAccount: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = snapshot?.let { TimetableQueries.weekHeaderLabel(it) } ?: "THIS WEEK",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "My Timetable",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
            )
            if (snapshot != null) {
                Text(
                    text = "${snapshot.termName} · ${snapshot.sessions.size} classes · " +
                        RelativeTime.label(snapshot.fetchedAtMillis),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconButton(onClick = onRefresh, enabled = !isBusy) {
                Icon(Icons.Outlined.Refresh, contentDescription = "Refresh")
            }
            IconButton(onClick = onOpenAccount) {
                Icon(Icons.Outlined.AccountCircle, contentDescription = "Account")
            }
        }
    }
}

@Composable
private fun WeekDayStrip(
    dates: List<LocalDate>,
    selectedIso: String,
    sessions: List<TimetableSession>,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dayFmt = remember { DateTimeFormatter.ofPattern("EEE", Locale.US) }
    val datesWithClasses = remember(sessions) { sessions.map { it.dateIso }.toSet() }

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(dates, key = { it.toString() }) { date ->
            val iso = date.toString()
            val selected = iso == selectedIso
            val hasClasses = iso in datesWithClasses
            val primary = MaterialTheme.colorScheme.primary
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onSelect(date) }
                    .then(
                        if (selected) {
                            Modifier.border(1.5.dp, primary, RoundedCornerShape(16.dp))
                        } else {
                            Modifier
                        },
                    )
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = date.format(dayFmt).uppercase(Locale.US),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) {
                        primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                Text(
                    text = "%02d".format(date.dayOfMonth),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = if (selected) primary else MaterialTheme.colorScheme.onSurface,
                )
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                selected -> primary
                                hasClasses -> MaterialTheme.colorScheme.outline
                                else -> Color.Transparent
                            },
                        ),
                )
            }
        }
    }
}

@Composable
fun TimetableSessionCard(
    session: TimetableSession,
    venue: String,
    isNext: Boolean,
    accent: Color,
    onVenueClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val kind = TimetableQueries.sessionKind(session)
    val duration = TimetableQueries.durationLabel(session)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
        ) {
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .fillMaxHeight()
                    .background(accent),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = session.subjectName,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (session.facultyName.isNotBlank()) {
                            Text(
                                text = session.facultyName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    KindChip(label = kind, accent = accent)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            Icons.Outlined.AccessTime,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = TimetableQueries.formatClockRange(session),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (duration != null) {
                            DurationChip(duration)
                        }
                    }
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onVenueClick)
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            Icons.Outlined.Place,
                            contentDescription = "Edit venue",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = venue,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                if (isNext) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                        )
                        Text(
                            text = "Next class",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun KindChip(label: String, accent: Color) {
    Surface(
        shape = RoundedCornerShape(50),
        color = accent.copy(alpha = 0.16f),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = accent,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun DurationChip(label: String) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptyTimetableHint(text: String) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(20.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun accentForSubject(name: String): Color {
    val scheme = MaterialTheme.colorScheme
    val palette = listOf(
        scheme.primary,
        scheme.secondary,
        scheme.tertiary,
        scheme.error,
        scheme.primary.copy(alpha = 0.75f),
        scheme.secondary.copy(alpha = 0.85f),
    )
    val index = (name.hashCode().and(0x7fffffff)) % palette.size
    return palette[index]
}
