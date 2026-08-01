package com.bookkeeper.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.bookkeeper.domain.model.RecurringPeriod
import com.bookkeeper.domain.model.RecurringRule
import com.bookkeeper.domain.model.TransactionType

@Entity(
    tableName = "recurring_rules",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"]
        ),
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"]
        )
    ],
    indices = [Index("categoryId"), Index("accountId"), Index("nextRun")]
)
data class RecurringRuleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val type: TransactionType,
    val amount: Long,
    val categoryId: Long,
    val accountId: Long,
    val note: String? = null,
    val period: RecurringPeriod,
    val nextRun: Long,
    val autoCreate: Boolean = true,
    val lastRun: Long? = null,
    val isDeleted: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long = 0L
) {
    fun toDomain(): RecurringRule = RecurringRule(
        id = id,
        type = type,
        amount = amount,
        categoryId = categoryId,
        accountId = accountId,
        note = note,
        period = period,
        nextRun = nextRun,
        autoCreate = autoCreate,
        lastRun = lastRun,
        isDeleted = isDeleted,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    companion object {
        fun fromDomain(r: RecurringRule) = RecurringRuleEntity(
            id = r.id,
            type = r.type,
            amount = r.amount,
            categoryId = r.categoryId,
            accountId = r.accountId,
            note = r.note,
            period = r.period,
            nextRun = r.nextRun,
            autoCreate = r.autoCreate,
            lastRun = r.lastRun,
            isDeleted = r.isDeleted,
            createdAt = r.createdAt,
            updatedAt = r.updatedAt
        )
    }
}
