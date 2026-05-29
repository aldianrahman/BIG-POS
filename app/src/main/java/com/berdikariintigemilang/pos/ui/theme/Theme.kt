package com.berdikariintigemilang.pos.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = BigRed,
    onPrimary = Color.White,
    primaryContainer = BigRedContainer,
    onPrimaryContainer = OnBigRedContainer,

    secondary = PosGreen,
    onSecondary = Color.White,
    secondaryContainer = PosGreenContainer,
    onSecondaryContainer = OnPosGreenContainer,

    tertiary = PosAmber,
    onTertiary = Color(0xFF3D2A00),
    tertiaryContainer = PosAmberContainer,
    onTertiaryContainer = OnPosAmberContainer,

    background = PosBackground,
    onBackground = PosOnSurface,
    surface = PosSurface,
    onSurface = PosOnSurface,
    surfaceVariant = PosSurfaceVariant,
    onSurfaceVariant = PosOnSurfaceVariant,
    surfaceContainer = PosSurfaceContainer,
    surfaceContainerHighest = PosSurfaceVariant,

    outline = PosOutline,
    outlineVariant = PosOutlineVariant,

    error = PosError,
    onError = Color.White,
    errorContainer = PosErrorContainer,
    onErrorContainer = OnPosErrorContainer
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
        shapes = PosShapes,
        content = content
    )
}
