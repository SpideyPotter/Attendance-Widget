package edu.bmu.attendance.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.glance.color.ColorProviders
import androidx.glance.material3.ColorProviders as GlanceM3ColorProviders
import androidx.glance.unit.ColorProvider

object AppThemeSpec {
    private val IosActionBlue = Color(0xFF0A84FF)
    private val IosRed = Color(0xFFFF3B30)

    // Neon reference palette (screenshot: dark charcoal-blue + vivid accents).
    val NeonBackground = Color(0xFF0B1622)
    val NeonSurfaceRaised = Color(0xFF152238)
    val NeonTextSecondary = Color(0xFF8B9AAB)
    val NeonGreen = Color(0xFF32FF8F)
    val NeonYellow = Color(0xFFFFB020)
    val NeonRed = Color(0xFFFF453A)

    private val IphoneLight = lightColorScheme(
        primary = IosActionBlue,
        onPrimary = Color.White,
        primaryContainer = Color(0xFFE8F4FF),
        onPrimaryContainer = Color.Black,
        background = Color(0xFFF2F2F7),
        onBackground = Color.Black,
        surface = Color(0xFFF2F2F7),
        onSurface = Color.Black,
        surfaceContainerHigh = Color.White,
        onSurfaceVariant = Color.Black.copy(alpha = 0.55f),
        outline = Color.Black.copy(alpha = 0.20f),
        error = IosRed,
    )

    private val IphoneDark = darkColorScheme(
        primary = IosActionBlue,
        onPrimary = Color.White,
        primaryContainer = Color(0xFF1C1C1E),
        onPrimaryContainer = Color.White,
        background = Color.Black,
        onBackground = Color.White,
        surface = Color.Black,
        onSurface = Color.White,
        surfaceContainerHigh = Color(0xFF1C1C1E),
        onSurfaceVariant = Color.White.copy(alpha = 0.65f),
        outline = Color.White.copy(alpha = 0.25f),
        error = IosRed,
    )

    private val Neon = darkColorScheme(
        primary = NeonGreen,
        onPrimary = NeonBackground,
        primaryContainer = NeonSurfaceRaised,
        onPrimaryContainer = Color.White,
        background = NeonBackground,
        onBackground = Color.White,
        surface = NeonBackground,
        onSurface = Color.White,
        surfaceContainerHigh = NeonSurfaceRaised,
        onSurfaceVariant = NeonTextSecondary,
        outline = Color(0xFF243447),
        error = NeonRed,
    )

    @Composable
    fun materialColorScheme(themeId: AppThemeId): ColorScheme {
        val context = LocalContext.current
        val systemDark = isSystemInDarkTheme()
        return when (themeId) {
            AppThemeId.DEFAULT -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (systemDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                } else {
                    if (systemDark) IphoneDark else IphoneLight
                }
            }
            AppThemeId.LIGHT -> IphoneLight
            AppThemeId.DARK -> IphoneDark
            AppThemeId.NEON -> Neon
        }
    }

    fun glanceColorProviders(themeId: AppThemeId): ColorProviders? = when (themeId) {
        AppThemeId.DEFAULT -> null
        AppThemeId.LIGHT -> glanceScheme(IphoneLight)
        AppThemeId.DARK -> glanceScheme(IphoneDark)
        AppThemeId.NEON -> glanceScheme(Neon)
    }

    fun widgetPercentageColor(percentage: Double, themeId: AppThemeId): ColorProvider {
        if (themeId == AppThemeId.NEON) {
            return when {
                percentage < 70.0 -> ColorProvider(NeonRed)
                percentage < 80.0 -> ColorProvider(NeonYellow)
                else -> ColorProvider(NeonGreen)
            }
        }
        return when {
            percentage < 70.0 -> ColorProvider(IosRed)
            percentage < 80.0 -> ColorProvider(Color(0xFFFF9F0A))
            else -> ColorProvider(Color(0xFF34C759))
        }
    }

    private fun glanceScheme(scheme: ColorScheme): ColorProviders {
        val flat = scheme.copy(
            surface = scheme.background,
            secondaryContainer = scheme.background,
        )
        return GlanceM3ColorProviders(flat)
    }

    fun usesDarkChrome(themeId: AppThemeId, systemDark: Boolean): Boolean = when (themeId) {
        AppThemeId.DEFAULT -> systemDark
        AppThemeId.LIGHT -> false
        AppThemeId.DARK, AppThemeId.NEON -> true
    }
}
