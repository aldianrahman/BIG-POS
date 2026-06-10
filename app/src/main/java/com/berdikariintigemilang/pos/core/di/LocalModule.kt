package com.berdikariintigemilang.pos.core.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.berdikariintigemilang.pos.data.local.CatalogDao
import com.berdikariintigemilang.pos.data.local.LocalStockDao
import com.berdikariintigemilang.pos.data.local.PendingTransactionDao
import com.berdikariintigemilang.pos.data.local.PosDatabase
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
     * v1 -> v2: tambah kolom metode & referensi pembayaran tanpa menghapus data
     * (penting: transaksi offline yang belum tersinkron tidak boleh hilang).
     */
    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE pending_transactions ADD COLUMN paymentMethod TEXT NOT NULL DEFAULT 'CASH'")
            db.execSQL("ALTER TABLE pending_transactions ADD COLUMN paymentReference TEXT")
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
}
