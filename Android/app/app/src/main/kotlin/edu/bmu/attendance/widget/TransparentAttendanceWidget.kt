package edu.bmu.attendance.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.res.ResourcesCompat
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.width
import androidx.glance.unit.ColorProvider
import edu.bmu.attendance.MainActivity
import edu.bmu.attendance.R
import edu.bmu.attendance.data.AttendanceRepository
import edu.bmu.attendance.data.AttendanceSnapshot
import edu.bmu.attendance.data.CredentialStore

class TransparentAttendanceWidget : GlanceAppWidget() {
    // Exact size so LocalSize matches the placed host width; needed for the
    // 15% leading inset to line up with Niagara’s text column.
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = AttendanceRepository.get(context).cachedSnapshot
        val hasCredentials = CredentialStore(context).hasCredentials()

        provideContent {
            TransparentWidgetContent(
                snapshot = snapshot,
                hasCredentials = hasCredentials,
            )
        }
    }
}

@Composable
private fun TransparentWidgetContent(
    snapshot: AttendanceSnapshot?,
    hasCredentials: Boolean,
) {
    val context = LocalContext.current
    val openApp = actionStartActivity<MainActivity>()
    // Match Niagara’s left text column (icons / At a Glance), not Scaffold chrome.
    val startInset = LocalSize.current.width * START_INSET_FRACTION

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(Color.Transparent))
            .clickable(openApp),
        contentAlignment = Alignment.BottomStart,
    ) {
        when {
            !hasCredentials -> InsetManropeText(
                text = context.getString(R.string.widget_no_credentials),
                fontSize = 12.sp,
                startInset = startInset,
            )
            snapshot == null -> InsetManropeText(
                text = context.getString(R.string.widget_open_app),
                fontSize = 12.sp,
                startInset = startInset,
            )
            else -> InsetManropeText(
                text = "%.2f%%".format(snapshot.overallPercentage),
                fontSize = 28.sp,
                startInset = startInset,
            )
        }
    }
}

@Composable
private fun InsetManropeText(
    text: String,
    fontSize: TextUnit,
    startInset: Dp,
) {
    Row(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.Bottom,
    ) {
        Spacer(modifier = GlanceModifier.width(startInset))
        ManropeImageText(
            text = text,
            fontSize = fontSize,
            color = Color.White,
        )
    }
}

/**
 * Glance cannot load app-bundled fonts into [androidx.glance.text.Text]; Niagara's
 * Manrope isn't a system face either. Render Manrope SemiBold to a bitmap instead.
 */
@Composable
private fun ManropeImageText(
    text: String,
    fontSize: TextUnit,
    color: Color,
) {
    val context = LocalContext.current
    val density = context.resources.displayMetrics.density
    val bitmap = renderManropeText(
        context = context,
        text = text,
        textSizePx = fontSize.value * density,
        colorArgb = color.toArgb(),
    )
    Image(
        provider = ImageProvider(bitmap),
        contentDescription = text,
    )
}

private fun renderManropeText(
    context: Context,
    text: String,
    textSizePx: Float,
    colorArgb: Int,
): Bitmap {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = manropeSemiBold(context)
        textSize = textSizePx
        color = colorArgb
        isSubpixelText = true
        // Variable Manrope defaults to ExtraLight; force SemiBold explicitly.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            fontVariationSettings = "'wght' 600"
        }
        isFakeBoldText = false
    }
    // Tight crop to glyph bounds so layout padding aligns to the visible ink,
    // not font side-bearings (digits often have empty left bearing).
    val bounds = Rect()
    paint.getTextBounds(text, 0, text.length, bounds)
    val width = bounds.width().coerceAtLeast(1)
    val height = bounds.height().coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    Canvas(bitmap).drawText(
        text,
        -bounds.left.toFloat(),
        -bounds.top.toFloat(),
        paint,
    )
    return bitmap
}

private fun manropeSemiBold(context: Context): Typeface {
    // Prefer the font-family XML that pins wght 600; fall back to the variable TTF.
    val fromFamily = ResourcesCompat.getFont(context, R.font.manrope_semibold)
    if (fromFamily != null) return fromFamily
    val variable = ResourcesCompat.getFont(context, R.font.manrope) ?: return Typeface.DEFAULT
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        Typeface.create(variable, /* weight = */ 600, /* italic = */ false)
    } else {
        Typeface.create(variable, Typeface.BOLD)
    }
}

/** Slight leading inset; keep text a hair left of Niagara’s icon/text column. */
private const val START_INSET_FRACTION = 0.08f
