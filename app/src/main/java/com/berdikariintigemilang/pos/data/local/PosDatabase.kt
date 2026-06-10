package com.berdikariintigemilang.pos.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        PendingTransactionEntity::class,
        CachedProductEntity::class,
        LocalStockEntity::class,
        CachedBundleEntity::class,
        CachedBundleItemEntity::class,
        CachedReceiptSettingEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class PosDatabase : RoomDatabase() {
    abstract fun pendingTransactionDao(): PendingTransactionDao
    abstract fun localStockDao(): LocalStockDao
    abstract fun catalogDao(): CatalogDao
}
