package com.bookkeeper.data.local.dao

import androidx.room.*
import com.bookkeeper.data.local.entity.TransactionEntity
import com.bookkeeper.domain.model.TransactionType
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    @Query("SELECT * FROM transactions WHERE isDeleted = 0 ORDER BY date DESC")
    fun getAllTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE isDeleted = 0 AND date BETWEEN :startDate AND :endDate ORDER BY date DESC")
    fun getTransactionsByDateRange(startDate: Long, endDate: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE isDeleted = 0 AND type = :type ORDER BY date DESC")
    fun getTransactionsByType(type: TransactionType): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE isDeleted = 0 AND categoryId = :categoryId ORDER BY date DESC")
    fun getTransactionsByCategory(categoryId: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE isDeleted = 0 AND accountId = :accountId ORDER BY date DESC")
    fun getTransactionsByAccount(accountId: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getTransactionById(id: Long): TransactionEntity?

    @Query("SELECT SUM(amount) FROM transactions WHERE isDeleted = 0 AND type = :type AND date BETWEEN :startDate AND :endDate")
    suspend fun getTotalByTypeAndDateRange(type: TransactionType, startDate: Long, endDate: Long): Long?

    // 实时观察
    @Query("SELECT COALESCE(SUM(amount),0) FROM transactions WHERE isDeleted = 0 AND type = :type AND date BETWEEN :startDate AND :endDate")
    fun observeTotalByTypeAndDateRange(type: TransactionType, startDate: Long, endDate: Long): Flow<Long>

    @Query("SELECT SUM(amount) FROM transactions WHERE isDeleted = 0 AND type = :type AND categoryId = :categoryId AND date BETWEEN :startDate AND :endDate")
    suspend fun getTotalByCategoryAndDateRange(type: TransactionType, categoryId: Long, startDate: Long, endDate: Long): Long?

    @Query("SELECT categoryId, SUM(amount) as total FROM transactions WHERE isDeleted = 0 AND type = :type AND date BETWEEN :startDate AND :endDate GROUP BY categoryId ORDER BY total DESC")
    suspend fun getCategoryTotals(type: TransactionType, startDate: Long, endDate: Long): List<CategoryTotal>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: TransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactions(transactions: List<TransactionEntity>)

    @Update
    suspend fun updateTransaction(transaction: TransactionEntity)

    @Query("UPDATE transactions SET isDeleted = 1, updatedAt = :timestamp WHERE id = :id")
    suspend fun softDeleteTransaction(id: Long, timestamp: Long = System.currentTimeMillis())

    @Query("SELECT * FROM transactions WHERE isDeleted = 0")
    suspend fun getAllTransactionsForExport(): List<TransactionEntity>

    // 同步用：返回 updatedAt > since 的所有记录（含 tombstone）
    @Query("SELECT * FROM transactions WHERE updatedAt > :since ORDER BY id")
    suspend fun getAllForSync(since: Long): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE id = :id")
    fun getByIdSync(id: Long): TransactionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(t: TransactionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(list: List<TransactionEntity>)
}

data class CategoryTotal(
    val categoryId: Long,
    val total: Long
)
