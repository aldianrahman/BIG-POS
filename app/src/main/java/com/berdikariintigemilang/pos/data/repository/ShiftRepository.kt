package com.berdikariintigemilang.pos.data.repository

import com.berdikariintigemilang.pos.core.network.ApiResult
import com.berdikariintigemilang.pos.core.network.safePosCall
import com.berdikariintigemilang.pos.core.network.safePosCallNullable
import com.berdikariintigemilang.pos.data.remote.ApiService
import com.berdikariintigemilang.pos.data.remote.CloseShiftRequest
import com.berdikariintigemilang.pos.data.remote.OpenShiftRequest
import com.berdikariintigemilang.pos.data.remote.ShiftDto
import com.berdikariintigemilang.pos.data.remote.ZReportDto
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShiftRepository @Inject constructor(
    private val api: ApiService
) {
    suspend fun current(): ApiResult<ShiftDto?> = safePosCallNullable { api.currentShift() }

    suspend fun open(openingCash: Double): ApiResult<ShiftDto> =
        safePosCall { api.openShift(OpenShiftRequest(openingCash)) }

    suspend fun get(id: Long): ApiResult<ShiftDto> = safePosCall { api.shift(id) }

    suspend fun close(id: Long, closingCash: Double, notes: String?): ApiResult<ShiftDto> =
        safePosCall { api.closeShift(id, CloseShiftRequest(closingCash, notes)) }

    suspend fun zReport(id: Long): ApiResult<ZReportDto> = safePosCall { api.zReport(id) }
}
