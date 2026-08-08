package edu.bmu.attendance.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import edu.bmu.attendance.data.TimetableSession
import edu.bmu.attendance.data.VenueStore

@Composable
fun VenueEditDialog(
    session: TimetableSession,
    initialVenue: String,
    initialScope: VenueStore.Scope,
    onDismiss: () -> Unit,
    onSave: (venue: String, scope: VenueStore.Scope) -> Unit,
    onClear: (scope: VenueStore.Scope) -> Unit,
) {
    var draft by remember {
        mutableStateOf(initialVenue.takeUnless { it == VenueStore.TBA }.orEmpty())
    }
    var scope by remember { mutableStateOf(initialScope) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set venue") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "${session.subjectName} · ${session.lectureDay} · ${session.startTime}",
                    fontWeight = FontWeight.Medium,
                )
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    label = { Text("Classroom / Lab") },
                    placeholder = { Text("e.g. A-201") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                )
                ScopeOption(
                    selected = scope == VenueStore.Scope.Course,
                    title = "All ${session.subjectName} classes",
                    subtitle = "Every place this course appears",
                    onClick = { scope = VenueStore.Scope.Course },
                )
                ScopeOption(
                    selected = scope == VenueStore.Scope.WeeklySlot,
                    title = "This slot every week",
                    subtitle = "${session.lectureDay}s at ${session.startTime}",
                    onClick = { scope = VenueStore.Scope.WeeklySlot },
                )
                ScopeOption(
                    selected = scope == VenueStore.Scope.Slot,
                    title = "Only this class",
                    subtitle = "${session.lectureDate.trim()} · ${session.startTime}",
                    onClick = { scope = VenueStore.Scope.Slot },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(draft, scope) },
                enabled = draft.isNotBlank(),
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onClear(scope) }) {
                    Text("Clear")
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        },
    )
}

@Composable
private fun ScopeOption(
    selected: Boolean,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(text = title, fontWeight = FontWeight.SemiBold)
            Text(text = subtitle)
        }
    }
}
