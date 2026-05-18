package edu.bmu.attendance.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.components.CircleIconButton
import androidx.glance.appwidget.components.Scaffold
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import edu.bmu.attendance.MainActivity
import edu.bmu.attendance.R
import edu.bmu.attendance.data.AttendanceRepository
import edu.bmu.attendance.data.AttendanceSnapshot
import edu.bmu.attendance.data.CredentialStore
import edu.bmu.attendance.data.Subject
import edu.bmu.attendance.data.SubjectAliasStore
import edu.bmu.attendance.data.ThemeStore
import edu.bmu.attendance.ui.RelativeTime
import edu.bmu.attendance.ui.SubjectRowLayout
import edu.bmu.attendance.ui.theme.AppThemeId
import edu.bmu.attendance.ui.theme.AppThemeSpec

open class AttendanceWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(SMALL_SIZE, MEDIUM_SIZE, EXPANDED_SIZE),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = AttendanceRepository.get(context).cachedSnapshot
        val hasCredentials = CredentialStore(context).hasCredentials()
        val aliasStore = SubjectAliasStore.get(context)

        val themeId = ThemeStore.get(context).current()
        val glanceColors = AppThemeSpec.glanceColorProviders(themeId)

        provideContent {
            if (glanceColors != null) {
                GlanceTheme(colors = glanceColors) {
                    WidgetContent(
                        snapshot = snapshot,
                        hasCredentials = hasCredentials,
                        aliasStore = aliasStore,
                    )
                }
            } else {
                GlanceTheme {
                    WidgetContent(
                        snapshot = snapshot,
                        hasCredentials = hasCredentials,
                        aliasStore = aliasStore,
                    )
                }
            }
        }
    }
}

class CompactAttendanceWidget : AttendanceWidget()

@Composable
private fun WidgetContent(
    snapshot: AttendanceSnapshot?,
    hasCredentials: Boolean,
    aliasStore: SubjectAliasStore,
) {
    val size = LocalSize.current
    val isSmall = size.width <= 200.dp && size.height <= 200.dp
    val colors = GlanceTheme.colors
    val context = LocalContext.current
    val themeId = ThemeStore.get(context).current()
    val isNeon = themeId == AppThemeId.NEON
    val showRefresh = hasCredentials && snapshot != null
    val openApp = actionStartActivity<MainActivity>()

    Scaffold(
        backgroundColor = colors.widgetBackground,
        horizontalPadding = if (isSmall) 8.dp else 12.dp,
    ) {
        when {
            !hasCredentials -> EmptyState(
                message = context.getString(R.string.widget_no_credentials),
                action = openApp,
            )
            snapshot == null -> EmptyState(
                message = context.getString(R.string.widget_open_app),
                action = openApp,
            )
            isSmall -> PopulatedContent(
                snapshot = snapshot,
                aliasStore = aliasStore,
                showRefresh = showRefresh,
                openApp = openApp,
                themeId = themeId,
                subjectLimit = SMALL_SUBJECT_LIMIT,
                overallFontSize = SMALL_OVERALL_FONT,
                rowFontSize = SMALL_ROW_FONT,
                showMetadata = false,
                sortByLowestAttendance = true,
                headerPaddingH = 8.dp,
                headerPaddingV = 8.dp,
                sectionSpacing = 6.dp,
                rowPaddingV = 1.dp,
            )
            isNeon -> NeonPopulatedContent(
                snapshot = snapshot,
                showRefresh = showRefresh,
                openApp = openApp,
                themeId = themeId,
            )
            else -> PopulatedContent(
                snapshot = snapshot,
                aliasStore = aliasStore,
                showRefresh = showRefresh,
                openApp = openApp,
                themeId = themeId,
                subjectLimit = MAX_VISIBLE_SUBJECTS,
                overallFontSize = 36.sp,
                rowFontSize = 14.sp,
                showMetadata = true,
                sortByLowestAttendance = false,
                headerPaddingH = 12.dp,
                headerPaddingV = 10.dp,
                sectionSpacing = 10.dp,
                rowPaddingV = 3.dp,
            )
        }
    }
}

@Composable
private fun NeonPopulatedContent(
    snapshot: AttendanceSnapshot,
    showRefresh: Boolean,
    openApp: androidx.glance.action.Action,
    themeId: AppThemeId,
) {
    val colors = GlanceTheme.colors
    val visible = snapshot.subjects.take(MAX_VISIBLE_SUBJECTS)
    val hiddenCount = snapshot.subjects.size - visible.size
    val rowFontSize = 14.sp
    val percentageFontSize = (rowFontSize.value + 1f).sp
    val fractionFontSize = (percentageFontSize.value * 0.80f).sp

    Column(modifier = GlanceModifier.fillMaxWidth()) {
        NeonOverallHeader(
            snapshot = snapshot,
            showRefresh = showRefresh,
            openApp = openApp,
            themeId = themeId,
        )
        Spacer(GlanceModifier.height(12.dp))
        Column(
            modifier = GlanceModifier
                .fillMaxWidth()
                .clickable(openApp),
            verticalAlignment = Alignment.Vertical.Top,
        ) {
            visible.forEach { subject ->
                NeonSubjectRow(
                    subject = subject,
                    themeId = themeId,
                    codeFontSize = rowFontSize,
                    nameFontSize = (rowFontSize.value - 1f).sp,
                    percentageFontSize = percentageFontSize,
                    fractionFontSize = fractionFontSize,
                )
                Spacer(GlanceModifier.height(10.dp))
            }
            if (hiddenCount > 0) {
                Text(
                    text = "+$hiddenCount more",
                    style = TextStyle(
                        color = colors.onSurfaceVariant,
                        fontSize = 12.sp,
                    ),
                )
            }
        }
    }
}

@Composable
private fun NeonOverallHeader(
    snapshot: AttendanceSnapshot,
    showRefresh: Boolean,
    openApp: androidx.glance.action.Action,
    themeId: AppThemeId,
) {
    val colors = GlanceTheme.colors
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.Vertical.Top,
    ) {
        Column(
            modifier = GlanceModifier
                .defaultWeight()
                .clickable(openApp),
        ) {
            Text(
                text = "Overall",
                style = TextStyle(
                    color = colors.onSurfaceVariant,
                    fontSize = 12.sp,
                ),
            )
            Spacer(GlanceModifier.height(2.dp))
            Text(
                text = "%.1f%%".format(snapshot.overallPercentage),
                style = TextStyle(
                    color = AppThemeSpec.widgetPercentageColor(snapshot.overallPercentage, themeId),
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Spacer(GlanceModifier.height(4.dp))
            Text(
                text = snapshotMetadata(snapshot),
                style = TextStyle(
                    color = colors.onSurfaceVariant,
                    fontSize = 12.sp,
                ),
            )
        }
        if (showRefresh) {
            NeonRefreshButton()
        }
    }
}

@Composable
private fun NeonRefreshButton() {
    val context = LocalContext.current
    val colors = GlanceTheme.colors
    CircleIconButton(
        imageProvider = ImageProvider(R.drawable.ic_refresh),
        contentDescription = context.getString(R.string.widget_refresh),
        onClick = actionRunCallback<RefreshAction>(),
        backgroundColor = colors.primaryContainer,
        contentColor = colors.onSurface,
    )
}

@Composable
private fun NeonSubjectRow(
    subject: Subject,
    themeId: AppThemeId,
    codeFontSize: TextUnit,
    nameFontSize: TextUnit,
    percentageFontSize: TextUnit,
    fractionFontSize: TextUnit,
) {
    val colors = GlanceTheme.colors
    val codeColor = if (subject.percentage < 70.0) {
        AppThemeSpec.widgetPercentageColor(subject.percentage, themeId)
    } else {
        colors.onSurface
    }
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.Vertical.Top,
    ) {
        Column(
            modifier = GlanceModifier
                .defaultWeight()
                .padding(end = 8.dp),
        ) {
            Text(
                text = subject.code,
                style = TextStyle(
                    color = codeColor,
                    fontSize = codeFontSize,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 1,
            )
            Spacer(GlanceModifier.height(2.dp))
            Text(
                text = subject.name,
                style = TextStyle(
                    color = colors.onSurfaceVariant,
                    fontSize = nameFontSize,
                ),
                maxLines = 1,
            )
        }
        Row(
            verticalAlignment = Alignment.Vertical.CenterVertically,
        ) {
            Text(
                text = widgetSubjectFraction(subject),
                style = TextStyle(
                    color = colors.onSurfaceVariant,
                    fontSize = fractionFontSize,
                ),
                maxLines = 1,
            )
            Spacer(GlanceModifier.width(8.dp))
            Text(
                text = widgetSubjectPercentageOneDecimal(subject),
                style = TextStyle(
                    color = AppThemeSpec.widgetPercentageColor(subject.percentage, themeId),
                    fontSize = percentageFontSize,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun PopulatedContent(
    snapshot: AttendanceSnapshot,
    aliasStore: SubjectAliasStore,
    showRefresh: Boolean,
    openApp: androidx.glance.action.Action,
    themeId: AppThemeId,
    subjectLimit: Int,
    overallFontSize: TextUnit,
    rowFontSize: TextUnit,
    showMetadata: Boolean,
    sortByLowestAttendance: Boolean,
    headerPaddingH: androidx.compose.ui.unit.Dp,
    headerPaddingV: androidx.compose.ui.unit.Dp,
    sectionSpacing: androidx.compose.ui.unit.Dp,
    rowPaddingV: androidx.compose.ui.unit.Dp,
) {
    val colors = GlanceTheme.colors
    val subjects = if (sortByLowestAttendance) {
        orderedSubjectsForSmall(snapshot.subjects)
    } else {
        snapshot.subjects
    }
    val visible = subjects.take(subjectLimit)
    val hiddenCount = subjects.size - visible.size

    Column(modifier = GlanceModifier.fillMaxWidth()) {
        OverallHeaderCard(
            snapshot = snapshot,
            showRefresh = showRefresh,
            openApp = openApp,
            themeId = themeId,
            overallFontSize = overallFontSize,
            showMetadata = showMetadata,
            paddingH = headerPaddingH,
            paddingV = headerPaddingV,
        )
        Spacer(GlanceModifier.height(sectionSpacing))
        WidgetDivider()
        Spacer(GlanceModifier.height(sectionSpacing))
        Column(
            modifier = GlanceModifier
                .fillMaxWidth()
                .clickable(openApp),
        ) {
            visible.forEach { subject ->
                SubjectRow(
                    subject = subject,
                    aliasStore = aliasStore,
                    themeId = themeId,
                    fontSize = rowFontSize,
                    rowPaddingV = rowPaddingV,
                )
            }
            if (hiddenCount > 0) {
                Spacer(GlanceModifier.height(4.dp))
                Text(
                    text = "+$hiddenCount more",
                    style = TextStyle(
                        color = colors.onSurfaceVariant,
                        fontSize = (rowFontSize.value - 1).sp,
                        fontFamily = FontFamily.Monospace,
                    ),
                )
            }
        }
    }
}

@Composable
private fun OverallHeaderCard(
    snapshot: AttendanceSnapshot,
    showRefresh: Boolean,
    openApp: androidx.glance.action.Action,
    themeId: AppThemeId,
    overallFontSize: TextUnit,
    showMetadata: Boolean,
    paddingH: androidx.compose.ui.unit.Dp,
    paddingV: androidx.compose.ui.unit.Dp,
) {
    val colors = GlanceTheme.colors
    Box(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(horizontal = paddingH, vertical = paddingV),
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.Vertical.CenterVertically,
        ) {
            Column(
                modifier = GlanceModifier
                    .defaultWeight()
                    .clickable(openApp),
            ) {
                Text(
                    text = "%.2f%%".format(snapshot.overallPercentage),
                    style = TextStyle(
                        color = AppThemeSpec.widgetPercentageColor(snapshot.overallPercentage, themeId),
                        fontSize = overallFontSize,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                    ),
                    maxLines = 1,
                )
                if (showMetadata) {
                    Spacer(GlanceModifier.height(4.dp))
                    Text(
                        text = snapshotMetadata(snapshot),
                        style = TextStyle(
                            color = colors.onSurfaceVariant,
                            fontSize = 12.sp,
                        ),
                    )
                }
            }
            if (showRefresh) {
                RefreshIconButton(contentColor = colors.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun RefreshIconButton(contentColor: ColorProvider) {
    val context = LocalContext.current
    CircleIconButton(
        imageProvider = ImageProvider(R.drawable.ic_refresh),
        contentDescription = context.getString(R.string.widget_refresh),
        onClick = actionRunCallback<RefreshAction>(),
        backgroundColor = null,
        contentColor = contentColor,
    )
}

@Composable
private fun SubjectRow(
    subject: Subject,
    aliasStore: SubjectAliasStore,
    themeId: AppThemeId,
    fontSize: TextUnit,
    rowPaddingV: androidx.compose.ui.unit.Dp,
) {
    val colors = GlanceTheme.colors
    val labelColor = if (subject.percentage < 70.0) {
        AppThemeSpec.widgetPercentageColor(subject.percentage, themeId)
    } else {
        colors.onSurface
    }
    val label = SubjectRowLayout.label(subject, aliasStore)
    val percentageFontSize = (fontSize.value + 1f).sp
    val fractionFontSize = (percentageFontSize.value * 0.80f).sp
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(vertical = rowPaddingV),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        Text(
            text = label,
            style = TextStyle(
                color = labelColor,
                fontSize = fontSize,
                fontFamily = FontFamily.Monospace,
            ),
            maxLines = 1,
        )
        Row(
            modifier = GlanceModifier.defaultWeight(),
            horizontalAlignment = Alignment.Horizontal.End,
            verticalAlignment = Alignment.Vertical.CenterVertically,
        ) {
            Text(
                text = widgetSubjectFraction(subject),
                style = TextStyle(
                    color = colors.onSurfaceVariant,
                    fontSize = fractionFontSize,
                    fontFamily = FontFamily.Monospace,
                ),
                maxLines = 1,
            )
            Spacer(GlanceModifier.width(6.dp))
            Text(
                text = widgetSubjectPercentage(subject),
                style = TextStyle(
                    color = AppThemeSpec.widgetPercentageColor(subject.percentage, themeId),
                    fontSize = percentageFontSize,
                    fontFamily = FontFamily.Monospace,
                ),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun EmptyState(
    message: String,
    action: androidx.glance.action.Action,
) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .clickable(action),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            style = TextStyle(
                color = GlanceTheme.colors.onSurfaceVariant,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
    }
}

@Composable
private fun WidgetDivider() {
    Box(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(1.dp)
            .background(GlanceTheme.colors.outline),
    ) {}
}

private fun widgetSubjectFraction(subject: Subject): String =
    "%2d/%2d".format(subject.present, subject.total)

private fun widgetSubjectPercentage(subject: Subject): String =
    "%.2f%%".format(subject.percentage)

private fun widgetSubjectPercentageOneDecimal(subject: Subject): String =
    "%.1f%%".format(subject.percentage)

private fun orderedSubjectsForSmall(subjects: List<Subject>): List<Subject> =
    subjects.sortedWith(
        compareBy<Subject> { it.percentage }
            .thenBy { it.code.lowercase() },
    )

private fun snapshotMetadata(snapshot: AttendanceSnapshot): String =
    "${snapshot.totalPresent} / ${snapshot.totalDelivered} · " +
        "${snapshot.termName} · ${RelativeTime.label(snapshot.fetchedAtMillis)}"

private val SMALL_SIZE = DpSize(140.dp, 110.dp)
private val MEDIUM_SIZE = DpSize(220.dp, 160.dp)
private val EXPANDED_SIZE = DpSize(300.dp, 260.dp)
private val SMALL_OVERALL_FONT = 24.sp
private val SMALL_ROW_FONT = 13.sp
private const val SMALL_SUBJECT_LIMIT = 4
private const val MAX_VISIBLE_SUBJECTS = 8
