package com.berdikariintigemilang.pos.ui.main

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.PointOfSale
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
    MainTab.REPORTS -> TabIcon(Icons.Filled.Assessment, Icons.Outlined.Assessment)
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
    onAddProduct: () -> Unit = {}
) {
    var selectedTab by rememberSaveable { mutableStateOf(MainTab.POS) }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp
            ) {
                MainTab.entries.forEach { tab ->
                    val icon = iconFor(tab)
                    val selected = selectedTab == tab
                    NavigationBarItem(
                        selected = selected,
                        onClick = { selectedTab = tab },
                        icon = {
                            Icon(
                                imageVector = if (selected) icon.selected else icon.unselected,
                                contentDescription = tab.label
                            )
                        },
                        label = { Text(tab.label, style = MaterialTheme.typography.labelMedium) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }
    ) { padding ->
        val contentModifier = Modifier.padding(padding)
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
                modifier = contentModifier
            )
        }
    }
}
