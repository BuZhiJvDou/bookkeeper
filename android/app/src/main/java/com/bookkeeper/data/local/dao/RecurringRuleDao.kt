package com.bookkeeper.data.local.dao

import androidx.room.*
import com.bookkeeper.data.local.entity.RecurringRuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecurringRuleDao {

    @Query("SELECT * FROM recurring_rules WHERE isDeleted = 0 ORDER BY nextRun ASC")
    fun getAllRules(): Flow<List<RecurringRuleEntity>>

    /** 到期且开启自动记账的规则（用于启动时批处理） */
    @Query("SELECT * FROM recurring_rules WHERE isDeleted = 0 AND autoCreate = 1 AND nextRun <= :now")
    suspend fun getDueRules(now: Long): List<RecurringRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: RecurringRuleEntity): Long

    @Update
    suspend fun updateRule(rule: RecurringRuleEntity)

    @Query("UPDATE recurring_rules SET nextRun = :nextRun, lastRun = :lastRun WHERE id = :id")
    suspend fun updateNextRun(id: Long, nextRun: Long, lastRun: Long)

    @Query("UPDATE recurring_rules SET isDeleted = 1 WHERE id = :id")
    suspend fun softDeleteRule(id: Long)

    @Query("SELECT * FROM recurring_rules WHERE updatedAt > :since ORDER BY id")
    suspend fun getAllForSync(since: Long): List<RecurringRuleEntity>

    @Query("SELECT * FROM recurring_rules WHERE id = :id")
    fun getByIdSync(id: Long): RecurringRuleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(r: RecurringRuleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(list: List<RecurringRuleEntity>)
}
