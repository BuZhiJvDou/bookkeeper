package com.bookkeeper.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class TransactionType {
    INCOME, EXPENSE, TRANSFER
}

@Serializable
data class Transaction(
    val id: Long = 0,
    val type: TransactionType,
    val amount: Long,           // 单位：分
    val categoryId: Long,
    val accountId: Long,
    val toAccountId: Long? = null,
    val note: String? = null,
    val tags: List<String> = emptyList(),
    val date: Long,             // Unix 时间戳（毫秒）
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)

// 显示用扩展
fun Transaction.displayAmount(): String {
    val yuan = amount / 100.0
    return String.format("%.2f", yuan)
}

fun Transaction.isIncome() = type == TransactionType.INCOME
fun Transaction.isExpense() = type == TransactionType.EXPENSE
fun Transaction.isTransfer() = type == TransactionType.TRANSFER
