package com.berdikariintigemilang.pos.core.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.berdikariintigemilang.pos.data.local.CatalogDao
import com.berdikariintigemilang.pos.data.local.LocalStockDao
import com.berdikariintigemilang.pos.data.local.PendingTransactionDao
import com.berdikariintigemilang.pos.data.local.PosDatabase
import com.berdikariintigemilang.pos.data.local.PriceEditLogDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object LocalModule {

    /**
     * v1 -> v2: tambah tabel `price_edit_logs` (log perubahan harga sales vs
     * master). Migrasi aditif agar antrian transaksi yang BELUM tersinkron tidak
     * ikut terhapus saat update aplikasi (berbeda dengan fallback destruktif).
     */
    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `price_edit_logs` (" +
                    "`id` TEXT NOT NULL, " +
                    "`productId` INTEGER NOT NULL, " +
                    "`productName` TEXT NOT NULL, " +
                    "`sku` TEXT NOT NULL, " +
                    "`masterPrice` REAL NOT NULL, " +
                    "`newPrice` REAL NOT NULL, " +
                    "`quantity` INTEGER NOT NULL, " +
                    "`editedByUserId` INTEGER NOT NULL, " +
                    "`editedByName` TEXT NOT NULL, " +
                    "`cashierUserId` INTEGER, " +
                    "`cashierName` TEXT, " +
                    "`clientTxnId` TEXT NOT NULL, " +
                    "`offlineTrxNo` TEXT NOT NULL, " +
                    "`serverTrxNo` TEXT, " +
                    "`createdAt` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`id`))"
            )
        }
    }

    @Provides
    @Singleton
    fun providePosDatabase(@ApplicationContext context: Context): PosDatabase =
        Room.databaseBuilder(context, PosDatabase::class.java, "pos_offline.db")
            .addMigrations(MIGRATION_1_2)
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun providePendingTransactionDao(db: PosDatabase): PendingTransactionDao = db.pendingTransactionDao()

    @Provides
    fun provideLocalStockDao(db: PosDatabase): LocalStockDao = db.localStockDao()

    @Provides
    fun provideCatalogDao(db: PosDatabase): CatalogDao = db.catalogDao()

    @Provides
    fun providePriceEditLogDao(db: PosDatabase): PriceEditLogDao = db.priceEditLogDao()
}
