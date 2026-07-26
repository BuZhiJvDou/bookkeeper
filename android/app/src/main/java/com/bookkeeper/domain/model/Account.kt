package com.bookkeeper.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class AccountType {
    CASH, BANK_CARD, ALIPAY, WECHAT, CREDIT_CARD, OTHER
}

@Serializable
data class Account(
    val id: Long = 0,
    val name: String,
    val type: AccountType,
    val icon: String,
    val color: String,
    val balance: Long = 0,      // 单位：分
    val currency: String = "CNY",
    val sortOrder: Int = 0,
    val isDeleted: Boolean = false
)
