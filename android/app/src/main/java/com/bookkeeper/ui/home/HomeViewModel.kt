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
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            val calendar = Calendar.getInstance()

            // 今日起止
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val todayStart = calendar.timeInMillis

            calendar.add(Calendar.DAY_OF_MONTH, 1)
            val todayEnd = calendar.timeInMillis

            // 本月起止
            calendar.timeInMillis = System.currentTimeMillis()
            calendar.set(Calendar.DAY_OF_MONTH, 1)
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val monthStart = calendar.timeInMillis

            calendar.add(Calendar.MONTH, 1)
            val monthEnd = calendar.timeInMillis

            // 并行加载数据
            val todayExpense = transactionRepository.getTotalByTypeAndDateRange(
                TransactionType.EXPENSE, todayStart, todayEnd
            )
            val todayIncome = transactionRepository.getTotalByTypeAndDateRange(
                TransactionType.INCOME, todayStart, todayEnd
            )
            val monthExpense = transactionRepository.getTotalByTypeAndDateRange(
                TransactionType.EXPENSE, monthStart, monthEnd
            )
            val monthIncome = transactionRepository.getTotalByTypeAndDateRange(
                TransactionType.INCOME, monthStart, monthEnd
            )
            val totalBalance = accountRepository.getTotalBalance()

            // 最近交易
            transactionRepository.getTransactionsByDateRange(monthStart, monthEnd).collect { transactions ->
                _uiState.value = HomeUiState(
                    todayExpense = todayExpense,
                    todayIncome = todayIncome,
                    monthExpense = monthExpense,
                    monthIncome = monthIncome,
                    totalBalance = totalBalance,
                    recentTransactions = transactions.take(10),
                    isLoading = false
                )
            }
        }
    }

    fun refresh() {
        _uiState.value = _uiState.value.copy(isLoading = true)
        loadData()
    }
}
