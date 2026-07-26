package com.bookkeeper.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.bookkeeper.domain.model.Budget
import com.bookkeeper.domain.model.BudgetPeriod

@Entity(
    tableName = "budgets",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"]
        )
    ],
    indices = [Index("categoryId")]
)
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val categoryId: Long? = null,
    val amount: Long,
    val period: BudgetPeriod,
    val startDate: Long,
    val isDeleted: Boolean = false
) {
    fun toDomain(): Budget = Budget(
        id = id,
        categoryId = categoryId,
        amount = amount,
        period = period,
        startDate = startDate,
        isDeleted = isDeleted
    )

    companion object {
        fun fromDomain(b: Budget) = BudgetEntity(
            id = b.id,
            categoryId = b.categoryId,
            amount = b.amount,
            period = b.period,
            startDate = b.startDate,
            isDeleted = b.isDeleted
        )
    }
}
