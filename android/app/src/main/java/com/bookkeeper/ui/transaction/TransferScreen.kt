package com.bookkeeper.ui.transaction

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bookkeeper.domain.model.Account

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferScreen(
    onNavigateBack: () -> Unit,
    viewModel: TransferViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // 保存成功后返回上一页
    LaunchedEffect(state.isSaved) {
        if (state.isSaved) onNavigateBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("账户转账") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 金额
            OutlinedTextField(
                value = state.amount,
                onValueChange = viewModel::setAmount,
                label = { Text("转账金额") },
                placeholder = { Text("0.00") },
                leadingIcon = { Text("¥", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // 转出账户
            AccountDropdown(
                label = "转出账户",
                accounts = state.accounts,
                selectedId = state.fromAccountId,
                onSelect = viewModel::setFromAccount
            )

            // 箭头指示
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.ArrowDownward, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }

            // 转入账户
            AccountDropdown(
                label = "转入账户",
                accounts = state.accounts,
                selectedId = state.toAccountId,
                onSelect = viewModel::setToAccount
            )

            // 备注
            OutlinedTextField(
                value = state.note,
                onValueChange = viewModel::setNote,
                label = { Text("备注（可选）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // 错误提示
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            }

            // 转账按钮
            Button(
                onClick = viewModel::save,
                enabled = !state.isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text(if (state.isSaving) "转账中..." else "确认转账", fontSize = 16.sp)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountDropdown(
    label: String,
    accounts: List<Account>,
    selectedId: Long?,
    onSelect: (Long) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = accounts.firstOrNull { it.id == selectedId }
    val display = selected?.let { "${it.name}（¥${String.format("%.2f", it.balance / 100.0)}）" } ?: "选择账户"

    Column {
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it }
        ) {
            OutlinedTextField(
                value = display,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor()
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                accounts.forEach { acc ->
                    DropdownMenuItem(
                        text = { Text("${acc.name}（¥${String.format("%.2f", acc.balance / 100.0)}）") },
                        onClick = { onSelect(acc.id); expanded = false }
                    )
                }
            }
        }
    }
}
