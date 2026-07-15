package com.securechat.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ── Color Palette ─────────────────────────────────────────────────────────────

private val Indigo600  = Color(0xFF4F46E5)
private val Indigo800  = Color(0xFF3730A3)
private val Emerald500 = Color(0xFF10B981)
private val Slate900   = Color(0xFF0F172A)
private val Slate800   = Color(0xFF1E293B)
private val Slate700   = Color(0xFF334155)
private val Slate200   = Color(0xFFE2E8F0)
private val Slate50    = Color(0xFFF8FAFC)
private val White      = Color(0xFFFFFFFF)

private val DarkColorScheme = darkColorScheme(
    primary          = Indigo600,
    onPrimary        = White,
    primaryContainer = Indigo800,
    secondary        = Emerald500,
    background       = Slate900,
    surface          = Slate800,
    surfaceVariant   = Slate700,
    onBackground     = Slate200,
    onSurface        = Slate200,
    onSurfaceVariant = Slate200,
    outline          = Slate700,
)

private val LightColorScheme = lightColorScheme(
    primary          = Indigo600,
    onPrimary        = White,
    primaryContainer = Color(0xFFE0E7FF),
    secondary        = Emerald500,
    background       = Slate50,
    surface          = White,
    surfaceVariant   = Slate200,
    onBackground     = Slate900,
    onSurface        = Slate900,
    onSurfaceVariant = Slate700,
    outline          = Slate200,
)

@Composable
fun SecureChatTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = Typography(),
        content     = content,
    )
}
