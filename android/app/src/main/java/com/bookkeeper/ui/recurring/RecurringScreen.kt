package com.bookkeeper.ui.recurring

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bookkeeper.domain.model.Account
import com.bookkeeper.domain.model.Category
import com.bookkeeper.domain.model.RecurringPeriod
import com.bookkeeper.domain.model.TransactionType
import java.text.SimpleDateFormat
import java.util.*

private fun periodLabel(p: RecurringPeriod): String = when (p) {
    RecurringPeriod.DAILY -> "每天"
    RecurringPeriod.WEEKLY -> "每周"
    RecurringPeriod.MONTHLY -> "每月"
}

private fun money(cents: Long): String = String.format("%.2f", cents / 100.0)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringScreen(
    onNavigateBack: () -> Unit,
    viewModel: RecurringViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val displays = viewModel.toDisplay(state)

    // 对话框：null=不显示；否则显示编辑/新增
    var showDialog by remember { mutableStateOf(false) }
    var editingId by remember { mutableStateOf<Long?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("循环记账") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { editingId = null; showDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "新增循环")
                    }
                }
            )
        }
    ) { padding ->
        if (displays.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🔁", fontSize = 48.sp)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "还没有循环记账规则\n如房租、工资等固定收支",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(displays) { d ->
                    RecurringCard(
                        display = d,
                        onEdit = { editingId = d.rule.id; showDialog = true },
                        onDelete = { viewModel.deleteRule(d.rule.id) }
                    )
                }
            }
        }
    }

    if (showDialog) {
        val editing = displays.firstOrNull { it.rule.id == editingId }
        RecurringDialog(
            editing = editing,
            categories = state.categories,
            accounts = state.accounts,
            onDismiss = { showDialog = false },
            onSave = { type, cents, categoryId, accountId, note, period, nextRun ->
                viewModel.saveRule(editingId, type, cents, categoryId, accountId, note, period, nextRun)
                showDialog = false
            }
        )
    }
}

@Composable
private fun RecurringCard(display: RecurringDisplay, onEdit: () -> Unit, onDelete: () -> Unit) {
    val r = display.rule
    val isIncome = r.type == TransactionType.INCOME
    val accent = try { Color(android.graphics.Color.parseColor(display.categoryColor)) } catch (e: Exception) { Color(0xFF6C63FF) }
    val df = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }

    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(36.dp).clip(CircleShape).background(accent.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(display.categoryName.firstOrNull()?.toString() ?: "🔁", color = accent, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(r.note ?: display.categoryName.ifEmpty { "循环记账" }, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                    Text("${periodLabel(r.period)} · ${display.accountName}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = "编辑", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "删除", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "${if (isIncome) "+" else "-"}¥${money(r.amount)}",
                    fontWeight = FontWeight.Bold,
                    color = if (isIncome) Color(0xFF4CAF50) else Color(0xFFF44336)
                )
                Text("下次: ${df.format(Date(r.nextRun))}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecurringDialog(
    editing: RecurringDisplay?,
    categories: List<Category>,
    accounts: List<Account>,
    onDismiss: () -> Unit,
    onSave: (type: TransactionType, cents: Long, categoryId: Long, accountId: Long, note: String?, period: RecurringPeriod, nextRun: Long) -> Unit
) {
    val isEdit = editing != null
    var type by remember { mutableStateOf(editing?.rule?.type ?: TransactionType.EXPENSE) }
    var amountText by remember { mutableStateOf(if (isEdit) money(editing!!.rule.amount) else "") }
    var period by remember { mutableStateOf(editing?.rule?.period ?: RecurringPeriod.MONTHLY) }
    var categoryId by remember { mutableStateOf(editing?.rule?.categoryId) }
    var accountId by remember { mutableStateOf(editing?.rule?.accountId ?: accounts.firstOrNull()?.id) }
    var note by remember { mutableStateOf(editing?.rule?.note ?: "") }
    var nextRun by remember { mutableStateOf(editing?.rule?.nextRun ?: System.currentTimeMillis()) }
    var catMenuExpanded by remember { mutableStateOf(false) }
    var accMenuExpanded by remember { mutableStateOf(false) }

    val filteredCats = categories.filter { it.type == type }
    // 类型切换后若当前分类不属于该类型，重置
    LaunchedEffect(type) {
        if (filteredCats.none { it.id == categoryId }) categoryId = filteredCats.firstOrNull()?.id
    }
    val selectedCatName = categoryId?.let { id -> categories.firstOrNull { it.id == id }?.name } ?: "选择分类"
    val selectedAccName = accountId?.let { id -> accounts.firstOrNull { it.id == id }?.name } ?: "选择账户"
    val df = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEdit) "编辑循环" else "新增循环") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // 收支类型
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = type == TransactionType.EXPENSE, onClick = { type = TransactionType.EXPENSE }, label = { Text("支出") })
                    FilterChip(selected = type == TransactionType.INCOME, onClick = { type = TransactionType.INCOME }, label = { Text("收入") })
                }
                // 金额
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { input -> amountText = input.filter { it.isDigit() || it == '.' || it == ',' } },
                    label = { Text("金额") },
                    placeholder = { Text("0.00") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                // 周期
                Text("周期", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RecurringPeriod.values().forEach { p ->
                        FilterChip(selected = period == p, onClick = { period = p }, label = { Text(periodLabel(p)) })
                    }
                }
                // 分类下拉
                Box {
                    OutlinedButton(onClick = { catMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(selectedCatName, modifier = Modifier.weight(1f))
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(expanded = catMenuExpanded, onDismissRequest = { catMenuExpanded = false }) {
                        filteredCats.forEach { cat ->
                            DropdownMenuItem(text = { Text(cat.name) }, onClick = { categoryId = cat.id; catMenuExpanded = false })
                        }
                    }
                }
                // 账户下拉
                Box {
                    OutlinedButton(onClick = { accMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(selectedAccName, modifier = Modifier.weight(1f))
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(expanded = accMenuExpanded, onDismissRequest = { accMenuExpanded = false }) {
                        accounts.forEach { acc ->
                            DropdownMenuItem(text = { Text(acc.name) }, onClick = { accountId = acc.id; accMenuExpanded = false })
                        }
                    }
                }
                // 下次记账日期（点击 +/- 周期微调，简单起见显示只读日期）
                Text("下次记账: ${df.format(Date(nextRun))}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { nextRun -= 86400000L }) { Text("- 1天") }
                    TextButton(onClick = { nextRun += 86400000L }) { Text("+ 1天") }
                    TextButton(onClick = { nextRun = System.currentTimeMillis() }) { Text("今天") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val cents = (amountText.replace(',', '.').toDoubleOrNull() ?: 0.0).times(100).toLong()
                val cId = categoryId
                val aId = accountId
                if (cents > 0 && cId != null && aId != null) {
                    onSave(type, cents, cId, aId, note.ifEmpty { null }, period, nextRun)
                }
            }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
