package com.berdikariintigemilang.pos.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Placeholder sementara untuk tab yang diisi pada task berikutnya. */
@Composable
fun PlaceholderScreen(title: String, subtitle: String, modifier: Modifier = Modifier) {
    EmptyState(
        icon = Icons.Filled.Info,
        title = title,
        subtitle = subtitle,
        modifier = modifier
    )
}
