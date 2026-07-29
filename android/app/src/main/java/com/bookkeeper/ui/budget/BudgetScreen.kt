package com.bookkeeper.ui.budget

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.bookkeeper.domain.model.BudgetPeriod
import com.bookkeeper.domain.model.Category

/** 周期中文标签 */
private fun periodLabel(p: BudgetPeriod): String = when (p) {
    BudgetPeriod.MONTHLY -> "每月"
    BudgetPeriod.WEEKLY -> "每周"
    BudgetPeriod.YEARLY -> "每年"
}

/** 分 → 元字符串 */
private fun money(cents: Long): String = String.format("%.2f", cents / 100.0)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetScreen(
    onNavigateBack: () -> Unit,
    viewModel: BudgetViewModel = hiltViewModel()
) {
    val budgets by viewModel.budgets.collectAsStateWithLifecycle()
    val expenseCategories by viewModel.expenseCategories.collectAsStateWithLifecycle()

    // 对话框状态：null=不显示；BudgetUi=编辑现有；EMPTY 标记=新增
    var editingItem by remember { mutableStateOf<BudgetUi?>(null) }
    var showDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("预算管理") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { editingItem = null; showDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "新增预算")
                    }
                }
            )
        }
    ) { padding ->
        if (budgets.isEmpty()) {
            // 空状态
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🎯", fontSize = 48.sp)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "还没有预算，点击右上角新增\n控制你的开支",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(budgets) { item ->
                    BudgetCard(
                        item = item,
                        onEdit = { editingItem = item; showDialog = true },
                        onDelete = { viewModel.deleteBudget(item.budget.id) }
                    )
                }
            }
        }
    }

    if (showDialog) {
        BudgetDialog(
            item = editingItem,
            categories = expenseCategories,
            onDismiss = { showDialog = false },
            onSave = { amount, period, categoryId ->
                viewModel.saveBudget(editingItem?.budget?.id, amount, period, categoryId)
                showDialog = false
            }
        )
    }
}

@Composable
private fun BudgetCard(item: BudgetUi, onEdit: () -> Unit, onDelete: () -> Unit) {
    // 进度条颜色：超支红 / 预警橙 / 正常绿
    val barColor = when {
        item.isOverspent -> Color(0xFFF44336)
        item.isWarning -> Color(0xFFFF9800)
        else -> Color(0xFF4CAF50)
    }
    val title = item.categoryName ?: "总预算"

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 头部：标题 + 周期标签 + 操作
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(periodLabel(item.budget.period), fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = "编辑", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "删除", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                }
            }

            Spacer(Modifier.height(12.dp))

            // 进度条
            LinearProgressIndicator(
                progress = item.progress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp)),
                color = barColor,
                trackColor = Color.Black.copy(alpha = 0.06f)
            )

            Spacer(Modifier.height(8.dp))

            // 数字行
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("已用 ¥${money(item.used)} / ¥${money(item.budget.amount)}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                when {
                    item.isOverspent -> Text("⚠️ 已超支 ¥${money(-item.remaining)}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF44336))
                    item.isWarning -> Text("接近上限 · 剩余 ¥${money(item.remaining)}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF9800))
                    else -> Text("剩余 ¥${money(item.remaining)}", fontSize = 13.sp, color = Color(0xFF4CAF50))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BudgetDialog(
    item: BudgetUi?,
    categories: List<Category>,
    onDismiss: () -> Unit,
    onSave: (amount: Long, period: BudgetPeriod, categoryId: Long?) -> Unit
) {
    val isEdit = item != null
    var amountText by remember { mutableStateOf(if (isEdit) money(item!!.budget.amount) else "") }
    var period by remember { mutableStateOf(item?.budget?.period ?: BudgetPeriod.MONTHLY) }
    var categoryId by remember { mutableStateOf(item?.budget?.categoryId) }
    var categoryMenuExpanded by remember { mutableStateOf(false) }

    val selectedCatName = categoryId?.let { id -> categories.firstOrNull { it.id == id }?.name } ?: "总预算（不限分类）"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEdit) "编辑预算" else "新增预算") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // 金额
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { input -> amountText = input.filter { it.isDigit() || it == '.' || it == ',' } },
                    label = { Text("预算金额") },
                    placeholder = { Text("0.00") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // 周期选择
                Text("周期", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BudgetPeriod.values().forEach { p ->
                        FilterChip(
                            selected = period == p,
                            onClick = { period = p },
                            label = { Text(periodLabel(p)) }
                        )
                    }
                }

                // 分类选择
                Text("分类", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Box {
                    OutlinedButton(
                        onClick = { categoryMenuExpanded = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(selectedCatName, modifier = Modifier.weight(1f))
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(
                        expanded = categoryMenuExpanded,
                        onDismissRequest = { categoryMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("总预算（不限分类）") },
                            onClick = { categoryId = null; categoryMenuExpanded = false }
                        )
                        categories.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat.name) },
                                onClick = { categoryId = cat.id; categoryMenuExpanded = false }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val cents = (amountText.replace(',', '.').toDoubleOrNull() ?: 0.0).times(100).toLong()
                if (cents > 0) onSave(cents, period, categoryId)
            }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
