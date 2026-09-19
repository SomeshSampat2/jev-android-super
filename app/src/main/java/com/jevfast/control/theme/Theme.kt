package com.jevfast.control.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val JevFastScheme = darkColorScheme(
    primary = Accent,
    onPrimary = BgDeep,
    secondary = InfoBlue,
    tertiary = WarnAmber,
    background = BgDeep,
    surface = BgDeep,
    surfaceVariant = SurfaceCard,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onSurfaceVariant = TextMuted,
    outline = Border,
    error = ErrorRed,
)

@Composable
fun JevFastTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = JevFastScheme, typography = Typography, content = content)
}
