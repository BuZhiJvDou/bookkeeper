package com.bookkeeper.data.repository

import com.bookkeeper.data.local.dao.TransactionDao
import com.bookkeeper.data.local.dao.CategoryTotal
import com.bookkeeper.data.local.entity.TransactionEntity
import com.bookkeeper.domain.model.Transaction
import com.bookkeeper.domain.model.TransactionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransactionRepository @Inject constructor(
    private val transactionDao: TransactionDao
) {
    fun getAllTransactions(): Flow<List<Transaction>> =
        transactionDao.getAllTransactions().map { list ->
            list.map { it.toDomain() }
        }

    fun getTransactionsByDateRange(startDate: Long, endDate: Long): Flow<List<Transaction>> =
        transactionDao.getTransactionsByDateRange(startDate, endDate).map { list ->
            list.map { it.toDomain() }
        }

    fun getTransactionsByType(type: TransactionType): Flow<List<Transaction>> =
        transactionDao.getTransactionsByType(type).map { list ->
            list.map { it.toDomain() }
        }

    fun getTransactionsByCategory(categoryId: Long): Flow<List<Transaction>> =
        transactionDao.getTransactionsByCategory(categoryId).map { list ->
            list.map { it.toDomain() }
        }

    fun getTransactionsByAccount(accountId: Long): Flow<List<Transaction>> =
        transactionDao.getTransactionsByAccount(accountId).map { list ->
            list.map { it.toDomain() }
        }

    suspend fun getTransactionById(id: Long): Transaction? =
        transactionDao.getTransactionById(id)?.toDomain()

    suspend fun getTotalByTypeAndDateRange(type: TransactionType, startDate: Long, endDate: Long): Long =
        transactionDao.getTotalByTypeAndDateRange(type, startDate, endDate) ?: 0L

    suspend fun getCategoryTotals(type: TransactionType, startDate: Long, endDate: Long): List<CategoryTotal> =
        transactionDao.getCategoryTotals(type, startDate, endDate)

    suspend fun insertTransaction(transaction: Transaction): Long =
        transactionDao.insertTransaction(TransactionEntity.fromDomain(transaction))

    suspend fun updateTransaction(transaction: Transaction) =
        transactionDao.updateTransaction(TransactionEntity.fromDomain(transaction))

    suspend fun deleteTransaction(id: Long) =
        transactionDao.softDeleteTransaction(id)

    suspend fun getAllForExport(): List<Transaction> =
        transactionDao.getAllTransactionsForExport().map { it.toDomain() }

    suspend fun importTransactions(transactions: List<Transaction>) {
        transactionDao.insertTransactions(transactions.map { TransactionEntity.fromDomain(it) })
    }
}
