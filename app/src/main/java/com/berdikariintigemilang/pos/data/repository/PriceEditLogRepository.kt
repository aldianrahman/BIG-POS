package com.berdikariintigemilang.pos.data.repository

import com.berdikariintigemilang.pos.data.local.PriceEditLogDao
import com.berdikariintigemilang.pos.data.local.PriceEditLogEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** Akses log perubahan harga sales vs harga master (untuk menu Log Ubah Harga). */
@Singleton
class PriceEditLogRepository @Inject constructor(
    private val dao: PriceEditLogDao
) {
    fun observeAll(): Flow<List<PriceEditLogEntity>> = dao.observeAll()
}
