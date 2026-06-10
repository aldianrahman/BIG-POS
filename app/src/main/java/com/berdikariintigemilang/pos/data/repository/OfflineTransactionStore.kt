package com.berdikariintigemilang.pos.data.repository

import com.berdikariintigemilang.pos.data.cart.CartLine
import com.berdikariintigemilang.pos.data.local.CatalogDao
import com.berdikariintigemilang.pos.data.local.LocalStockDao
import com.berdikariintigemilang.pos.data.local.PendingTransactionDao
import com.berdikariintigemilang.pos.data.local.PendingTransactionEntity
import com.berdikariintigemilang.pos.data.local.SyncStatus
import com.berdikariintigemilang.pos.data.pricing.LocalPricingCalculator
import com.berdikariintigemilang.pos.data.pricing.OfflineReceiptComposer
import com.berdikariintigemilang.pos.data.pricing.ReceiptData
import com.berdikariintigemilang.pos.data.pricing.ReceiptLine
import com.berdikariintigemilang.pos.data.remote.TransactionItemRequest
import com.berdikariintigemilang.pos.data.remote.TransactionRequest
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.flow.Flow
import java.lang.reflect.Type
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sumber kebenaran transaksi sisi HP. Setiap penjualan ditulis di sini SEBELUM
 * dikirim ke server, dengan [PendingTransactionEntity.clientTxnId] (UUID) yang
 * dipakai sebagai idempotency key di semua percobaan kirim — sehingga retry
 * tidak pernah membuat transaksi dobel.
 */
/** Agregat penjualan per produk dari data lokal (untuk dashboard offline). */
data class LocalProductAgg(
    val productId: Long,
    val name: String,
    val sku: String,
    val quantitySold: Int,
    val totalSales: Double
)

/** Ringkasan penjualan lokal (untuk dashboard realtime/offline). */
data class LocalSalesAgg(
    val totalSales: Double = 0.0,
    val totalProfit: Double = 0.0,
    val count: Int = 0,
    val products: List<LocalProductAgg> = emptyList()
)

@Singleton
class OfflineTransactionStore @Inject constructor(
    private val pendingDao: PendingTransactionDao,
    private val localStockDao: LocalStockDao,
    private val catalogDao: CatalogDao,
    private val pricing: LocalPricingCalculator,
    private val receiptComposer: OfflineReceiptComposer,
    moshi: Moshi
) {
    private val itemsType: Type = Types.newParameterizedType(List::class.java, TransactionItemRequest::class.java)
    private val itemsAdapter: JsonAdapter<List<TransactionItemRequest>> = moshi.adapter(itemsType)
    private val noFmt = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmssSSS")

    fun observeAll(): Flow<List<PendingTransactionEntity>> = pendingDao.observeAll()
    fun observeUnsyncedCount(): Flow<Int> = pendingDao.observeUnsyncedCount()
    suspend fun all(): List<PendingTransactionEntity> = pendingDao.getAllOnce()
    suspend fun getById(clientTxnId: String): PendingTransactionEntity? = pendingDao.getById(clientTxnId)
    suspend fun pendingForSync(): List<PendingTransactionEntity> = pendingDao.pendingForSync()

    /**
     * Bersihkan transaksi tersinkron yang sudah lebih tua dari [retentionMillis]
     * (default 2 hari). Transaksi ini sudah aman di server, sehingga salinan
     * lokalnya tak perlu disimpan selamanya — mencegah "data hantu" menumpuk.
     * Transaksi hari ini tetap dipertahankan (untuk cetak ulang struk).
     */
    suspend fun purgeOldSynced(retentionMillis: Long = 2 * 24 * 60 * 60 * 1000L): Int =
        pendingDao.deleteSyncedOlderThan(System.currentTimeMillis() - retentionMillis)

    /**
     * Simpan transaksi ke antrian lokal (PENDING) + kurangi stok lokal.
     * Mengembalikan entity tersimpan (berisi struk siap cetak & nomor sementara).
     */
    suspend fun enqueue(
        lines: List<CartLine>,
        discount: Double,
        cashReceived: Double,
        notes: String?,
        cashierName: String?
    ): PendingTransactionEntity {
        val now = System.currentTimeMillis()
        val result = pricing.price(lines, discount)
        val change = (cashReceived - result.total).coerceAtLeast(0.0)
        val offlineNo = "OFFLINE/" +
            LocalDateTime.ofInstant(Instant.ofEpochMilli(now), ZoneId.systemDefault()).format(noFmt)
        // Harga sales yang lebih murah dikirim ke server sebagai diskon per item:
        // server menghitung lineSubtotal = hargaMaster*qty - discountAmount, sehingga
        // total yang dihitung ulang server sama persis dengan struk yang dicetak.
        // priceEditedBy = id sales yang menurunkan harga (untuk log harga di web admin).
        val items = lines.map {
            val lineDiscount = ((it.masterPrice - it.unitPrice) * it.quantity).coerceAtLeast(0.0)
            TransactionItemRequest(
                productId = it.productId,
                quantity = it.quantity,
                discountAmount = lineDiscount,
                priceEditedBy = if (it.isPriceEdited) it.priceEditedByUserId else null
            )
        }
        val setting = catalogDao.receiptSetting()
        val receipt = receiptComposer.compose(
            ReceiptData(
                trxNo = offlineNo,
                dateMillis = now,
                cashierName = cashierName,
                lines = lines.map { ReceiptLine(it.name, it.quantity, it.unitPrice, it.lineSubtotal) },
                subtotal = result.subtotal,
                discount = result.discount,
                bundleDiscount = result.bundleDiscount,
                appliedBundles = result.appliedBundles,
                taxAmount = result.taxAmount,
                taxInclusive = result.taxInclusive,
                total = result.total,
                cashReceived = cashReceived,
                change = change,
                pendingSync = false
            ),
            setting
        )
        val entity = PendingTransactionEntity(
            clientTxnId = UUID.randomUUID().toString(),
            itemsJson = itemsAdapter.toJson(items),
            discountAmount = discount,
            cashReceived = cashReceived,
            notes = notes,
            subtotal = result.subtotal,
            bundleDiscount = result.bundleDiscount,
            taxAmount = result.taxAmount,
            totalAmount = result.total,
            changeAmount = change,
            offlineTrxNo = offlineNo,
            receiptContent = receipt,
            cashierName = cashierName,
            createdAt = now,
            status = SyncStatus.PENDING.name
        )
        pendingDao.insert(entity)
        // Kurangi stok lokal (gabung qty per produk).
        val merged = HashMap<Long, Int>()
        lines.forEach { merged.merge(it.productId, it.quantity, Int::plus) }
        merged.forEach { (productId, qty) -> localStockDao.decrement(productId, qty, now) }
        return entity
    }

    /**
     * Bentuk ulang request untuk dikirim ke server saat sinkronisasi, membawa
     * waktu jual asli (agar laporan akurat) dan allowNegativeStock=true (server
     * menerima penjualan offline yang sudah terjadi walau stok kurang).
     */
    fun toRequest(e: PendingTransactionEntity): TransactionRequest {
        val items = itemsAdapter.fromJson(e.itemsJson) ?: emptyList()
        return TransactionRequest(
            items = items,
            discountAmount = e.discountAmount,
            cashReceived = e.cashReceived,
            notes = e.notes,
            clientCreatedAt = isoLocal(e.createdAt),
            allowNegativeStock = true
        )
    }

    /** Epoch millis -> ISO-8601 lokal (tanpa zona), sesuai LocalDateTime server. */
    private fun isoLocal(millis: Long): String =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault())
            .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

    /**
     * Total qty terjual pada transaksi yang BELUM tersinkron, per produk.
     * Dipakai untuk menghitung stok lokal = stok server − penjualan belum tersinkron,
     * sehingga refresh dari server tidak menghilangkan pengurangan stok offline.
     */
    suspend fun unsyncedSoldQuantities(): Map<Long, Int> {
        val map = HashMap<Long, Int>()
        pendingDao.getAllOnce()
            .filter { it.status != SyncStatus.SYNCED.name }
            .forEach { e ->
                (itemsAdapter.fromJson(e.itemsJson) ?: emptyList()).forEach { item ->
                    map.merge(item.productId, item.quantity, Int::plus)
                }
            }
        return map
    }

    /**
     * Agregasi penjualan lokal sejak [sinceMillis] untuk dashboard.
     * [includeSynced]=false hanya menghitung transaksi yang belum tersinkron
     * (dipakai sebagai overlay saat online); =true menghitung semua transaksi
     * lokal (dipakai saat offline). Laba ≈ (subtotal − diskon − bundle) − HPP.
     */
    suspend fun aggregateLocalSales(sinceMillis: Long, includeSynced: Boolean): LocalSalesAgg {
        val txns = pendingDao.getAllOnce().filter {
            it.createdAt >= sinceMillis && (includeSynced || it.status != SyncStatus.SYNCED.name)
        }
        if (txns.isEmpty()) return LocalSalesAgg()
        var totalSales = 0.0
        var netRevenue = 0.0
        val qtyById = HashMap<Long, Int>()
        txns.forEach { e ->
            totalSales += e.totalAmount
            netRevenue += (e.subtotal - e.discountAmount - e.bundleDiscount)
            (itemsAdapter.fromJson(e.itemsJson) ?: emptyList()).forEach { item ->
                qtyById.merge(item.productId, item.quantity, Int::plus)
            }
        }
        val products = catalogDao.productsByIds(qtyById.keys.toList()).associateBy { it.id }
        var cost = 0.0
        val productAggs = qtyById.map { (pid, qty) ->
            val p = products[pid]
            cost += (p?.purchasePrice ?: 0.0) * qty
            LocalProductAgg(
                productId = pid,
                name = p?.name ?: "Produk #$pid",
                sku = p?.sku ?: "",
                quantitySold = qty,
                totalSales = (p?.sellingPrice ?: 0.0) * qty
            )
        }.sortedByDescending { it.quantitySold }
        return LocalSalesAgg(
            totalSales = totalSales,
            totalProfit = netRevenue - cost,
            count = txns.size,
            products = productAggs
        )
    }

    suspend fun markSynced(clientTxnId: String, serverId: Long, serverTrxNo: String, warning: String? = null) {
        val e = pendingDao.getById(clientTxnId) ?: return
        pendingDao.update(
            e.copy(
                status = SyncStatus.SYNCED.name,
                serverId = serverId,
                serverTrxNo = serverTrxNo,
                syncedAt = System.currentTimeMillis(),
                // Simpan peringatan stok-minus dari server (bila ada) untuk ditinjau staf.
                lastError = warning
            )
        )
    }

    /** Gagal sementara (jaringan/server) — tetap di antrian untuk dicoba lagi. */
    suspend fun markRetry(clientTxnId: String, error: String?) {
        val e = pendingDao.getById(clientTxnId) ?: return
        pendingDao.update(e.copy(status = SyncStatus.FAILED.name, attemptCount = e.attemptCount + 1, lastError = error))
    }

    /** Ditolak server secara permanen — butuh perhatian manual (tidak di-retry otomatis). */
    suspend fun markConflict(clientTxnId: String, error: String?) {
        val e = pendingDao.getById(clientTxnId) ?: return
        pendingDao.update(e.copy(status = SyncStatus.CONFLICT.name, attemptCount = e.attemptCount + 1, lastError = error))
    }
}
