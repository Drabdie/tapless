package com.aiphoneagent.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val AgentBlue = Color(0xFF3E7BFA)
val AgentInk = Color(0xFF121826)
val AgentNight = Color(0xFF0E1422)
val AgentDanger = Color(0xFFFF5A6A)
val AgentOk = Color(0xFF1F9D6A)

private val DarkColors = darkColorScheme(
    primary = AgentBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF1E3A8A),
    onPrimaryContainer = Color(0xFFD6E2FF),
    secondary = Color(0xFF8FB4FF),
    onSecondary = AgentInk,
    background = AgentNight,
    onBackground = Color(0xFFF4F7FF),
    surface = Color(0xFF121A2B),
    onSurface = Color(0xFFF4F7FF),
    surfaceVariant = Color(0xFF1C2740),
    onSurfaceVariant = Color(0xFF93A0B8),
    error = AgentDanger,
    onError = Color(0xFF2A0A10),
    outline = Color(0xFF2C3A55),
)

private val LightColors = lightColorScheme(
    primary = AgentBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE6FF),
    onPrimaryContainer = Color(0xFF0B1B4A),
    secondary = Color(0xFF3457B2),
    onSecondary = Color.White,
    background = Color(0xFFF6F8FC),
    onBackground = AgentInk,
    surface = Color.White,
    onSurface = AgentInk,
    surfaceVariant = Color(0xFFE7ECF5),
    onSurfaceVariant = Color(0xFF52607A),
    error = Color(0xFFD3263A),
    onError = Color.White,
    outline = Color(0xFFD5DCEC),
)

private val AppType = Typography(
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 34.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
)

@Composable
fun AiAgentTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppType,
        content = content,
    )
}
