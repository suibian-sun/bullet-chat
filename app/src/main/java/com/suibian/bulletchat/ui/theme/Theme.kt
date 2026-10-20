package com.suibian.bulletchat.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val AccentCyan = Color(0xFF5FE3F3)
val SuccessGreen = Color(0xFF4ADE80)
val WarnAmber = Color(0xFFFBBF24)

private val BulletChatColors = darkColorScheme(
    primary = AccentCyan,
    onPrimary = Color(0xFF00343B),
    primaryContainer = Color(0xFF0E3A42),
    onPrimaryContainer = Color(0xFFB6F4FC),
    secondary = Color(0xFF8AB4FF),
    onSecondary = Color(0xFF0B2547),
    tertiary = Color(0xFFB69CFF),
    background = Color(0xFF080C12),
    onBackground = Color(0xFFE8EFF7),
    surface = Color(0xFF101823),
    onSurface = Color(0xFFE8EFF7),
    surfaceVariant = Color(0xFF18222F),
    onSurfaceVariant = Color(0xFF93A3B8),
    outline = Color(0xFF2B3949),
    error = Color(0xFFFF7A7A),
)

@Composable
fun BulletChatTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = BulletChatColors,
        content = content,
    )
}
