package com.berdikariintigemilang.pos.data.repository

import com.berdikariintigemilang.pos.core.network.ApiResult
import com.berdikariintigemilang.pos.core.network.safePosCall
import com.berdikariintigemilang.pos.data.remote.ApiService
import com.berdikariintigemilang.pos.data.remote.ReceiptDto
import com.berdikariintigemilang.pos.data.remote.TransactionDto
import com.berdikariintigemilang.pos.data.remote.TransactionRequest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransactionRepository @Inject constructor(
    private val api: ApiService
) {
    suspend fun create(idempotencyKey: String, request: TransactionRequest): ApiResult<TransactionDto> =
        safePosCall { api.createTransaction(idempotencyKey, request) }

    suspend fun receipt(id: Long): ApiResult<ReceiptDto> =
        safePosCall { api.receipt(id) }
}
