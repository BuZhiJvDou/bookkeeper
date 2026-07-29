package com.bookkeeper.data.repository

import com.bookkeeper.data.local.dao.AccountDao
import com.bookkeeper.data.local.dao.RecurringRuleDao
import com.bookkeeper.data.local.dao.TransactionDao
import com.bookkeeper.data.local.entity.RecurringRuleEntity
import com.bookkeeper.data.local.entity.TransactionEntity
import com.bookkeeper.domain.model.RecurringPeriod
import com.bookkeeper.domain.model.RecurringRule
import com.bookkeeper.domain.model.Transaction
import com.bookkeeper.domain.model.TransactionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecurringRepository @Inject constructor(
    private val recurringDao: RecurringRuleDao,
    private val transactionDao: TransactionDao,
    private val accountDao: AccountDao
) {
    fun getAllRules(): Flow<List<RecurringRule>> =
        recurringDao.getAllRules().map { list -> list.map { it.toDomain() } }

    suspend fun addRule(rule: RecurringRule): Long =
        recurringDao.insertRule(RecurringRuleEntity.fromDomain(rule))

    suspend fun updateRule(rule: RecurringRule) =
        recurringDao.updateRule(RecurringRuleEntity.fromDomain(rule))

    suspend fun deleteRule(id: Long) =
        recurringDao.softDeleteRule(id)

    /** 在 from 基础上加一个周期，返回下一次执行时间戳 */
    private fun computeNextRun(period: RecurringPeriod, from: Long): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = from }
        when (period) {
            RecurringPeriod.DAILY -> cal.add(Calendar.DAY_OF_YEAR, 1)
            RecurringPeriod.WEEKLY -> cal.add(Calendar.WEEK_OF_YEAR, 1)
            RecurringPeriod.MONTHLY -> cal.add(Calendar.MONTH, 1)
        }
        return cal.timeInMillis
    }

    /**
     * 处理所有到期的循环记账规则。
     * 对每条 nextRun <= now 且 autoCreate 的规则：生成交易 + 更新账户余额 + 推进 nextRun。
     * 补齐所有错过的周期。返回本次生成的交易数。
     * 与桌面端逻辑保持一致。
     */
    suspend fun processDueRules(): Int {
        val now = System.currentTimeMillis()
        val due = recurringDao.getDueRules(now)
        var created = 0
        for (rule in due) {
            var nextRun = rule.nextRun
            var guard = 0
            while (nextRun <= now && guard < 1000) {
                transactionDao.insertTransaction(
                    TransactionEntity.fromDomain(
                        Transaction(
                            type = rule.type,
                            amount = rule.amount,
                            categoryId = rule.categoryId,
                            accountId = rule.accountId,
                            note = rule.note,
                            date = nextRun
                        )
                    )
                )
                val balanceChange = if (rule.type == TransactionType.INCOME) rule.amount else -rule.amount
                accountDao.updateBalance(rule.accountId, balanceChange)
                created++
                nextRun = computeNextRun(rule.period, nextRun)
                guard++
            }
            recurringDao.updateNextRun(rule.id, nextRun, now)
        }
        return created
    }
}
