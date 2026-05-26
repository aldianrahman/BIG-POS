package com.berdikariintigemilang.pos.ui.pos

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.berdikariintigemilang.pos.ui.components.PlaceholderScreen

@Composable
fun PosScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(
        title = "Kasir (POS)",
        subtitle = "Scan/cari produk, keranjang, dan pembayaran akan tersedia di Task 5.",
        modifier = modifier
    )
}
