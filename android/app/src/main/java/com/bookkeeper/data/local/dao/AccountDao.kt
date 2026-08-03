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

    // 实时观察：每次 accounts 表变更后 Flow 会重新发射
    @Query("SELECT COALESCE(SUM(balance),0) FROM accounts WHERE isDeleted = 0")
    fun observeTotalBalance(): Flow<Long>

    @Query("SELECT COUNT(*) FROM accounts WHERE isDeleted = 0")
    suspend fun getAccountCount(): Int

    @Query("SELECT * FROM accounts WHERE isDeleted = 0")
    suspend fun getAllAccountsForExport(): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE updatedAt > :since ORDER BY id")
    suspend fun getAllForSync(since: Long): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE id = :id")
    fun getByIdSync(id: Long): AccountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(a: AccountEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(list: List<AccountEntity>)
}
