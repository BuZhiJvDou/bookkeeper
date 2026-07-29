package com.bookkeeper.data.repository

import com.bookkeeper.data.local.dao.BudgetDao
import com.bookkeeper.data.local.dao.TransactionDao
import com.bookkeeper.data.local.entity.BudgetEntity
import com.bookkeeper.domain.model.Budget
import com.bookkeeper.domain.model.BudgetPeriod
import com.bookkeeper.domain.model.TransactionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 预算仓库。
 * 负责预算的增删改查，并计算「当期已用金额」——按预算周期（月/周/年）
 * 过滤支出交易汇总，桌面端逻辑保持一致。
 */
@Singleton
class BudgetRepository @Inject constructor(
    private val budgetDao: BudgetDao,
    private val transactionDao: TransactionDao
) {
    fun getAllBudgets(): Flow<List<Budget>> =
        budgetDao.getAllBudgets().map { list -> list.map { it.toDomain() } }

    suspend fun insertBudget(budget: Budget): Long =
        budgetDao.insertBudget(BudgetEntity.fromDomain(budget))

    suspend fun updateBudget(budget: Budget) =
        budgetDao.updateBudget(BudgetEntity.fromDomain(budget))

    suspend fun deleteBudget(id: Long) =
        budgetDao.softDeleteBudget(id)

    /**
     * 计算某预算当期已用金额。
     * - categoryId 为 null：统计当期所有支出（总预算）
     * - categoryId 非 null：仅统计该分类支出（分类预算）
     */
    suspend fun getUsedAmount(budget: Budget): Long {
        val (start, end) = currentPeriodRange(budget.period)
        return if (budget.categoryId == null) {
            transactionDao.getTotalByTypeAndDateRange(TransactionType.EXPENSE, start, end) ?: 0L
        } else {
            transactionDao.getTotalByCategoryAndDateRange(TransactionType.EXPENSE, budget.categoryId, start, end) ?: 0L
        }
    }

    /** 计算指定周期的当期起止时间戳 [start, end] */
    fun currentPeriodRange(period: BudgetPeriod): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        // 归零到当天 00:00:00
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)

        return when (period) {
            BudgetPeriod.WEEKLY -> {
                // 周一为一周起点
                cal.firstDayOfWeek = Calendar.MONDAY
                cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                val start = cal.timeInMillis
                val end = start + 7L * 86400000L - 1L
                start to end
            }
            BudgetPeriod.YEARLY -> {
                cal.set(Calendar.DAY_OF_YEAR, 1)
                val start = cal.timeInMillis
                cal.add(Calendar.YEAR, 1)
                val end = cal.timeInMillis - 1L
                start to end
            }
            else -> {
                // MONTHLY 默认
                cal.set(Calendar.DAY_OF_MONTH, 1)
                val start = cal.timeInMillis
                cal.add(Calendar.MONTH, 1)
                val end = cal.timeInMillis - 1L
                start to end
            }
        }
    }

    suspend fun getAllForExport(): List<Budget> =
        budgetDao.getAllBudgetsForExport().map { it.toDomain() }

    suspend fun importBudgets(budgets: List<Budget>) {
        budgets.forEach { budgetDao.insertBudget(BudgetEntity.fromDomain(it)) }
    }
}
