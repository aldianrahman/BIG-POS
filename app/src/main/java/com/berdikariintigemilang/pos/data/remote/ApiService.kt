package com.berdikariintigemilang.pos.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface ApiService {

    // ===== Auth (format big-tracker) =====
    @POST("api/auth/signin")
    suspend fun signin(@Body body: LoginRequest): LoginResponse

    // ===== POS =====
    @GET("api/pos/me")
    suspend fun me(): PosApiResponse<MeDto>

    @POST("api/pos/shifts/open")
    suspend fun openShift(@Body body: OpenShiftRequest): PosApiResponse<ShiftDto>

    @GET("api/pos/shifts/current")
    suspend fun currentShift(): PosApiResponse<ShiftDto>

    @GET("api/pos/shifts/{id}")
    suspend fun shift(@Path("id") id: Long): PosApiResponse<ShiftDto>

    @POST("api/pos/shifts/{id}/close")
    suspend fun closeShift(@Path("id") id: Long, @Body body: CloseShiftRequest): PosApiResponse<ShiftDto>

    @GET("api/pos/shifts/{id}/z-report")
    suspend fun zReport(@Path("id") id: Long): PosApiResponse<ZReportDto>
}
