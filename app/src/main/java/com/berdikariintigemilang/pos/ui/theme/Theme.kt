package com.berdikariintigemilang.pos.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = BigRed,
    onPrimary = Color.White,
    primaryContainer = BigRedLight,
    secondary = PosGreen,
    onSecondary = Color.White,
    background = PosNeutralBg,
    surface = PosSurface,
    onSurface = PosOnSurface,
    error = BigRedDark
)

private val DarkColors = darkColorScheme(
    primary = BigRedLight,
    onPrimary = Color.Black,
    secondary = PosGreen,
    background = Color(0xFF121417),
    surface = Color(0xFF1C1F24),
    onSurface = Color(0xFFE4E6EB)
)

@Composable
fun PosTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = PosTypography,
        content = content
    )
}
