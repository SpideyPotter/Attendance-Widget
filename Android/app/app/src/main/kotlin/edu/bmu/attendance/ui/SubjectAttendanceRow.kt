package edu.bmu.attendance.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import edu.bmu.attendance.data.Subject
import edu.bmu.attendance.data.SubjectAliasStore

@Composable
fun SubjectAttendanceRow(
    subject: Subject,
    labelColumnWidth: Dp,
    colonColumnWidth: Dp,
    fontSize: TextUnit,
    color: Color,
    aliasStore: SubjectAliasStore,
    modifier: Modifier = Modifier,
) {
    val style = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = fontSize,
        color = color,
    )
    Row(modifier = modifier) {
        Text(
            text = SubjectRowLayout.label(subject, aliasStore),
            style = style,
            modifier = Modifier.width(labelColumnWidth),
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
        Text(
            text = ":",
            style = style,
            modifier = Modifier.width(colonColumnWidth),
            maxLines = 1,
        )
        Text(
            text = SubjectRowLayout.details(subject),
            style = style,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
