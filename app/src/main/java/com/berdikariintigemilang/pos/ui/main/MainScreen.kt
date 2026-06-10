package com.berdikariintigemilang.pos.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.PointOfSale
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.berdikariintigemilang.pos.ui.dashboard.DashboardScreen
import com.berdikariintigemilang.pos.ui.inventory.InventoryScreen
import com.berdikariintigemilang.pos.ui.navigation.MainTab
import com.berdikariintigemilang.pos.ui.pos.PosScreen
import com.berdikariintigemilang.pos.ui.reports.ReportsScreen
import com.berdikariintigemilang.pos.ui.settings.SettingsScreen

private data class TabIcon(val selected: ImageVector, val unselected: ImageVector)

private fun iconFor(tab: MainTab): TabIcon = when (tab) {
    MainTab.POS -> TabIcon(Icons.Filled.PointOfSale, Icons.Outlined.PointOfSale)
    MainTab.INVENTORY -> TabIcon(Icons.Filled.Inventory2, Icons.Outlined.Inventory2)
    MainTab.DASHBOARD -> TabIcon(Icons.Filled.Dashboard, Icons.Outlined.Dashboard)
    MainTab.REPORTS -> TabIcon(Icons.Filled.BarChart, Icons.Outlined.BarChart)
    MainTab.SETTINGS -> TabIcon(Icons.Filled.Settings, Icons.Outlined.Settings)
}

@Composable
fun MainScreen(
    onCloseShift: (Long) -> Unit,
    onLoggedOut: () -> Unit,
    onScan: () -> Unit = {},
    onSearch: () -> Unit = {},
    onCheckout: () -> Unit = {},
    onProductClick: (Long) -> Unit = {},
    onTransactions: () -> Unit = {},
    onAddProduct: () -> Unit = {},
    onPriceLog: () -> Unit = {}
) {
    var selectedTab by rememberSaveable { mutableStateOf(MainTab.POS) }

    Scaffold(
        bottomBar = {
            PosBottomBar(selected = selectedTab, onSelect = { selectedTab = it })
        }
    ) { padding ->
        // consumeWindowInsets agar imePadding di layar anak (mis. Kasir) tidak
        // menghitung ganda inset bottom-bar saat keyboard muncul.
        val contentModifier = Modifier.padding(padding).consumeWindowInsets(padding)
        when (selectedTab) {
            MainTab.POS -> PosScreen(
                modifier = contentModifier,
                onScanClick = onScan,
                onSearchClick = onSearch,
                onCheckout = onCheckout,
                onHistory = onTransactions
            )
            MainTab.INVENTORY -> InventoryScreen(
                modifier = contentModifier,
                onProductClick = onProductClick,
                onAddProduct = onAddProduct
            )
            MainTab.DASHBOARD -> DashboardScreen(contentModifier)
            MainTab.REPORTS -> ReportsScreen(contentModifier)
            MainTab.SETTINGS -> SettingsScreen(
                onCloseShift = onCloseShift,
                onLoggedOut = onLoggedOut,
                onTransactions = onTransactions,
                onPriceLog = onPriceLog,
                modifier = contentModifier
            )
        }
    }
}

/** Bottom navigation: indikator merah di tepi atas tab aktif, tanpa "pill". */
@Composable
private fun PosBottomBar(selected: MainTab, onSelect: (MainTab) -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 12.dp
    ) {
        Column {
            HorizontalDivider(
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .height(66.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MainTab.entries.forEach { tab ->
                    NavItem(
                        tab = tab,
                        selected = tab == selected,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        onClick = { onSelect(tab) }
                    )
                }
            }
        }
    }
}

@Composable
private fun NavItem(
    tab: MainTab,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    val icon = iconFor(tab)
    Box(
        modifier = modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick
        ),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .width(30.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(bottomStart = 3.dp, bottomEnd = 3.dp))
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = if (selected) icon.selected else icon.unselected,
                contentDescription = tab.label,
                tint = color,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                tab.label,
                style = MaterialTheme.typography.labelMedium,
                color = color,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}
