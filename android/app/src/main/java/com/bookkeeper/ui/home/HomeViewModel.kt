package com.bookkeeper.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bookkeeper.data.repository.TransactionRepository
import com.bookkeeper.data.repository.AccountRepository
import com.bookkeeper.domain.model.Transaction
import com.bookkeeper.domain.model.TransactionType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

data class HomeUiState(
    val todayExpense: Long = 0,
    val todayIncome: Long = 0,
    val monthExpense: Long = 0,
    val monthIncome: Long = 0,
    val totalBalance: Long = 0,
    val recentTransactions: List<Transaction> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        observeData()
    }

    /**
     * 实时订阅所有数据源：交易表、账户表的任何变更都会自动重算首页。
     * 旧实现：只在 init 跑一次 getTotalByTypeAndDateRange (suspend Long)，
     *       → 新增/删除交易后余额 / 今日收支不会刷新，要退出重进。
     * 新实现：5 个 Flow combine 后任意一个发射新值就更新 UiState。
     */
    private fun observeData() {
        viewModelScope.launch {
            val (todayStart, todayEnd, monthStart, monthEnd) = computeRanges()

            // 1) 最近交易（按本月范围，Flow 持续发射）
            val transactionsFlow = transactionRepository.getTransactionsByDateRange(monthStart, monthEnd)

            // 2-5) 4 个金额 Flow
            val todayExpenseFlow = transactionRepository.observeTotalByTypeAndDateRange(
                TransactionType.EXPENSE, todayStart, todayEnd)
            val todayIncomeFlow = transactionRepository.observeTotalByTypeAndDateRange(
                TransactionType.INCOME, todayStart, todayEnd)
            val monthExpenseFlow = transactionRepository.observeTotalByTypeAndDateRange(
                TransactionType.EXPENSE, monthStart, monthEnd)
            val monthIncomeFlow = transactionRepository.observeTotalByTypeAndDateRange(
                TransactionType.INCOME, monthStart, monthEnd)
            val totalBalanceFlow = accountRepository.observeTotalBalance()

            combine(
                transactionsFlow,
                todayExpenseFlow,
                todayIncomeFlow,
                monthExpenseFlow,
                monthIncomeFlow,
                totalBalanceFlow
            ) { values ->
                @Suppress("UNCHECKED_CAST")
                val txs = values[0] as List<Transaction>
                HomeUiState(
                    todayExpense = values[1] as Long,
                    todayIncome = values[2] as Long,
                    monthExpense = values[3] as Long,
                    monthIncome = values[4] as Long,
                    totalBalance = values[5] as Long,
                    recentTransactions = txs.take(10),
                    isLoading = false
                )
            }.collect { newState ->
                _uiState.value = newState
            }
        }
    }

    private fun computeRanges(): LongArray {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val todayStart = calendar.timeInMillis
        calendar.add(Calendar.DAY_OF_MONTH, 1)
        val todayEnd = calendar.timeInMillis

        calendar.timeInMillis = System.currentTimeMillis()
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val monthStart = calendar.timeInMillis
        calendar.add(Calendar.MONTH, 1)
        val monthEnd = calendar.timeInMillis

        return longArrayOf(todayStart, todayEnd, monthStart, monthEnd)
    }
}
