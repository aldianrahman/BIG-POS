package com.berdikariintigemilang.pos.data.repository

import com.berdikariintigemilang.pos.core.network.ApiResult
import com.berdikariintigemilang.pos.core.network.safePosCall
import com.berdikariintigemilang.pos.data.remote.ApiService
import com.berdikariintigemilang.pos.data.remote.DashboardSummaryDto
import com.berdikariintigemilang.pos.data.remote.StockDto
import com.berdikariintigemilang.pos.data.remote.TopProductDto
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DashboardRepository @Inject constructor(
    private val api: ApiService
) {
    suspend fun summary(date: String): ApiResult<DashboardSummaryDto> =
        safePosCall { api.dashboardSummary(date) }

    suspend fun topProducts(date: String, limit: Int = 5): ApiResult<List<TopProductDto>> =
        safePosCall { api.topProducts(date, limit) }

    suspend fun lowStock(): ApiResult<List<StockDto>> =
        safePosCall { api.lowStockAlerts() }
}
