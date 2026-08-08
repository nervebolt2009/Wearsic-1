package com.wearsic.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.dynamicColorScheme
import androidx.wear.compose.material3.Typography

/**
 * Wearsic app typography
 */
private val WearsicTypography = Typography()

/**
 * Wearsic app theme using Material 3
 */
@Composable
fun WearsicTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = dynamicColorScheme(LocalContext.current)
        ?: androidx.wear.compose.material3.ColorScheme(
            primary = Color(0xFFB7F397),
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
    MaterialTheme(
        colorScheme = colorScheme,
        typography = WearsicTypography,
        content = content
    )
}
