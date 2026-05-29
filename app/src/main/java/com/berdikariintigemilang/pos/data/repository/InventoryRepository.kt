package com.berdikariintigemilang.pos.data.repository

import com.berdikariintigemilang.pos.core.network.ApiResult
import com.berdikariintigemilang.pos.core.network.safePosCall
import com.berdikariintigemilang.pos.data.remote.ApiService
import com.berdikariintigemilang.pos.data.remote.StockAdjustmentRequest
import com.berdikariintigemilang.pos.data.remote.StockDto
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InventoryRepository @Inject constructor(
    private val api: ApiService
) {
    suspend fun stocks(search: String?, lowStock: Boolean): ApiResult<List<StockDto>> =
        safePosCall { api.stocks(search?.takeIf { it.isNotBlank() }, lowStock) }

    suspend fun adjust(productId: Long, quantity: Int, notes: String): ApiResult<StockDto> =
        safePosCall { api.adjustStock(StockAdjustmentRequest(productId, quantity, notes)) }
}
