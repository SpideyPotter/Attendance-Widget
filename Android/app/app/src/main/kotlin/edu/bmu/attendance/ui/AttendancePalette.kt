package edu.bmu.attendance.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object AttendancePalette {
    @Composable
    fun background(): Color = MaterialTheme.colorScheme.background

    @Composable
    fun sectionBackground(): Color = MaterialTheme.colorScheme.surfaceContainerHigh

    @Composable
    fun foreground(): Color = MaterialTheme.colorScheme.onSurface

    @Composable
    fun secondaryForeground(): Color = MaterialTheme.colorScheme.onSurfaceVariant

    val lowAttendance: Color = Color.Red

    @Composable
    fun rowColor(percentage: Double): Color =
        if (percentage < 70.0) lowAttendance else MaterialTheme.colorScheme.onSurface

    val statusOk: Color = Color(0xFF34C759)
    val statusFailed: Color = Color.Red
    val actionBlue: Color = Color(0xFF0A84FF)
}
