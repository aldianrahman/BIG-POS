package com.berdikariintigemilang.pos.data.repository

import com.berdikariintigemilang.pos.core.network.ApiResult
import com.berdikariintigemilang.pos.core.network.safePosCall
import com.berdikariintigemilang.pos.data.local.CatalogDao
import com.berdikariintigemilang.pos.data.local.LocalStockDao
import com.berdikariintigemilang.pos.data.remote.ApiService
import com.berdikariintigemilang.pos.data.remote.StockAdjustmentRequest
import com.berdikariintigemilang.pos.data.remote.StockDto
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stok dengan fallback OFFLINE: bila server tak terjangkau, daftar saldo stok
 * dilayani dari stok lokal (Room) — yaitu angka yang sudah dikurangi oleh
 * penjualan offline, sehingga kasir tetap melihat sisa stok yang sebenarnya.
 */
@Singleton
class InventoryRepository @Inject constructor(
    private val api: ApiService,
    private val catalogDao: CatalogDao,
    private val localStockDao: LocalStockDao
) {
    suspend fun stocks(search: String?, lowStock: Boolean): ApiResult<List<StockDto>> = try {
        val res = api.stocks(search?.takeIf { it.isNotBlank() }, lowStock)
        if (res.success && res.data != null) ApiResult.Success(res.data)
        else localStocksOrNull(search, lowStock) ?: ApiResult.Error(res.error?.message ?: res.message ?: "Gagal memuat stok")
    } catch (e: IOException) {
        localStocksOrNull(search, lowStock) ?: ApiResult.Error("Koneksi bermasalah. Periksa jaringan Anda.")
    } catch (e: HttpException) {
        ApiResult.Error(mapHttp(e.code()), httpStatus = e.code())
    } catch (e: Exception) {
        localStocksOrNull(search, lowStock) ?: ApiResult.Error(e.message ?: "Gagal memuat stok")
    }

    /** Penyesuaian stok manual hanya saat online (perlu konfirmasi server). */
    suspend fun adjust(productId: Long, quantity: Int, notes: String): ApiResult<StockDto> =
        safePosCall { api.adjustStock(StockAdjustmentRequest(productId, quantity, notes)) }

    /**
     * Daftar saldo stok dari cache lokal (server − penjualan belum tersinkron).
     * Dipakai untuk mode offline & kartu "stok menipis" di dashboard yang realtime.
     * Mengembalikan list kosong bila cache stok belum ada.
     */
    suspend fun localStockList(search: String?, lowStock: Boolean): List<StockDto> {
        val stocks = localStockDao.getAll()
        if (stocks.isEmpty()) return emptyList()
        val products = catalogDao.productsByIds(stocks.map { it.productId }).associateBy { it.id }
        val q = search?.trim()?.lowercase().orEmpty()
        return stocks.mapNotNull { st ->
            val p = products[st.productId] ?: return@mapNotNull null
            if (q.isNotEmpty() && !p.name.lowercase().contains(q) && !p.sku.lowercase().contains(q)) return@mapNotNull null
            val low = st.quantity <= st.minStock
            if (lowStock && !low) return@mapNotNull null
            StockDto(
                productId = st.productId,
                sku = p.sku,
                name = p.name,
                unit = p.unit,
                quantity = st.quantity,
                minStock = st.minStock,
                lowStock = low,
                updatedAt = null
            )
        }.sortedBy { it.name }
    }

    private suspend fun localStocksOrNull(search: String?, lowStock: Boolean): ApiResult.Success<List<StockDto>>? {
        if (localStockDao.count() == 0) return null
        return ApiResult.Success(localStockList(search, lowStock))
    }

    private fun mapHttp(code: Int): String = when (code) {
        401 -> "Sesi berakhir, silakan login ulang"
        403 -> "Anda tidak memiliki akses untuk aksi ini"
        in 500..599 -> "Server sedang bermasalah"
        else -> "Permintaan gagal ($code)"
    }
}
