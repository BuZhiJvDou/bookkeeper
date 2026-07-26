package com.bookkeeper.data.sync

import com.bookkeeper.data.repository.TransactionRepository
import com.bookkeeper.data.repository.CategoryRepository
import com.bookkeeper.data.repository.AccountRepository
import com.bookkeeper.domain.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class ExportData(
    val version: String = "1.0",
    val exportedAt: Long = System.currentTimeMillis(),
    val deviceId: String,
    val data: ExportPayload
)

@Serializable
data class ExportPayload(
    val transactions: List<Transaction>,
    val categories: List<Category>,
    val accounts: List<Account>
)

@Singleton
class SyncManager @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val accountRepository: AccountRepository
) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    suspend fun exportData(deviceId: String): String = withContext(Dispatchers.IO) {
        val transactions = transactionRepository.getAllForExport()
        val categories = categoryRepository.getAllForExport()
        val accounts = accountRepository.getAllForExport()

        val exportData = ExportData(
            deviceId = deviceId,
            data = ExportPayload(
                transactions = transactions,
                categories = categories,
                accounts = accounts
            )
        )

        json.encodeToString(exportData)
    }

    suspend fun exportToFile(deviceId: String, file: File): Boolean = withContext(Dispatchers.IO) {
        try {
            val data = exportData(deviceId)
            file.writeText(data)
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun importFromJson(jsonString: String): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val exportData = json.decodeFromString<ExportData>(jsonString)
            var importedCount = 0

            // 导入分类（跳过已存在的）
            for (category in exportData.data.categories) {
                val existing = categoryRepository.getCategoryById(category.id)
                if (existing == null) {
                    categoryRepository.importCategories(listOf(category))
                    importedCount++
                }
            }

            // 导入账户（跳过已存在的）
            for (account in exportData.data.accounts) {
                val existing = accountRepository.getAccountById(account.id)
                if (existing == null) {
                    accountRepository.importAccounts(listOf(account))
                }
            }

            // 导入交易（跳过已存在的）
            for (transaction in exportData.data.transactions) {
                val existing = transactionRepository.getTransactionById(transaction.id)
                if (existing == null) {
                    transactionRepository.importTransactions(listOf(transaction))
                    importedCount++
                }
            }

            Result.success(importedCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun importFromFile(file: File): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val jsonString = file.readText()
            importFromJson(jsonString)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun exportToCsv(): String = withContext(Dispatchers.IO) {
        val transactions = transactionRepository.getAllForExport()
        val categories = categoryRepository.getAllForExport()
        val accounts = accountRepository.getAllForExport()

        val categoryMap = categories.associateBy { it.id }
        val accountMap = accounts.associateBy { it.id }

        val header = "日期,类型,金额,分类,账户,备注"
        val rows = transactions.map { t ->
            val date = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                .format(java.util.Date(t.date))
            val type = when (t.type) {
                TransactionType.INCOME -> "收入"
                TransactionType.EXPENSE -> "支出"
                TransactionType.TRANSFER -> "转账"
            }
            val amount = String.format("%.2f", t.amount / 100.0)
            val category = categoryMap[t.categoryId]?.name ?: "未知"
            val account = accountMap[t.accountId]?.name ?: "未知"
            val note = t.note?.replace(",", "，") ?: ""

            "$date,$type,$amount,$category,$account,$note"
        }

        (listOf(header) + rows).joinToString("\n")
    }
}
