package edu.bmu.attendance.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import edu.bmu.attendance.data.Subject
import edu.bmu.attendance.data.SubjectAliasStore

object SubjectRowLayout {
    private const val COLON_COLUMN_WIDTH_FACTOR = 0.45f
    private const val MONOSPACED_CHARACTER_WIDTH_FACTOR = 0.62f

    fun label(subject: Subject, aliasStore: SubjectAliasStore): String =
        aliasStore.displayLabel(subject)

    fun details(subject: Subject): String =
        "%6.2f%% (%2d/%2d)".format(subject.percentage, subject.present, subject.total)

    fun labelColumnWidth(
        subjects: List<Subject>,
        fontSize: TextUnit,
        aliasStore: SubjectAliasStore,
    ): Dp {
        val maxLength = subjects.maxOfOrNull { label(it, aliasStore).length } ?: 1
        return (maxLength * fontSize.value * MONOSPACED_CHARACTER_WIDTH_FACTOR).dp
    }

    fun colonColumnWidth(fontSize: TextUnit): Dp =
        (fontSize.value * COLON_COLUMN_WIDTH_FACTOR).dp
}
