package com.berdikariintigemilang.pos.data.repository

import com.berdikariintigemilang.pos.data.local.LocalStockDao
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** Akses stok lokal: dipakai untuk validasi keranjang & pengurangan saat penjualan offline. */
@Singleton
class LocalStockRepository @Inject constructor(
    private val dao: LocalStockDao
) {
    /** Sisa stok lokal produk, atau null bila produk belum ada di cache stok. */
    suspend fun available(productId: Long): Int? = dao.getByProductId(productId)?.quantity

    fun observeAvailable(productId: Long): Flow<Int?> = dao.observeQuantity(productId)

    suspend fun hasStockData(): Boolean = dao.count() > 0

    suspend fun decrement(productId: Long, qty: Int) = dao.decrement(productId, qty, System.currentTimeMillis())

    /** Kembalikan stok (mis. saat transaksi offline dibatalkan sebelum sync). */
    suspend fun increment(productId: Long, qty: Int) = dao.increment(productId, qty, System.currentTimeMillis())
}
