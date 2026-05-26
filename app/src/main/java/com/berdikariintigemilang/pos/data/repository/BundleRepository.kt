package com.berdikariintigemilang.pos.data.repository

import com.berdikariintigemilang.pos.core.network.ApiResult
import com.berdikariintigemilang.pos.core.network.safePosCall
import com.berdikariintigemilang.pos.data.cart.CartLine
import com.berdikariintigemilang.pos.data.remote.ApiService
import com.berdikariintigemilang.pos.data.remote.BundleCalcItem
import com.berdikariintigemilang.pos.data.remote.BundleCalcRequest
import com.berdikariintigemilang.pos.data.remote.BundleCalcResult
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BundleRepository @Inject constructor(
    private val api: ApiService
) {
    suspend fun calculate(lines: List<CartLine>): ApiResult<BundleCalcResult> =
        safePosCall {
            api.calculateBundles(
                BundleCalcRequest(lines.map { BundleCalcItem(it.productId, it.quantity) })
            )
        }
}
