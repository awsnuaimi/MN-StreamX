package com.yourapp.iptv.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.yourapp.iptv.domain.model.HomeTheme

internal object AppThemeColors {
    private val dark = darkColorScheme(
        primary = Color(0xFF00B5EC), onPrimary = Color(0xFF031421),
        primaryContainer = Color(0xFF1A346C), onPrimaryContainer = Color(0xFFE2F4FF),
        inversePrimary = Color(0xFF005EB8),
        secondary = Color(0xFFB6CADC), onSecondary = Color(0xFF102438),
        secondaryContainer = Color(0xFF0F1C3D), onSecondaryContainer = Color(0xFFE1EFFA),
        tertiary = Color(0xFF8FE4A0), onTertiary = Color(0xFF003915),
        tertiaryContainer = Color(0xFF153529), onTertiaryContainer = Color(0xFFD4F8DC),
        error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
        background = Color(0xFF071127), onBackground = Color.White,
        surface = Color(0xFF122045), onSurface = Color.White,
        surfaceVariant = Color(0xFF172A52), onSurfaceVariant = Color(0xFFABBBD5),
        outline = Color(0xFF6886A5), outlineVariant = Color(0xFF263655),
        inverseSurface = Color(0xFFE1EAF4), inverseOnSurface = Color(0xFF16283B),
        surfaceTint = Color.Transparent, scrim = Color.Black,
        surfaceDim = Color(0xFF061024), surfaceBright = Color(0xFF1D3562),
        surfaceContainerLowest = Color(0xFF061024), surfaceContainerLow = Color(0xFF0F1C3D),
        surfaceContainer = Color(0xFF122045), surfaceContainerHigh = Color(0xFF172A52),
        surfaceContainerHighest = Color(0xFF1D3562)
    )
    private val light = lightColorScheme(
        primary = Color(0xFF005EB8), onPrimary = Color.White,
        primaryContainer = Color(0xFFDCEAF9), onPrimaryContainer = Color(0xFF12324D),
        inversePrimary = Color(0xFF9DCAFF),
        secondary = Color(0xFF45627B), onSecondary = Color.White,
        secondaryContainer = Color(0xFFE4EEF9), onSecondaryContainer = Color(0xFF19334B),
        tertiary = Color(0xFF006D37), onTertiary = Color.White,
        tertiaryContainer = Color(0xFFD0F1DA), onTertiaryContainer = Color(0xFF123C22),
        error = Color(0xFFBA1A1A), onError = Color.White,
        errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
        background = Color(0xFFF0F4F8), onBackground = Color(0xFF16283B),
        surface = Color.White, onSurface = Color(0xFF16283B),
        surfaceVariant = Color(0xFFE3EBF4), onSurfaceVariant = Color(0xFF52667A),
        outline = Color(0xFF75899D), outlineVariant = Color(0xFFB3C5D6),
        inverseSurface = Color(0xFF213244), inverseOnSurface = Color(0xFFF0F4F8),
        surfaceTint = Color.Transparent, scrim = Color.Black,
        surfaceDim = Color(0xFFDCE5EF), surfaceBright = Color.White,
        surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF5F8FC),
        surfaceContainer = Color(0xFFEDF3F9), surfaceContainerHigh = Color(0xFFE5EDF6),
        surfaceContainerHighest = Color(0xFFDCE7F2)
    )
    private val night = darkColorScheme(
        primary = NeonBlue, onPrimary = Color.Black,
        primaryContainer = Color(0xFF202020), onPrimaryContainer = Color.White,
        inversePrimary = Color(0xFF005EB8),
        secondary = Color(0xFFC4C4C4), onSecondary = Color.Black,
        secondaryContainer = Color(0xFF181818), onSecondaryContainer = Color.White,
        tertiary = Color(0xFF92D4A3), onTertiary = Color(0xFF052F13),
        tertiaryContainer = Color(0xFF14251A), onTertiaryContainer = Color(0xFFD6F3DC),
        error = Color(0xFFFF8080), onError = Color.Black,
        errorContainer = Color(0xFF3A1010), onErrorContainer = Color(0xFFFFDAD6),
        background = Color.Black, onBackground = Color.White,
        surface = Color(0xFF090909), onSurface = Color.White,
        surfaceVariant = Color(0xFF161616), onSurfaceVariant = Color(0xFFCACACA),
        outline = Color(0xFF777777), outlineVariant = Color(0xFF414141),
        inverseSurface = Color(0xFFE8E8E8), inverseOnSurface = Color(0xFF111111),
        surfaceTint = Color.Transparent, scrim = Color.Black,
        surfaceDim = Color.Black, surfaceBright = Color(0xFF202020),
        surfaceContainerLowest = Color.Black, surfaceContainerLow = Color(0xFF050505),
        surfaceContainer = Color(0xFF0A0A0A), surfaceContainerHigh = Color(0xFF141414),
        surfaceContainerHighest = Color(0xFF202020)
    )

    private val red = dark.copy(
        primary = Color(0xFFFF3535), onPrimary = Color.Black,
        primaryContainer = Color(0xFF3B1616), onPrimaryContainer = Color(0xFFFFE8E8),
        inversePrimary = Color(0xFFB71C1C),
        secondary = Color(0xFFD0D0D0), onSecondary = Color(0xFF151515),
        secondaryContainer = Color(0xFF1E1E1E), onSecondaryContainer = Color(0xFFF5F5F5),
        background = Color(0xFF101010), onBackground = Color(0xFFF5F5F5),
        surface = Color(0xFF191919), onSurface = Color(0xFFF5F5F5),
        surfaceVariant = Color(0xFF282828), onSurfaceVariant = Color(0xFFCCCCCC),
        outline = Color(0xFF888888), outlineVariant = Color(0xFF494949),
        inverseSurface = Color(0xFFEAEAEA), inverseOnSurface = Color(0xFF181818),
        surfaceDim = Color(0xFF0A0A0A), surfaceBright = Color(0xFF303030),
        surfaceContainerLowest = Color(0xFF0A0A0A), surfaceContainerLow = Color(0xFF151515),
        surfaceContainer = Color(0xFF191919), surfaceContainerHigh = Color(0xFF222222),
        surfaceContainerHighest = Color(0xFF2C2C2C)
    )

    fun forTheme(theme: HomeTheme, isNight: Boolean = false): ColorScheme = when {
        isNight -> night
        theme == HomeTheme.RED -> red
        theme == HomeTheme.LIGHT -> light
        else -> dark
    }

    fun favorite(colors: ColorScheme): Color = if (colors.onSurface.luminance() < 0.5f) {
        Color(0xFF855900)
    } else Color(0xFFFFD54F)

    fun warning(colors: ColorScheme): Color = if (colors.onSurface.luminance() < 0.5f) {
        Color(0xFF8A4F00)
    } else Color(0xFFFFD180)

    fun forMode(isDark: Boolean, isNight: Boolean = false): ColorScheme = when {
        isNight -> night
        isDark -> dark
        else -> light
    }
}