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
    val trend: List<TrendPoint> = emptyList(),
    val isLoading: Boolean = true
)

data class CategoryStat(
    val category: Category,
    val amount: Long,
    val percentage: Float
)

/** 趋势图数据点：标签 + 该桶收入/支出（单位：分） */
data class TrendPoint(
    val label: String,
    val income: Long,
    val expense: Long
)

enum class Period { WEEK, MONTH, YEAR }

@HiltViewModel
class StatisticsViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(StatisticsUiState())
    val uiState: StateFlow<StatisticsUiState> = _uiState.asStateFlow()

    // 选中的周期
    private val selectedPeriod = MutableStateFlow(Period.MONTH)

    init {
        // 任何交易表或账户表变化 + 周期切换都触发重算
        viewModelScope.launch {
            combine(
                transactionRepository.getAllTransactions(),
                categoryRepository.getAllCategories(),
                selectedPeriod
            ) { _, categories, period -> Triple(categories, period, Unit) }
                .collectLatest { (categories, period, _) ->
                    reloadForPeriod(categories, period)
                }
        }
    }

    fun setPeriod(period: Period) {
        selectedPeriod.value = period
    }

    private suspend fun reloadForPeriod(categories: List<Category>, period: Period) {
        _uiState.value = _uiState.value.copy(isLoading = true, selectedPeriod = period)
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
        val trend = calcTrend(period)

        _uiState.value = StatisticsUiState(
            selectedPeriod = period,
            totalIncome = totalIncome,
            totalExpense = totalExpense,
            categoryTotals = stats,
            trend = trend,
            isLoading = false
        )
    }

    /**
     * 计算收支趋势分桶数据。
     * 周=近7天（按天）；月=本月按周分段；年=近12月（按月）。
     * 与桌面端逻辑保持一致。
     */
    private suspend fun calcTrend(period: Period): List<TrendPoint> {
        val result = mutableListOf<TrendPoint>()
        when (period) {
            Period.WEEK -> {
                for (i in 6 downTo 0) {
                    val cal = Calendar.getInstance()
                    cal.add(Calendar.DAY_OF_YEAR, -i)
                    cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
                    val start = cal.timeInMillis
                    val end = start + 86400000L - 1
                    result.add(bucketPoint("${cal.get(Calendar.MONTH) + 1}/${cal.get(Calendar.DAY_OF_MONTH)}", start, end))
                }
            }
            Period.MONTH -> {
                val cal = Calendar.getInstance()
                val year = cal.get(Calendar.YEAR); val month = cal.get(Calendar.MONTH)
                val now = System.currentTimeMillis()
                for (w in 0 until 5) {
                    val cs = Calendar.getInstance().apply { set(year, month, 1 + w * 7, 0, 0, 0); set(Calendar.MILLISECOND, 0) }
                    val start = cs.timeInMillis
                    if (start > now) break
                    val end = start + 7L * 86400000L - 1
                    result.add(bucketPoint("第${w + 1}周", start, end))
                }
            }
            Period.YEAR -> {
                val year = Calendar.getInstance().get(Calendar.YEAR)
                for (m in 0 until 12) {
                    val cs = Calendar.getInstance().apply { set(year, m, 1, 0, 0, 0); set(Calendar.MILLISECOND, 0) }
                    val ce = Calendar.getInstance().apply { set(year, m + 1, 1, 0, 0, 0); set(Calendar.MILLISECOND, 0) }
                    result.add(bucketPoint("${m + 1}月", cs.timeInMillis, ce.timeInMillis - 1))
                }
            }
        }
        return result
    }

    private suspend fun bucketPoint(label: String, start: Long, end: Long): TrendPoint {
        val income = transactionRepository.getTotalByTypeAndDateRange(TransactionType.INCOME, start, end)
        val expense = transactionRepository.getTotalByTypeAndDateRange(TransactionType.EXPENSE, start, end)
        return TrendPoint(label, income, expense)
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
