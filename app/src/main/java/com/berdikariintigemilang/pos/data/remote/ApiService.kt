package com.berdikariintigemilang.pos.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
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

    @GET("api/pos/products/{id}")
    suspend fun product(@Path("id") id: Long): PosApiResponse<ProductDto>

    @PUT("api/pos/products/{id}")
    suspend fun updateProduct(@Path("id") id: Long, @Body body: ProductRequest): PosApiResponse<ProductDto>

    // ===== Inventory =====
    @GET("api/pos/inventory/stocks")
    suspend fun stocks(
        @Query("search") search: String?,
        @Query("lowStock") lowStock: Boolean
    ): PosApiResponse<List<StockDto>>

    @POST("api/pos/inventory/adjustments")
    suspend fun adjustStock(@Body body: StockAdjustmentRequest): PosApiResponse<StockDto>

    // ===== Dashboard =====
    @GET("api/pos/dashboard/summary")
    suspend fun dashboardSummary(@Query("date") date: String?): PosApiResponse<DashboardSummaryDto>

    @GET("api/pos/dashboard/top-products")
    suspend fun topProducts(
        @Query("date") date: String?,
        @Query("limit") limit: Int
    ): PosApiResponse<List<TopProductDto>>

    @GET("api/pos/dashboard/low-stock-alerts")
    suspend fun lowStockAlerts(): PosApiResponse<List<StockDto>>

    // ===== Reports =====
    @GET("api/pos/reports/sales")
    suspend fun salesReport(
        @Query("from") from: String?,
        @Query("to") to: String?,
        @Query("groupBy") groupBy: String
    ): PosApiResponse<List<SalesReportRowDto>>

    @GET("api/pos/reports/profit")
    suspend fun profitReport(
        @Query("from") from: String?,
        @Query("to") to: String?
    ): PosApiResponse<ProfitReportDto>

    // ===== Transactions =====
    @POST("api/pos/transactions")
    suspend fun createTransaction(
        @Header("X-Idempotency-Key") idempotencyKey: String,
        @Body body: TransactionRequest
    ): PosApiResponse<TransactionDto>

    @GET("api/pos/transactions/{id}/receipt")
    suspend fun receipt(@Path("id") id: Long): PosApiResponse<ReceiptDto>
}
