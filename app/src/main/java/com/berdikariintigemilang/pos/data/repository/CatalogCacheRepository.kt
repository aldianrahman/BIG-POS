package com.berdikariintigemilang.pos.data.repository

import com.berdikariintigemilang.pos.core.network.ApiResult
import com.berdikariintigemilang.pos.data.local.CachedBundleEntity
import com.berdikariintigemilang.pos.data.local.CachedBundleItemEntity
import com.berdikariintigemilang.pos.data.local.CachedProductEntity
import com.berdikariintigemilang.pos.data.local.CachedReceiptSettingEntity
import com.berdikariintigemilang.pos.data.local.CatalogDao
import com.berdikariintigemilang.pos.data.local.LocalStockDao
import com.berdikariintigemilang.pos.data.local.LocalStockEntity
import com.berdikariintigemilang.pos.data.remote.ApiService
import com.berdikariintigemilang.pos.data.remote.ProductDto
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Menarik katalog (produk, stok, bundle, pengaturan struk) dari server ke
 * cache lokal Room agar POS bisa beroperasi penuh tanpa sinyal.
 *
 * Dipanggil saat ada koneksi (mis. sebelum festival / buka shift / tombol
 * "Perbarui katalog"). Stok lokal SENGAJA tidak ditimpa bila sudah ada
 * (kecuali [resetStock]=true) agar pengurangan stok offline tidak hilang.
 */
@Singleton
class CatalogCacheRepository @Inject constructor(
    private val api: ApiService,
    private val catalogDao: CatalogDao,
    private val localStockDao: LocalStockDao
) {

    suspend fun refreshAll(resetStock: Boolean = false): ApiResult<Unit> = try {
        cacheProducts()
        cacheStock(resetStock)
        cacheBundles()
        cacheReceiptSetting()
        ApiResult.Success(Unit)
    } catch (e: HttpException) {
        ApiResult.Error(mapHttp(e.code()), httpStatus = e.code())
    } catch (e: IOException) {
        ApiResult.Error("Koneksi bermasalah. Periksa jaringan Anda.")
    } catch (e: Exception) {
        ApiResult.Error(e.message ?: "Gagal memuat katalog")
    }

    /** Katalog sudah pernah diunduh? (penanda apakah aman beroperasi offline). */
    suspend fun isCatalogReady(): Boolean = catalogDao.productCount() > 0

    private suspend fun cacheProducts() {
        val all = mutableListOf<CachedProductEntity>()
        var page = 0
        val size = 200
        while (true) {
            val res = api.searchProducts(null, null, page, size)
            check(res.success) { res.error?.message ?: res.message ?: "Gagal memuat produk" }
            val data = res.data ?: break
            all += data.content.map { it.toCached() }
            if (data.content.isEmpty() || page >= data.totalPages - 1) break
            page++
        }
        catalogDao.clearProducts()
        catalogDao.upsertProducts(all)
    }

    private suspend fun cacheStock(reset: Boolean) {
        val res = api.stocks(null, false)
        check(res.success) { res.error?.message ?: res.message ?: "Gagal memuat stok" }
        val server = res.data ?: emptyList()
        val now = System.currentTimeMillis()
        val existing = localStockDao.getAll().associateBy { it.productId }
        val rows = server.map { s ->
            val prev = existing[s.productId]
            // Pertahankan qty lokal (hasil penjualan offline) kecuali reset eksplisit.
            val qty = if (prev == null || reset) s.quantity else prev.quantity
            LocalStockEntity(
                productId = s.productId,
                quantity = qty,
                minStock = s.minStock,
                serverQuantity = s.quantity,
                updatedAt = now
            )
        }
        localStockDao.upsertAll(rows)
    }

    private suspend fun cacheBundles() {
        val res = api.bundles()
        check(res.success) { res.error?.message ?: res.message ?: "Gagal memuat bundle" }
        val bundles = res.data ?: emptyList()
        catalogDao.clearBundleItems()
        catalogDao.clearBundles()
        catalogDao.upsertBundles(
            bundles.map { CachedBundleEntity(it.id, it.name, it.bundlePrice, it.isActive, it.startDate, it.endDate) }
        )
        catalogDao.upsertBundleItems(
            bundles.flatMap { b -> b.items.map { CachedBundleItemEntity(b.id, it.productId, it.quantity) } }
        )
    }

    private suspend fun cacheReceiptSetting() {
        val res = api.receiptSetting()
        check(res.success) { res.error?.message ?: res.message ?: "Gagal memuat pengaturan struk" }
        val s = res.data ?: return
        catalogDao.upsertReceiptSetting(
            CachedReceiptSettingEntity(
                id = 0,
                storeName = s.storeName,
                address = s.address,
                phone = s.phone,
                footer = s.footer,
                taxEnabled = s.taxEnabled,
                taxPercent = s.taxPercent,
                taxInclusive = s.taxInclusive
            )
        )
    }

    private fun ProductDto.toCached() = CachedProductEntity(
        id = id,
        sku = sku,
        barcode = barcode,
        name = name,
        categoryId = categoryId,
        categoryName = categoryName,
        brand = brand,
        unit = unit,
        purchasePrice = purchasePrice,
        sellingPrice = sellingPrice,
        isActive = isActive,
        minStock = minStock
    )

    private fun mapHttp(code: Int): String = when (code) {
        401 -> "Sesi berakhir, silakan login ulang"
        403 -> "Anda tidak memiliki akses"
        in 500..599 -> "Server sedang bermasalah"
        else -> "Gagal memuat katalog ($code)"
    }
}
