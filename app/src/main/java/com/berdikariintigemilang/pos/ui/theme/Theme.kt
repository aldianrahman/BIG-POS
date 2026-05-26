package com.berdikariintigemilang.pos.ui.theme

import androidx.compose.material3.MaterialTheme
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

/**
 * Dark mode dimatikan: aplikasi selalu memakai skema terang agar tampilan
 * kasir konsisten di semua perangkat tanpa terpengaruh setting sistem.
 */
@Composable
fun PosTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = PosTypography,
        content = content
    )
}
