package com.bookkeeper.domain.model

import kotlinx.serialization.Serializable

/** 循环记账周期 */
@Serializable
enum class RecurringPeriod { DAILY, WEEKLY, MONTHLY }

/**
 * 循环记账规则领域模型。
 * 到达 nextRun 时自动生成一条对应交易，并把 nextRun 推进一个周期。
 */
@Serializable
data class RecurringRule(
    val id: Long = 0,
    val type: TransactionType,      // INCOME / EXPENSE
    val amount: Long,               // 单位：分
    val categoryId: Long,
    val accountId: Long,
    val note: String? = null,
    val period: RecurringPeriod = RecurringPeriod.MONTHLY,
    val nextRun: Long,              // 下次执行时间戳（毫秒）
    val autoCreate: Boolean = true,
    val lastRun: Long? = null,
    val isDeleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
