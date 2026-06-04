package com.berdikariintigemilang.pos.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity Room untuk mode offline-first.
 *
 * Prinsip:
 * - Transaksi tidak pernah hilang: setiap penjualan ditulis ke
 *   [PendingTransactionEntity] SEBELUM dikirim ke server.
 * - Saat offline, HP adalah sumber kebenaran untuk transaksi & stok.
 * - Katalog/stok/bundle/setting di-cache agar pencarian, scan barcode,
 *   serta perhitungan total (bundle + PPN) bisa jalan tanpa sinyal.
 */

/** Status sinkronisasi sebuah transaksi lokal. */
enum class SyncStatus { PENDING, SYNCED, FAILED, CONFLICT }

/**
 * Antrian transaksi lokal. [clientTxnId] = UUID yang dibuat sekali saat
 * transaksi disimpan, dipakai sebagai idempotency key di SEMUA percobaan
 * kirim sehingga server tidak pernah mencatat dobel.
 */
@Entity(tableName = "pending_transactions")
data class PendingTransactionEntity(
    @PrimaryKey val clientTxnId: String,
    /** JSON dari List<TransactionItemRequest> (productId, quantity, discountAmount). */
    val itemsJson: String,
    val discountAmount: Double,
    val cashReceived: Double,
    val notes: String?,
    // Snapshot perhitungan saat penjualan (untuk struk & tampilan, dihitung lokal).
    val subtotal: Double,
    val bundleDiscount: Double,
    val taxAmount: Double,
    val totalAmount: Double,
    val changeAmount: Double,
    /** Nomor struk sementara yang dicetak offline (mis. "OFFLINE/20260530/0001"). */
    val offlineTrxNo: String,
    /** Teks struk siap cetak (sudah diformat 32 kolom seperti server). */
    val receiptContent: String,
    val cashierName: String?,
    /** Waktu jual sebenarnya (epoch millis, jam HP) — bukan waktu sync. */
    val createdAt: Long,
    val status: String = SyncStatus.PENDING.name,
    val attemptCount: Int = 0,
    val lastError: String? = null,
    // Diisi setelah sync sukses.
    val serverId: Long? = null,
    val serverTrxNo: String? = null,
    val syncedAt: Long? = null
)

/** Snapshot katalog produk untuk pencarian & scan barcode offline. */
@Entity(tableName = "cached_products")
data class CachedProductEntity(
    @PrimaryKey val id: Long,
    val sku: String,
    val barcode: String?,
    val name: String,
    val categoryId: Long,
    val categoryName: String?,
    val brand: String,
    val unit: String,
    val purchasePrice: Double,
    val sellingPrice: Double,
    val isActive: Boolean,
    val minStock: Int?
)

/**
 * Stok lokal — dikurangi langsung tiap penjualan offline agar penjualan
 * berikutnya melihat sisa yang benar. [serverQuantity] = angka terakhir
 * dari server saat cache di-refresh (untuk rekonsiliasi/diagnosa).
 */
@Entity(tableName = "local_stock")
data class LocalStockEntity(
    @PrimaryKey val productId: Long,
    val quantity: Int,
    val minStock: Int,
    val serverQuantity: Int,
    val updatedAt: Long
)

/** Definisi bundle (promo) untuk perhitungan diskon offline. */
@Entity(tableName = "cached_bundles")
data class CachedBundleEntity(
    @PrimaryKey val id: Long,
    val name: String,
    val bundlePrice: Double,
    val isActive: Boolean,
    /** ISO date "yyyy-MM-dd" atau null. */
    val startDate: String?,
    val endDate: String?
)

/** Item penyusun bundle (produk + qty yang dibutuhkan per satu bundle). */
@Entity(tableName = "cached_bundle_items", primaryKeys = ["bundleId", "productId"])
data class CachedBundleItemEntity(
    val bundleId: Long,
    val productId: Long,
    val quantity: Int
)

/** Pengaturan struk (header + PPN) untuk format struk & hitung pajak offline. Satu baris (id=0). */
@Entity(tableName = "cached_receipt_setting")
data class CachedReceiptSettingEntity(
    @PrimaryKey val id: Int = 0,
    val storeName: String,
    val address: String,
    val phone: String,
    val footer: String,
    val taxEnabled: Boolean,
    val taxPercent: Double,
    val taxInclusive: Boolean
)
