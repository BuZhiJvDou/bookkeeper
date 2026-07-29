package com.bookkeeper.ui.recurring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bookkeeper.data.repository.AccountRepository
import com.bookkeeper.data.repository.CategoryRepository
import com.bookkeeper.data.repository.RecurringRepository
import com.bookkeeper.domain.model.Account
import com.bookkeeper.domain.model.Category
import com.bookkeeper.domain.model.RecurringPeriod
import com.bookkeeper.domain.model.RecurringRule
import com.bookkeeper.domain.model.TransactionType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RecurringUiState(
    val rules: List<RecurringRule> = emptyList(),
    val categories: List<Category> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val isLoading: Boolean = true
)

/** 用于列表显示：规则 + 分类名/颜色 + 账户名 */
data class RecurringDisplay(
    val rule: RecurringRule,
    val categoryName: String,
    val categoryColor: String,
    val accountName: String
)

@HiltViewModel
class RecurringViewModel @Inject constructor(
    private val recurringRepository: RecurringRepository,
    private val categoryRepository: CategoryRepository,
    private val accountRepository: AccountRepository
) : ViewModel() {

    val uiState: StateFlow<RecurringUiState> = combine(
        recurringRepository.getAllRules(),
        categoryRepository.getAllCategories(),
        accountRepository.getAllAccounts()
    ) { rules, categories, accounts ->
        RecurringUiState(
            rules = rules,
            categories = categories,
            accounts = accounts,
            isLoading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RecurringUiState())

    /** 组装列表显示数据（含分类/账户名） */
    fun toDisplay(state: RecurringUiState): List<RecurringDisplay> {
        val catMap = state.categories.associateBy { it.id }
        val accMap = state.accounts.associateBy { it.id }
        return state.rules.map { r ->
            RecurringDisplay(
                rule = r,
                categoryName = catMap[r.categoryId]?.name ?: "",
                categoryColor = catMap[r.categoryId]?.color ?: "#6C63FF",
                accountName = accMap[r.accountId]?.name ?: ""
            )
        }
    }

    fun saveRule(
        id: Long?,
        type: TransactionType,
        amountCents: Long,
        categoryId: Long,
        accountId: Long,
        note: String?,
        period: RecurringPeriod,
        nextRun: Long
    ) {
        viewModelScope.launch {
            val rule = RecurringRule(
                id = id ?: 0,
                type = type,
                amount = amountCents,
                categoryId = categoryId,
                accountId = accountId,
                note = note,
                period = period,
                nextRun = nextRun,
                autoCreate = true
            )
            if (id == null) recurringRepository.addRule(rule)
            else recurringRepository.updateRule(rule)
        }
    }

    fun deleteRule(id: Long) {
        viewModelScope.launch { recurringRepository.deleteRule(id) }
    }
}
