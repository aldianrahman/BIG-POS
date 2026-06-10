package com.berdikariintigemilang.pos.core.di

import android.content.Context
import androidx.room.Room
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

    @Provides
    @Singleton
    fun providePosDatabase(@ApplicationContext context: Context): PosDatabase =
        Room.databaseBuilder(context, PosDatabase::class.java, "pos_offline.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun providePendingTransactionDao(db: PosDatabase): PendingTransactionDao = db.pendingTransactionDao()

    @Provides
    fun provideLocalStockDao(db: PosDatabase): LocalStockDao = db.localStockDao()

    @Provides
    fun provideCatalogDao(db: PosDatabase): CatalogDao = db.catalogDao()
}
