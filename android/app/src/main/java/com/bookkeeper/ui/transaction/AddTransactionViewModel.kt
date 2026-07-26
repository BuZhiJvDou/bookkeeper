package com.bookkeeper.ui.transaction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bookkeeper.data.repository.TransactionRepository
import com.bookkeeper.data.repository.CategoryRepository
import com.bookkeeper.data.repository.AccountRepository
import com.bookkeeper.domain.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AddTransactionUiState(
    val type: TransactionType = TransactionType.EXPENSE,
    val amount: String = "",
    val note: String = "",
    val selectedCategoryId: Long? = null,
    val selectedAccountId: Long? = null,
    val date: Long = System.currentTimeMillis(),
    val categories: List<Category> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class AddTransactionViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val accountRepository: AccountRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddTransactionUiState())
    val uiState: StateFlow<AddTransactionUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            // 加载分类
            categoryRepository.getCategoriesByType(TransactionType.EXPENSE).collect { categories ->
                _uiState.value = _uiState.value.copy(
                    categories = categories,
                    selectedCategoryId = categories.firstOrNull()?.id
                )
            }
        }
        viewModelScope.launch {
            // 加载账户
            accountRepository.getAllAccounts().collect { accounts ->
                _uiState.value = _uiState.value.copy(
                    accounts = accounts,
                    selectedAccountId = accounts.firstOrNull()?.id
                )
            }
        }
    }

    fun setType(type: TransactionType) {
        _uiState.value = _uiState.value.copy(type = type)
        // 重新加载对应类型的分类
        viewModelScope.launch {
            categoryRepository.getCategoriesByType(type).collect { categories ->
                _uiState.value = _uiState.value.copy(
                    categories = categories,
                    selectedCategoryId = categories.firstOrNull()?.id
                )
            }
        }
    }

    fun setAmount(amount: String) {
        // 只允许数字和小数点
        if (amount.isEmpty() || amount.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
            _uiState.value = _uiState.value.copy(amount = amount)
        }
    }

    fun setNote(note: String) {
        _uiState.value = _uiState.value.copy(note = note)
    }

    fun setCategory(categoryId: Long) {
        _uiState.value = _uiState.value.copy(selectedCategoryId = categoryId)
    }

    fun setAccount(accountId: Long) {
        _uiState.value = _uiState.value.copy(selectedAccountId = accountId)
    }

    fun setDate(date: Long) {
        _uiState.value = _uiState.value.copy(date = date)
    }

    fun save() {
        val state = _uiState.value
        if (state.amount.isEmpty() || state.amount.toDoubleOrNull() == null) {
            _uiState.value = state.copy(error = "请输入有效金额")
            return
        }
        if (state.selectedCategoryId == null) {
            _uiState.value = state.copy(error = "请选择分类")
            return
        }
        if (state.selectedAccountId == null) {
            _uiState.value = state.copy(error = "请选择账户")
            return
        }

        _uiState.value = state.copy(isSaving = true, error = null)

        viewModelScope.launch {
            try {
                val amountInCents = (state.amount.toDouble() * 100).toLong()
                val transaction = Transaction(
                    type = state.type,
                    amount = amountInCents,
                    categoryId = state.selectedCategoryId,
                    accountId = state.selectedAccountId,
                    note = state.note.ifEmpty { null },
                    date = state.date
                )
                transactionRepository.insertTransaction(transaction)

                // 更新账户余额
                val balanceChange = if (state.type == TransactionType.INCOME) amountInCents else -amountInCents
                accountRepository.updateBalance(state.selectedAccountId, balanceChange)

                _uiState.value = state.copy(isSaving = false, isSaved = true)
            } catch (e: Exception) {
                _uiState.value = state.copy(isSaving = false, error = "保存失败: ${e.message}")
            }
        }
    }
}
