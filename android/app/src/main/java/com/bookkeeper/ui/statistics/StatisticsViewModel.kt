package com.bookkeeper.ui.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bookkeeper.data.local.dao.CategoryTotal
import com.bookkeeper.data.repository.CategoryRepository
import com.bookkeeper.data.repository.TransactionRepository
import com.bookkeeper.domain.model.Category
import com.bookkeeper.domain.model.TransactionType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

data class StatisticsUiState(
    val selectedPeriod: Period = Period.MONTH,
    val totalIncome: Long = 0,
    val totalExpense: Long = 0,
    val categoryTotals: List<CategoryStat> = emptyList(),
    val isLoading: Boolean = true
)

data class CategoryStat(
    val category: Category,
    val amount: Long,
    val percentage: Float
)

enum class Period { WEEK, MONTH, YEAR }

@HiltViewModel
class StatisticsViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(StatisticsUiState())
    val uiState: StateFlow<StatisticsUiState> = _uiState.asStateFlow()

    init {
        loadStatistics(Period.MONTH)
    }

    fun setPeriod(period: Period) {
        loadStatistics(period)
    }

    private fun loadStatistics(period: Period) {
        _uiState.value = _uiState.value.copy(isLoading = true, selectedPeriod = period)

        viewModelScope.launch {
            val (startDate, endDate) = getDateRange(period)

            val totalIncome = transactionRepository.getTotalByTypeAndDateRange(
                TransactionType.INCOME, startDate, endDate
            )
            val totalExpense = transactionRepository.getTotalByTypeAndDateRange(
                TransactionType.EXPENSE, startDate, endDate
            )
            val categoryTotals = transactionRepository.getCategoryTotals(
                TransactionType.EXPENSE, startDate, endDate
            )

            val categories = categoryRepository.getAllCategories().first()
            val categoryMap = categories.associateBy { it.id }

            val stats = categoryTotals.mapNotNull { ct ->
                categoryMap[ct.categoryId]?.let { cat ->
                    CategoryStat(
                        category = cat,
                        amount = ct.total,
                        percentage = if (totalExpense > 0) ct.total.toFloat() / totalExpense else 0f
                    )
                }
            }

            _uiState.value = StatisticsUiState(
                selectedPeriod = period,
                totalIncome = totalIncome,
                totalExpense = totalExpense,
                categoryTotals = stats,
                isLoading = false
            )
        }
    }

    private fun getDateRange(period: Period): Pair<Long, Long> {
        val calendar = Calendar.getInstance()
        val endDate = calendar.timeInMillis

        when (period) {
            Period.WEEK -> calendar.add(Calendar.WEEK_OF_YEAR, -1)
            Period.MONTH -> calendar.add(Calendar.MONTH, -1)
            Period.YEAR -> calendar.add(Calendar.YEAR, -1)
        }
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startDate = calendar.timeInMillis

        return startDate to endDate
    }
}
