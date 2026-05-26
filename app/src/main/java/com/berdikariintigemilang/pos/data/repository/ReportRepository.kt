package com.berdikariintigemilang.pos.data.repository

import com.berdikariintigemilang.pos.core.network.ApiResult
import com.berdikariintigemilang.pos.core.network.safePosCall
import com.berdikariintigemilang.pos.data.remote.ApiService
import com.berdikariintigemilang.pos.data.remote.ProfitReportDto
import com.berdikariintigemilang.pos.data.remote.SalesReportRowDto
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReportRepository @Inject constructor(
    private val api: ApiService
) {
    suspend fun sales(from: String, to: String, groupBy: String): ApiResult<List<SalesReportRowDto>> =
        safePosCall { api.salesReport(from, to, groupBy) }

    suspend fun profit(from: String, to: String): ApiResult<ProfitReportDto> =
        safePosCall { api.profitReport(from, to) }
}
