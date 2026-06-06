package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val BentoColorScheme = darkColorScheme(
    primary = Lavender,
    onPrimary = OnLavender,
    secondary = SoftPink,
    tertiary = SoftPink,
    background = BentoDark,
    onBackground = TextMain,
    surface = BentoCardMedium,
    onSurface = TextMain,
    surfaceVariant = BentoCardLight,
    onSurfaceVariant = TextMuted,
    outline = BentoHighlight,
    outlineVariant = BentoHighlight.copy(alpha = 0.4f),
    error = Color(0xFFF2B8B5)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Force premium dark Bento theme for optimal aesthetic
    dynamicColor: Boolean = false, // Disable dynamic colors to keep design integrity
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = BentoColorScheme,
        typography = Typography,
        content = content
    )
}
