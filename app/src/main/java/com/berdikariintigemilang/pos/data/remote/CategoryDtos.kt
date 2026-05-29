package com.berdikariintigemilang.pos.data.remote

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class CategoryDto(
    val id: Long = 0,
    val name: String = "",
    val parentId: Long? = null,
    val sortOrder: Int = 0,
    val isActive: Boolean = true,
    val children: List<CategoryDto> = emptyList()
)
