package com.wearsic.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.wear.compose.material.Colors as M2Colors
import androidx.wear.compose.material.MaterialTheme as M2MaterialTheme
import androidx.wear.compose.material3.MaterialTheme as M3MaterialTheme
import androidx.wear.compose.material3.dynamicColorScheme
import androidx.wear.compose.material3.Typography

/**
 * Wearsic app typography
 */
private val WearsicTypography = Typography()

/**
 * Wearsic app accent color (also the seed for the dynamic scheme when the
 * watch exposes one).
 */
val WearsicAccent = Color(0xFFB7F397)

/**
 * Material 2 color scheme for the Horologist components (TimeText, media
 * buttons, PositionIndicator, Scaffold). Horologist 0.6.x ships on the legacy
 * Wear Compose Material 1.x line, so its widgets read the M2 theme — we map it
 * to the same palette as the M3 theme so the whole app looks like one design.
 */
private val WearsicM2Colors = M2Colors(
    primary = WearsicAccent,
    onPrimary = Color(0xFF17210F),
    primaryVariant = Color(0xFF304A24),
    secondary = Color(0xFFB8CCAE),
    onSecondary = Color(0xFF1D241A),
    secondaryVariant = Color(0xFF354A2D),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    background = Color(0xFF0B0D0A),
    onBackground = Color(0xFFE2E3DA),
    surface = Color(0xFF171A16),
    onSurface = Color(0xFFE2E3DA)
)

/**
 * Wearsic app theme using Material 3, with an inner Material 2 theme so the
 * Horologist widgets (time text, media controls, position indicator) inherit
 * the same green accent and dark surfaces.
 */
@Composable
fun WearsicTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = dynamicColorScheme(LocalContext.current)
        ?: androidx.wear.compose.material3.ColorScheme(
            primary = WearsicAccent,
            onPrimary = Color(0xFF17210F),
            primaryContainer = Color(0xFF304A24),
            onPrimaryContainer = Color(0xFFD2FFB8),
            secondary = Color(0xFFB8CCAE),
            onSecondary = Color(0xFF1D241A),
            secondaryContainer = Color(0xFF354A2D),
            onSecondaryContainer = Color(0xFFD4EBC7),
            tertiary = Color(0xFFA6D5D5),
            onTertiary = Color(0xFF0A2526),
            tertiaryContainer = Color(0xFF2B4C4D),
            onTertiaryContainer = Color(0xFFC1EBEB),
            surfaceContainerLow = Color(0xFF10120F),
            surfaceContainer = Color(0xFF171A16),
            surfaceContainerHigh = Color(0xFF22251F),
            onSurface = Color(0xFFE2E3DA),
            onSurfaceVariant = Color(0xFFC1C9B8),
            outline = Color(0xFF899182),
            outlineVariant = Color(0xFF41483D),
            background = Color(0xFF0B0D0A),
            onBackground = Color(0xFFE2E3DA),
            error = Color(0xFFFFB4AB),
            errorDim = Color(0xFFB3261E),
            errorContainer = Color(0xFF8C1D18),
            onError = Color(0xFF690005),
            onErrorContainer = Color(0xFFFFDAD6)
        )
    M3MaterialTheme(
        colorScheme = colorScheme,
        typography = WearsicTypography
    ) {
        M2MaterialTheme(
            colors = WearsicM2Colors
        ) {
            content()
        }
    }
}
