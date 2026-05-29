package com.berdikariintigemilang.pos.data.repository

import com.berdikariintigemilang.pos.core.network.ApiResult
import com.berdikariintigemilang.pos.core.network.safePosCall
import com.berdikariintigemilang.pos.data.remote.ApiService
import com.berdikariintigemilang.pos.data.remote.ReceiptDto
import com.berdikariintigemilang.pos.data.remote.TransactionDto
import com.berdikariintigemilang.pos.data.remote.TransactionRequest
import com.berdikariintigemilang.pos.data.remote.TrxPageDto
import com.berdikariintigemilang.pos.data.remote.VoidRequest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransactionRepository @Inject constructor(
    private val api: ApiService
) {
    suspend fun create(idempotencyKey: String, request: TransactionRequest): ApiResult<TransactionDto> =
        safePosCall { api.createTransaction(idempotencyKey, request) }

    suspend fun list(from: String, to: String, page: Int, size: Int = 20): ApiResult<TrxPageDto<TransactionDto>> =
        safePosCall { api.transactions(from, to, page, size) }

    suspend fun void(id: Long, reason: String): ApiResult<TransactionDto> =
        safePosCall { api.voidTransaction(id, VoidRequest(reason)) }

    suspend fun receipt(id: Long): ApiResult<ReceiptDto> =
        safePosCall { api.receipt(id) }
}
