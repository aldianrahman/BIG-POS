package com.berdikariintigemilang.pos.data.repository

import com.berdikariintigemilang.pos.core.network.ApiResult
import com.berdikariintigemilang.pos.core.network.safePosCall
import com.berdikariintigemilang.pos.core.network.safePosCallNullable
import com.berdikariintigemilang.pos.data.remote.ApiService
import com.berdikariintigemilang.pos.data.remote.PageDto
import com.berdikariintigemilang.pos.data.remote.ProductDto
import com.berdikariintigemilang.pos.data.remote.ProductRequest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProductRepository @Inject constructor(
    private val api: ApiService
) {
    suspend fun search(query: String?, page: Int, size: Int = 20): ApiResult<PageDto<ProductDto>> =
        safePosCall { api.searchProducts(query, null, page, size) }

    suspend fun byBarcode(barcode: String): ApiResult<ProductDto> =
        safePosCall { api.productByBarcode(barcode) }

    suspend fun getById(id: Long): ApiResult<ProductDto> =
        safePosCall { api.product(id) }

    suspend fun create(request: ProductRequest): ApiResult<ProductDto> =
        safePosCall { api.createProduct(request) }

    suspend fun update(id: Long, request: ProductRequest): ApiResult<ProductDto> =
        safePosCall { api.updateProduct(id, request) }

    /** Soft delete; body data null sehingga pakai nullable. */
    suspend fun delete(id: Long): ApiResult<ProductDto?> =
        safePosCallNullable { api.deleteProduct(id) }
}
