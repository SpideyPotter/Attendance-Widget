package edu.bmu.attendance.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import edu.bmu.attendance.ui.theme.AppThemeId

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ThemePicker(
    selected: AppThemeId,
    onSelect: (AppThemeId) -> Unit,
) {
    Column(
        modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AppThemeId.entries.forEach { theme ->
                FilterChip(
                    selected = selected == theme,
                    onClick = { onSelect(theme) },
                    label = { Text(theme.label) },
                )
            }
        }
        Text(
            text = themeDescription(selected),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun themeDescription(theme: AppThemeId): String = when (theme) {
    AppThemeId.DEFAULT -> "Follows your system theme and Material dynamic colors."
    AppThemeId.LIGHT -> "iPhone-style light: white and gray surfaces with black text."
    AppThemeId.DARK -> "iPhone-style dark: black surfaces with white text."
    AppThemeId.NEON -> "Blue background with the same layout and accent colors."
}
