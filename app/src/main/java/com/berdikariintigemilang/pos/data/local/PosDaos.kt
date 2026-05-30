package com.berdikariintigemilang.pos.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingTransactionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(txn: PendingTransactionEntity)

    @Update
    suspend fun update(txn: PendingTransactionEntity)

    @Query("SELECT * FROM pending_transactions WHERE clientTxnId = :id LIMIT 1")
    suspend fun getById(id: String): PendingTransactionEntity?

    /** Antrian yang masih perlu dikirim, urut waktu jual (FIFO). */
    @Query("SELECT * FROM pending_transactions WHERE status IN ('PENDING','FAILED') ORDER BY createdAt ASC")
    suspend fun pendingForSync(): List<PendingTransactionEntity>

    @Query("SELECT * FROM pending_transactions ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<PendingTransactionEntity>>

    @Query("SELECT * FROM pending_transactions ORDER BY createdAt DESC")
    suspend fun getAllOnce(): List<PendingTransactionEntity>

    /** Jumlah transaksi yang belum tersinkron (untuk indikator status). */
    @Query("SELECT COUNT(*) FROM pending_transactions WHERE status IN ('PENDING','FAILED')")
    fun observeUnsyncedCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM pending_transactions WHERE status = :status")
    suspend fun countByStatus(status: String): Int

    /**
     * Hapus transaksi yang SUDAH tersinkron & lebih lama dari [cutoff] (epoch
     * millis). Aman: data sudah tersimpan di server. Mencegah penumpukan
     * "data hantu" di lokal yang bisa mengacaukan tampilan offline.
     */
    @Query("DELETE FROM pending_transactions WHERE status = 'SYNCED' AND createdAt < :cutoff")
    suspend fun deleteSyncedOlderThan(cutoff: Long): Int
}

@Dao
interface LocalStockDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(stocks: List<LocalStockEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(stock: LocalStockEntity)

    @Query("SELECT * FROM local_stock WHERE productId = :productId LIMIT 1")
    suspend fun getByProductId(productId: Long): LocalStockEntity?

    @Query("SELECT * FROM local_stock")
    suspend fun getAll(): List<LocalStockEntity>

    @Query("UPDATE local_stock SET quantity = quantity - :qty, updatedAt = :now WHERE productId = :productId")
    suspend fun decrement(productId: Long, qty: Int, now: Long)

    @Query("UPDATE local_stock SET quantity = quantity + :qty, updatedAt = :now WHERE productId = :productId")
    suspend fun increment(productId: Long, qty: Int, now: Long)

    @Query("SELECT quantity FROM local_stock WHERE productId = :productId LIMIT 1")
    fun observeQuantity(productId: Long): Flow<Int?>

    @Query("SELECT COUNT(*) FROM local_stock")
    suspend fun count(): Int
}

@Dao
interface CatalogDao {

    // ---- Produk ----
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProducts(products: List<CachedProductEntity>)

    @Query("DELETE FROM cached_products")
    suspend fun clearProducts()

    @Query(
        "SELECT * FROM cached_products WHERE isActive = 1 AND (" +
            ":q = '' OR name LIKE '%' || :q || '%' OR sku LIKE '%' || :q || '%' OR barcode LIKE '%' || :q || '%'" +
            ") ORDER BY name LIMIT :limit OFFSET :offset"
    )
    suspend fun searchProducts(q: String, limit: Int, offset: Int): List<CachedProductEntity>

    @Query("SELECT * FROM cached_products WHERE barcode = :barcode AND isActive = 1 LIMIT 1")
    suspend fun productByBarcode(barcode: String): CachedProductEntity?

    @Query("SELECT * FROM cached_products WHERE id = :id LIMIT 1")
    suspend fun productById(id: Long): CachedProductEntity?

    @Query("SELECT * FROM cached_products WHERE id IN (:ids)")
    suspend fun productsByIds(ids: List<Long>): List<CachedProductEntity>

    @Query("SELECT COUNT(*) FROM cached_products")
    suspend fun productCount(): Int

    // ---- Bundle ----
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBundles(bundles: List<CachedBundleEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBundleItems(items: List<CachedBundleItemEntity>)

    @Query("DELETE FROM cached_bundles")
    suspend fun clearBundles()

    @Query("DELETE FROM cached_bundle_items")
    suspend fun clearBundleItems()

    @Query("SELECT * FROM cached_bundles WHERE isActive = 1")
    suspend fun activeBundles(): List<CachedBundleEntity>

    @Query("SELECT * FROM cached_bundle_items")
    suspend fun allBundleItems(): List<CachedBundleItemEntity>

    // ---- Pengaturan struk ----
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertReceiptSetting(setting: CachedReceiptSettingEntity)

    @Query("SELECT * FROM cached_receipt_setting WHERE id = 0 LIMIT 1")
    suspend fun receiptSetting(): CachedReceiptSettingEntity?
}
