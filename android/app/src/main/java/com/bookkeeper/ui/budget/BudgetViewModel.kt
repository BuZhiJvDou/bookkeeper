package com.bookkeeper.ui.budget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bookkeeper.data.repository.BudgetRepository
import com.bookkeeper.data.repository.CategoryRepository
import com.bookkeeper.data.repository.TransactionRepository
import com.bookkeeper.domain.model.Budget
import com.bookkeeper.domain.model.BudgetPeriod
import com.bookkeeper.domain.model.Category
import com.bookkeeper.domain.model.TransactionType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 预算页 UI 数据：预算本体 + 当期已用 + 关联分类信息 */
data class BudgetUi(
    val budget: Budget,
    val used: Long,
    val categoryName: String?,   // null = 总预算
    val categoryColor: String?
) {
    val isOverspent: Boolean get() = used > budget.amount
    val isWarning: Boolean get() = !isOverspent && budget.amount > 0 && used.toDouble() / budget.amount >= 0.8
    val progress: Float get() = if (budget.amount > 0) (used.toDouble() / budget.amount).coerceAtMost(1.0).toFloat() else 0f
    val remaining: Long get() = budget.amount - used
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class BudgetViewModel @Inject constructor(
    private val budgetRepository: BudgetRepository,
    private val categoryRepository: CategoryRepository,
    private val transactionRepository: TransactionRepository
) : ViewModel() {

    /** 支出分类列表——用于新增/编辑预算时选择分类 */
    val expenseCategories: StateFlow<List<Category>> =
        categoryRepository.getCategoriesByType(TransactionType.EXPENSE)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * 预算 UI 列表。
     * 组合 budgets、categories、transactions 三条 flow——任一变化都重新计算已用金额，
     * 保证记一笔支出后预算进度立即刷新。
     */
    val budgets: StateFlow<List<BudgetUi>> =
        combine(
            budgetRepository.getAllBudgets(),
            categoryRepository.getAllCategories(),
            transactionRepository.getAllTransactions()
        ) { budgetList, categories, _ ->
            budgetList.map { b ->
                val cat = b.categoryId?.let { id -> categories.firstOrNull { it.id == id } }
                BudgetUi(
                    budget = b,
                    used = budgetRepository.getUsedAmount(b),
                    categoryName = cat?.name,
                    categoryColor = cat?.color
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun saveBudget(id: Long?, amount: Long, period: BudgetPeriod, categoryId: Long?) {
        viewModelScope.launch {
            if (id != null) {
                budgetRepository.updateBudget(
                    Budget(id = id, categoryId = categoryId, amount = amount, period = period, startDate = System.currentTimeMillis())
                )
            } else {
                budgetRepository.insertBudget(
                    Budget(categoryId = categoryId, amount = amount, period = period, startDate = System.currentTimeMillis())
                )
            }
        }
    }

    fun deleteBudget(id: Long) {
        viewModelScope.launch { budgetRepository.deleteBudget(id) }
    }
}
