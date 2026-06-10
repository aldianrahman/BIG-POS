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
 * Stok lokal dihitung sebagai: stok server − penjualan yang BELUM tersinkron,
 * sehingga refresh dari server SELALU benar — memunculkan restock dari web
 * sekaligus mempertahankan pengurangan stok dari penjualan offline.
 */
@Singleton
class CatalogCacheRepository @Inject constructor(
    private val api: ApiService,
    private val catalogDao: CatalogDao,
    private val localStockDao: LocalStockDao,
    private val offlineStore: OfflineTransactionStore
) {

    /** Tarik seluruh katalog + stok (untuk persiapan offline / tombol manual). */
    suspend fun refreshAll(): ApiResult<Unit> = try {
        cacheProducts()
        cacheStock()
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

    /** Refresh ringan khusus stok (satu panggilan) — dipakai tiap kali online. */
    suspend fun refreshStock(): ApiResult<Unit> = try {
        cacheStock()
        ApiResult.Success(Unit)
    } catch (e: HttpException) {
        ApiResult.Error(mapHttp(e.code()), httpStatus = e.code())
    } catch (e: IOException) {
        ApiResult.Error("Koneksi bermasalah. Periksa jaringan Anda.")
    } catch (e: Exception) {
        ApiResult.Error(e.message ?: "Gagal memuat stok")
    }

    /**
     * Refresh ringan bundle + pengaturan struk — dipakai tiap kali online agar
     * promo/PPN yang baru dibuat/diedit di web langsung berlaku di kasir tanpa
     * perlu "Perbarui katalog" manual. Bila panggilan gagal, cache lama tetap
     * dipertahankan (clear baru dilakukan setelah respons sukses).
     */
    suspend fun refreshBundles(): ApiResult<Unit> = try {
        cacheBundles()
        cacheReceiptSetting()
        ApiResult.Success(Unit)
    } catch (e: HttpException) {
        ApiResult.Error(mapHttp(e.code()), httpStatus = e.code())
    } catch (e: IOException) {
        ApiResult.Error("Koneksi bermasalah. Periksa jaringan Anda.")
    } catch (e: Exception) {
        ApiResult.Error(e.message ?: "Gagal memuat bundle")
    }

    /**
     * Segarkan cache untuk produk yang baru diambil dari server (saat browsing
     * online), termasuk stoknya — supaya bila tiba-tiba offline, data terbaru
     * (mis. hasil restock di web) sudah tersimpan lokal.
     */
    suspend fun cacheServerProducts(products: List<ProductDto>) {
        if (products.isEmpty()) return
        catalogDao.upsertProducts(products.map { it.toCached() })
        val unsynced = offlineStore.unsyncedSoldQuantities()
        val now = System.currentTimeMillis()
        val rows = products.mapNotNull { p ->
            val serverQty = p.stockQuantity ?: return@mapNotNull null
            LocalStockEntity(
                productId = p.id,
                quantity = serverQty - (unsynced[p.id] ?: 0),
                minStock = p.minStock ?: 0,
                serverQuantity = serverQty,
                updatedAt = now
            )
        }
        if (rows.isNotEmpty()) localStockDao.upsertAll(rows)
    }

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

    private suspend fun cacheStock() {
        val res = api.stocks(null, false)
        check(res.success) { res.error?.message ?: res.message ?: "Gagal memuat stok" }
        val server = res.data ?: emptyList()
        val unsynced = offlineStore.unsyncedSoldQuantities()
        val now = System.currentTimeMillis()
        val rows = server.map { s ->
            LocalStockEntity(
                productId = s.productId,
                // Stok server dikurangi penjualan offline yang belum tersinkron.
                quantity = s.quantity - (unsynced[s.productId] ?: 0),
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
