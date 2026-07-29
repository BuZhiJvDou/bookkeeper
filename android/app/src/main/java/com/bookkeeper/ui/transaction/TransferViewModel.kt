package com.bookkeeper.ui.transaction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bookkeeper.data.repository.AccountRepository
import com.bookkeeper.data.repository.CategoryRepository
import com.bookkeeper.data.repository.TransactionRepository
import com.bookkeeper.domain.model.Account
import com.bookkeeper.domain.model.Transaction
import com.bookkeeper.domain.model.TransactionType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TransferUiState(
    val amount: String = "",
    val note: String = "",
    val fromAccountId: Long? = null,
    val toAccountId: Long? = null,
    val accounts: List<Account> = emptyList(),
    val placeholderCategoryId: Long? = null,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val error: String? = null
)

/**
 * 账户间转账 ViewModel。
 * 转账逻辑：记一条 TRANSFER 交易 + 转出账户扣款 + 转入账户到账。
 * 与桌面端保持一致。
 */
@HiltViewModel
class TransferViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val accountRepository: AccountRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TransferUiState())
    val uiState: StateFlow<TransferUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            accountRepository.getAllAccounts().collect { accounts ->
                _uiState.value = _uiState.value.copy(
                    accounts = accounts,
                    fromAccountId = _uiState.value.fromAccountId ?: accounts.firstOrNull()?.id,
                    toAccountId = _uiState.value.toAccountId ?: accounts.getOrNull(1)?.id ?: accounts.firstOrNull()?.id
                )
            }
        }
        viewModelScope.launch {
            // 加载一个支出分类作为转账记录的占位分类（满足外键约束；转账已被统计按 type 排除）
            categoryRepository.getCategoriesByType(TransactionType.EXPENSE).collect { cats ->
                if (_uiState.value.placeholderCategoryId == null) {
                    _uiState.value = _uiState.value.copy(placeholderCategoryId = cats.firstOrNull()?.id)
                }
            }
        }
    }

    fun setAmount(amount: String) {
        if (amount.isEmpty() || amount.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
            _uiState.value = _uiState.value.copy(amount = amount)
        }
    }

    fun setNote(note: String) { _uiState.value = _uiState.value.copy(note = note) }
    fun setFromAccount(id: Long) { _uiState.value = _uiState.value.copy(fromAccountId = id, error = null) }
    fun setToAccount(id: Long) { _uiState.value = _uiState.value.copy(toAccountId = id, error = null) }

    fun save() {
        val state = _uiState.value
        val amount = state.amount.toDoubleOrNull()
        if (amount == null || amount <= 0) {
            _uiState.value = state.copy(error = "请输入有效金额"); return
        }
        if (state.fromAccountId == null || state.toAccountId == null) {
            _uiState.value = state.copy(error = "请选择账户"); return
        }
        if (state.fromAccountId == state.toAccountId) {
            _uiState.value = state.copy(error = "转出和转入账户不能相同"); return
        }

        _uiState.value = state.copy(isSaving = true, error = null)
        viewModelScope.launch {
            try {
                val cents = (amount * 100).toLong()
                // 记一条转账交易（categoryId 用占位分类满足外键，转账不计入分类统计）
                transactionRepository.insertTransaction(
                    Transaction(
                        type = TransactionType.TRANSFER,
                        amount = cents,
                        categoryId = state.placeholderCategoryId ?: 1L,
                        accountId = state.fromAccountId,
                        toAccountId = state.toAccountId,
                        note = state.note.ifEmpty { null },
                        date = System.currentTimeMillis()
                    )
                )
                // 转出扣款、转入到账
                accountRepository.updateBalance(state.fromAccountId, -cents)
                accountRepository.updateBalance(state.toAccountId, cents)

                _uiState.value = state.copy(isSaving = false, isSaved = true)
            } catch (e: Exception) {
                _uiState.value = state.copy(isSaving = false, error = "转账失败: ${e.message}")
            }
        }
    }
}
