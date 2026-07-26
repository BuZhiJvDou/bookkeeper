package com.bookkeeper.data.local.dao

import androidx.room.*
import com.bookkeeper.data.local.entity.BudgetEntity
import com.bookkeeper.domain.model.BudgetPeriod
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {

    @Query("SELECT * FROM budgets WHERE isDeleted = 0")
    fun getAllBudgets(): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets WHERE isDeleted = 0 AND period = :period")
    fun getBudgetsByPeriod(period: BudgetPeriod): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets WHERE categoryId = :categoryId AND isDeleted = 0 LIMIT 1")
    suspend fun getBudgetByCategory(categoryId: Long): BudgetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBudget(budget: BudgetEntity): Long

    @Update
    suspend fun updateBudget(budget: BudgetEntity)

    @Query("UPDATE budgets SET isDeleted = 1 WHERE id = :id")
    suspend fun softDeleteBudget(id: Long)

    @Query("SELECT * FROM budgets WHERE isDeleted = 0")
    suspend fun getAllBudgetsForExport(): List<BudgetEntity>
}
