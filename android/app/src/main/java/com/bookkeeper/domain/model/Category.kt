package com.bookkeeper.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Category(
    val id: Long = 0,
    val name: String,
    val type: TransactionType,
    val icon: String,
    val color: String,
    val parentId: Long? = null,
    val sortOrder: Int = 0,
    val isSystem: Boolean = false,
    val isDeleted: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)
