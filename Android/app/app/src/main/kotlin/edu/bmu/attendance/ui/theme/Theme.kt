package edu.bmu.attendance.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

@Composable
fun BmuAttendanceTheme(
    themeId: AppThemeId,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = AppThemeSpec.materialColorScheme(themeId),
        typography = AppTypography,
        content = content,
    )
}
