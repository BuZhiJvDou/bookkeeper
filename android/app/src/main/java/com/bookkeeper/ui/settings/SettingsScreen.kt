package com.bookkeeper.ui.settings

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToCategories: () -> Unit,
    onNavigateToAccounts: () -> Unit,
    onNavigateToBudgets: () -> Unit,
    onNavigateToRecurring: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // 待写入文件的内容缓存（点导出→生成内容→SAF 选路径→写入）
    var pendingContent by remember { mutableStateOf<String?>(null) }

    // 显示提示
    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    // 创建 JSON 文件的 SAF launcher
    val createJsonLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val content = pendingContent
        if (uri != null && content != null) {
            writeToUri(context, uri, content, viewModel)
        }
        pendingContent = null
    }

    // 创建 CSV 文件的 SAF launcher
    val createCsvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        val content = pendingContent
        if (uri != null && content != null) {
            writeToUri(context, uri, content, viewModel)
        }
        pendingContent = null
    }

    // 打开 JSON 文件的 SAF launcher（导入）
    val openJsonLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                if (text != null) viewModel.importJson(text)
                else viewModel.setMessage("读取文件失败")
            } catch (e: Exception) {
                viewModel.setMessage("读取失败: ${e.message}")
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 数据管理
            Text("数据管理", fontWeight = FontWeight.Medium, fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            SettingsItem(
                icon = Icons.Default.Category,
                title = "分类管理",
                subtitle = "自定义收入和支出分类",
                onClick = onNavigateToCategories
            )

            SettingsItem(
                icon = Icons.Default.AccountBalance,
                title = "账户管理",
                subtitle = "管理你的现金、银行卡等账户",
                onClick = onNavigateToAccounts
            )

            SettingsItem(
                icon = Icons.Default.Savings,
                title = "预算管理",
                subtitle = "设置月/周/年预算，控制开支",
                onClick = onNavigateToBudgets
            )

            SettingsItem(
                icon = Icons.Default.Autorenew,
                title = "循环记账",
                subtitle = "房租、工资等固定周期自动记账",
                onClick = onNavigateToRecurring
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 数据操作
            Text("数据操作", fontWeight = FontWeight.Medium, fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            SettingsItem(
                icon = Icons.Default.FileUpload,
                title = "导出数据",
                subtitle = "导出为 JSON 文件",
                onClick = {
                    viewModel.buildExportJson { json ->
                        pendingContent = json
                        createJsonLauncher.launch("记账单备份-${System.currentTimeMillis()}.json")
                    }
                }
            )

            SettingsItem(
                icon = Icons.Default.FileDownload,
                title = "导入数据",
                subtitle = "从 JSON 文件导入",
                onClick = { openJsonLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }
            )

            SettingsItem(
                icon = Icons.Default.TableChart,
                title = "导出 CSV",
                subtitle = "导出为 Excel 可打开的 CSV",
                onClick = {
                    viewModel.buildExportCsv { csv ->
                        pendingContent = csv
                        createCsvLauncher.launch("记账单-${System.currentTimeMillis()}.csv")
                    }
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 关于
            Text("关于", fontWeight = FontWeight.Medium, fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            SettingsItem(
                icon = Icons.Default.Info,
                title = "关于记账单",
                subtitle = "版本 1.0.0",
                onClick = { }
            )
        }
    }
}

/** 把内容写入用户通过 SAF 选择的 Uri */
private fun writeToUri(context: Context, uri: android.net.Uri, content: String, viewModel: SettingsViewModel) {
    try {
        context.contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray()) }
        viewModel.setMessage("导出成功")
    } catch (e: Exception) {
        viewModel.setMessage("写入失败: ${e.message}")
    }
}

@Composable
fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Medium)
                Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
