package com.berdikariintigemilang.pos.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

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

    // ===== Products =====
    @GET("api/pos/products")
    suspend fun searchProducts(
        @Query("search") search: String?,
        @Query("categoryId") categoryId: Long?,
        @Query("page") page: Int,
        @Query("size") size: Int
    ): PosApiResponse<PageDto<ProductDto>>

    @GET("api/pos/products/barcode/{barcode}")
    suspend fun productByBarcode(@Path("barcode") barcode: String): PosApiResponse<ProductDto>

    // ===== Transactions =====
    @POST("api/pos/transactions")
    suspend fun createTransaction(
        @Header("X-Idempotency-Key") idempotencyKey: String,
        @Body body: TransactionRequest
    ): PosApiResponse<TransactionDto>

    @GET("api/pos/transactions/{id}/receipt")
    suspend fun receipt(@Path("id") id: Long): PosApiResponse<ReceiptDto>
}
