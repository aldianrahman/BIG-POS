package com.berdikariintigemilang.pos.data.repository

import com.berdikariintigemilang.pos.core.network.ApiResult
import com.berdikariintigemilang.pos.core.network.safePosCall
import com.berdikariintigemilang.pos.core.network.safePosCallNullable
import com.berdikariintigemilang.pos.data.local.CachedProductEntity
import com.berdikariintigemilang.pos.data.local.CatalogDao
import com.berdikariintigemilang.pos.data.remote.ApiService
import com.berdikariintigemilang.pos.data.remote.PageDto
import com.berdikariintigemilang.pos.data.remote.ProductDto
import com.berdikariintigemilang.pos.data.remote.ProductRequest
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Akses produk dengan fallback OFFLINE: bila server tak terjangkau, pencarian
 * & scan barcode dilayani dari cache lokal (Room), dengan stok diambil dari
 * stok lokal agar konsisten dengan penjualan offline yang sudah terjadi.
 */
@Singleton
class ProductRepository @Inject constructor(
    private val api: ApiService,
    private val catalogDao: CatalogDao,
    private val localStock: LocalStockRepository
) {
    suspend fun search(query: String?, page: Int, size: Int = 20): ApiResult<PageDto<ProductDto>> = try {
        val res = api.searchProducts(query, null, page, size)
        if (res.success && res.data != null) ApiResult.Success(res.data)
        else cacheSearch(query) ?: ApiResult.Error(res.error?.message ?: res.message ?: "Gagal memuat produk")
    } catch (e: IOException) {
        cacheSearch(query) ?: ApiResult.Error("Koneksi bermasalah. Periksa jaringan Anda.")
    } catch (e: HttpException) {
        ApiResult.Error(mapHttp(e.code()), httpStatus = e.code())
    } catch (e: Exception) {
        cacheSearch(query) ?: ApiResult.Error(e.message ?: "Gagal memuat produk")
    }

    suspend fun byBarcode(barcode: String): ApiResult<ProductDto> = try {
        val res = api.productByBarcode(barcode)
        if (res.success && res.data != null) ApiResult.Success(res.data)
        else cacheBarcode(barcode) ?: ApiResult.Error(res.error?.message ?: "Produk tidak ditemukan", httpStatus = 404)
    } catch (e: IOException) {
        cacheBarcode(barcode) ?: ApiResult.Error("Koneksi bermasalah. Periksa jaringan Anda.")
    } catch (e: HttpException) {
        if (e.code() == 404) cacheBarcode(barcode) ?: ApiResult.Error("Data tidak ditemukan", httpStatus = 404)
        else ApiResult.Error(mapHttp(e.code()), httpStatus = e.code())
    } catch (e: Exception) {
        cacheBarcode(barcode) ?: ApiResult.Error(e.message ?: "Gagal")
    }

    suspend fun getById(id: Long): ApiResult<ProductDto> = try {
        val res = api.product(id)
        if (res.success && res.data != null) ApiResult.Success(res.data)
        else cacheById(id) ?: ApiResult.Error(res.error?.message ?: "Data tidak ditemukan", httpStatus = 404)
    } catch (e: IOException) {
        cacheById(id) ?: ApiResult.Error("Koneksi bermasalah. Periksa jaringan Anda.")
    } catch (e: HttpException) {
        ApiResult.Error(mapHttp(e.code()), httpStatus = e.code())
    } catch (e: Exception) {
        cacheById(id) ?: ApiResult.Error(e.message ?: "Gagal")
    }

    suspend fun create(request: ProductRequest): ApiResult<ProductDto> =
        safePosCall { api.createProduct(request) }

    suspend fun update(id: Long, request: ProductRequest): ApiResult<ProductDto> =
        safePosCall { api.updateProduct(id, request) }

    /** Soft delete; body data null sehingga pakai nullable. */
    suspend fun delete(id: Long): ApiResult<ProductDto?> =
        safePosCallNullable { api.deleteProduct(id) }

    // ---- Fallback cache lokal ----

    private suspend fun cacheSearch(query: String?): ApiResult.Success<PageDto<ProductDto>>? {
        // Cache kosong: biarkan menjadi error agar pengguna tahu katalog belum diunduh.
        if (catalogDao.productCount() == 0) return null
        val q = query?.trim().orEmpty()
        val dtos = catalogDao.searchProducts(q, limit = 100, offset = 0).map { it.toDto() }
        return ApiResult.Success(
            PageDto(
                content = dtos,
                page = 0,
                size = dtos.size.coerceAtLeast(1),
                totalElements = dtos.size.toLong(),
                totalPages = 1
            )
        )
    }

    private suspend fun cacheBarcode(barcode: String): ApiResult.Success<ProductDto>? =
        catalogDao.productByBarcode(barcode)?.let { ApiResult.Success(it.toDto()) }

    private suspend fun cacheById(id: Long): ApiResult.Success<ProductDto>? =
        catalogDao.productById(id)?.let { ApiResult.Success(it.toDto()) }

    private suspend fun CachedProductEntity.toDto(): ProductDto = ProductDto(
        id = id,
        sku = sku,
        barcode = barcode,
        name = name,
        categoryId = categoryId,
        categoryName = categoryName,
        brand = brand,
        unit = unit,
        purchasePrice = purchasePrice,
        sellingPrice = sellingPrice,
        isActive = isActive,
        stockQuantity = localStock.available(id),
        minStock = minStock
    )

    private fun mapHttp(code: Int): String = when (code) {
        401 -> "Sesi berakhir, silakan login ulang"
        403 -> "Anda tidak memiliki akses untuk aksi ini"
        404 -> "Data tidak ditemukan"
        in 500..599 -> "Server sedang bermasalah"
        else -> "Permintaan gagal ($code)"
    }
}
