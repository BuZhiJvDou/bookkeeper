package com.bookkeeper.data.repository

import com.bookkeeper.data.local.dao.AccountDao
import com.bookkeeper.data.local.entity.AccountEntity
import com.bookkeeper.domain.model.Account
import com.bookkeeper.domain.model.AccountType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccountRepository @Inject constructor(
    private val accountDao: AccountDao
) {
    fun getAllAccounts(): Flow<List<Account>> =
        accountDao.getAllAccounts().map { list -> list.map { it.toDomain() } }

    suspend fun getAccountById(id: Long): Account? =
        accountDao.getAccountById(id)?.toDomain()

    suspend fun insertAccount(account: Account): Long =
        accountDao.insertAccount(AccountEntity.fromDomain(account))

    suspend fun updateAccount(account: Account) =
        accountDao.updateAccount(AccountEntity.fromDomain(account))

    suspend fun deleteAccount(id: Long) =
        accountDao.softDeleteAccount(id)

    suspend fun updateBalance(id: Long, amount: Long) =
        accountDao.updateBalance(id, amount)

    suspend fun getTotalBalance(): Long =
        accountDao.getTotalBalance() ?: 0L

    fun observeTotalBalance(): Flow<Long> =
        accountDao.observeTotalBalance()

    suspend fun initDefaultAccounts() {
        if (accountDao.getAccountCount() > 0) return
        val defaults = listOf(
            Account(name = "现金", type = AccountType.CASH, icon = "payments", color = "#2ECC71", sortOrder = 0),
            Account(name = "银行卡", type = AccountType.BANK_CARD, icon = "credit_card", color = "#3498DB", sortOrder = 1),
            Account(name = "支付宝", type = AccountType.ALIPAY, icon = "account_balance_wallet", color = "#1677FF", sortOrder = 2),
            Account(name = "微信", type = AccountType.WECHAT, icon = "chat", color = "#07C160", sortOrder = 3),
        )
        accountDao.insertAccounts(defaults.map { AccountEntity.fromDomain(it) })
    }

    suspend fun getAllForExport(): List<Account> =
        accountDao.getAllAccountsForExport().map { it.toDomain() }

    suspend fun importAccounts(accounts: List<Account>) {
        accountDao.insertAccounts(accounts.map { AccountEntity.fromDomain(it) })
    }
}
