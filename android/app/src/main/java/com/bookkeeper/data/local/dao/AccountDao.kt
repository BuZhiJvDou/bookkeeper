package com.bookkeeper.data.local.dao

import androidx.room.*
import com.bookkeeper.data.local.entity.AccountEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {

    @Query("SELECT * FROM accounts WHERE isDeleted = 0 ORDER BY sortOrder ASC")
    fun getAllAccounts(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun getAccountById(id: Long): AccountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccount(account: AccountEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccounts(accounts: List<AccountEntity>)

    @Update
    suspend fun updateAccount(account: AccountEntity)

    @Query("UPDATE accounts SET isDeleted = 1 WHERE id = :id")
    suspend fun softDeleteAccount(id: Long)

    @Query("UPDATE accounts SET balance = balance + :amount WHERE id = :id")
    suspend fun updateBalance(id: Long, amount: Long)

    @Query("SELECT SUM(balance) FROM accounts WHERE isDeleted = 0")
    suspend fun getTotalBalance(): Long?

    @Query("SELECT * FROM accounts WHERE isDeleted = 0")
    suspend fun getAllAccountsForExport(): List<AccountEntity>
}
