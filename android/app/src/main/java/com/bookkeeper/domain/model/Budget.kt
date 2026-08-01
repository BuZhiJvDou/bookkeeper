package com.bookkeeper.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class BudgetPeriod {
    MONTHLY, WEEKLY, YEARLY
}

@Serializable
data class Budget(
    val id: Long = 0,
    val categoryId: Long? = null,
    val amount: Long,
    val period: BudgetPeriod,
    val startDate: Long,
    val isDeleted: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)
