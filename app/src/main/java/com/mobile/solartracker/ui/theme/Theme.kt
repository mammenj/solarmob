package com.mobile.solartracker.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Palette carried over from the web app (slate + emerald).
val Solar = Color(0xFFF59E0B)
val Export = Color(0xFF10B981)
val Import = Color(0xFFEF4444)
val Consumption = Color(0xFF06B6D4)
val SelfConsumption = Color(0xFFF5E027)

val Bg = Color(0xFF0F172A)
val Surface1 = Color(0xFF1E293B)
val Surface2 = Color(0xFF334155)
val Muted = Color(0xFF94A3B8)
val GridLine = Color(0xFF1E293B)

private val Colors = darkColorScheme(
    primary = Color(0xFF10B981),
    onPrimary = Color(0xFF04231A),
    primaryContainer = Color(0xFF064E3B),
    onPrimaryContainer = Color(0xFFA7F3D0),
    secondary = Consumption,
    onSecondary = Color(0xFF04212A),
    background = Bg,
    onBackground = Color(0xFFE2E8F0),
    surface = Surface1,
    onSurface = Color(0xFFE2E8F0),
    surfaceVariant = Surface2,
    onSurfaceVariant = Muted,
    outline = Color(0xFF475569),
    error = Import,
    onError = Color.White,
)

@Composable
fun SolarTrackerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, content = content)
}
