package com.bookkeeper.ui.statistics

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bookkeeper.ui.components.SafeProgressBar
import com.bookkeeper.ui.theme.ExpenseColor
import com.bookkeeper.ui.theme.IncomeColor
import com.bookkeeper.ui.theme.Primary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    viewModel: StatisticsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("统计", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                // 不用 CircularProgressIndicator，避免 NoSuchMethodError 闪退
                Text("加载中…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 时间段选择
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Period.entries.forEach { period ->
                            FilterChip(
                                selected = uiState.selectedPeriod == period,
                                onClick = { viewModel.setPeriod(period) },
                                label = {
                                    Text(
                                        when (period) {
                                            Period.WEEK -> "本周"
                                            Period.MONTH -> "本月"
                                            Period.YEAR -> "本年"
                                        }
                                    )
                                }
                            )
                        }
                    }
                }

                // 收支总览
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("收入", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    "¥${String.format("%.2f", uiState.totalIncome / 100.0)}",
                                    color = IncomeColor,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("支出", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    "¥${String.format("%.2f", uiState.totalExpense / 100.0)}",
                                    color = ExpenseColor,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // 收支趋势折线图
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("收支趋势", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Spacer(modifier = Modifier.height(12.dp))
                            TrendChart(points = uiState.trend)
                        }
                    }
                }

                // 支出分类占比饼图
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("支出分类占比", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Spacer(modifier = Modifier.height(12.dp))
                            DonutChart(stats = uiState.categoryTotals, totalExpense = uiState.totalExpense)
                        }
                    }
                }

                // 支出分类统计
                item {
                    Text("支出分类", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }

                if (uiState.categoryTotals.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("暂无数据", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    items(uiState.categoryTotals) { stat ->
                        CategoryStatItem(stat = stat)
                    }
                }

                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
fun CategoryStatItem(stat: CategoryStat) {
    val color = try {
        Color(android.graphics.Color.parseColor(stat.category.color))
    } catch (e: Exception) {
        Primary
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(color.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(stat.category.name.first().toString(), color = color, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(stat.category.name, fontWeight = FontWeight.Medium)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "¥${String.format("%.2f", stat.amount / 100.0)}",
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${String.format("%.1f", stat.percentage * 100)}%",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 进度条
            SafeProgressBar(
                progress = stat.percentage,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                height = 6.dp,
                color = color,
                trackColor = color.copy(alpha = 0.1f)
            )
        }
    }
}

/**
 * 环形饼图（支出分类占比）。
 * 用 Compose Canvas 绘制 Top 6 分类 + 其他，右侧带图例。
 */
@Composable
fun DonutChart(stats: List<CategoryStat>, totalExpense: Long) {
    if (stats.isEmpty() || totalExpense <= 0) {
        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
            Text("暂无数据", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    // Top 6 + 其他
    val top = stats.take(6).map { it.category.name to it.amount }
    val restTotal = stats.drop(6).sumOf { it.amount }
    val slices = if (restTotal > 0) top + ("其他" to restTotal) else top
    val palette = listOf(
        Color(0xFFFF6B6B), Color(0xFF4ECDC4), Color(0xFF45B7D1),
        Color(0xFF96CEB4), Color(0xFFFFD93D), Color(0xFFDDA0DD), Color(0xFFBDC3C7)
    )

    Row(verticalAlignment = Alignment.CenterVertically) {
        // 环形图
        Canvas(modifier = Modifier.size(140.dp)) {
            var startAngle = -90f
            val strokeW = size.minDimension * 0.22f
            val diameter = size.minDimension - strokeW
            val topLeft = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
            slices.forEachIndexed { i, (_, amount) ->
                val sweep = (amount.toFloat() / totalExpense) * 360f
                drawArc(
                    color = palette[i % palette.size],
                    startAngle = startAngle,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = Size(diameter, diameter),
                    style = Stroke(width = strokeW, cap = StrokeCap.Butt)
                )
                startAngle += sweep
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        // 图例
        Column(modifier = Modifier.weight(1f)) {
            slices.forEachIndexed { i, (name, amount) ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(palette[i % palette.size]))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(name, fontSize = 13.sp, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
                    Text("${String.format("%.1f", amount.toFloat() / totalExpense * 100)}%", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/**
 * 收支趋势双线折线图（收入绿 / 支出红）。
 * 用 Compose Canvas 绘制，底部标签显示首/中/尾。
 */
@Composable
fun TrendChart(points: List<TrendPoint>) {
    if (points.isEmpty()) {
        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
            Text("暂无数据", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val maxV = (points.flatMap { listOf(it.income, it.expense) }.maxOrNull() ?: 1L).coerceAtLeast(1L)

    Column {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
        ) {
            val padL = 8f; val padR = 8f; val padT = 12f; val padB = 12f
            val w = size.width - padL - padR
            val hh = size.height - padT - padB
            val n = points.size
            val stepX = if (n > 1) w / (n - 1) else 0f

            fun pt(value: Long, i: Int): Offset {
                val x = padL + i * stepX
                val y = padT + hh - (value.toFloat() / maxV) * hh
                return Offset(x, y)
            }

            // 基准线
            drawLine(Color(0xFFEEEEEE), Offset(padL, padT + hh), Offset(padL + w, padT + hh), strokeWidth = 2f)

            // 收入线
            val incomePath = Path()
            val expensePath = Path()
            points.forEachIndexed { i, p ->
                val ip = pt(p.income, i); val ep = pt(p.expense, i)
                if (i == 0) { incomePath.moveTo(ip.x, ip.y); expensePath.moveTo(ep.x, ep.y) }
                else { incomePath.lineTo(ip.x, ip.y); expensePath.lineTo(ep.x, ep.y) }
            }
            drawPath(incomePath, color = Color(0xFF4CAF50), style = Stroke(width = 4f, cap = StrokeCap.Round))
            drawPath(expensePath, color = Color(0xFFF44336), style = Stroke(width = 4f, cap = StrokeCap.Round))

            // 数据点
            points.forEachIndexed { i, p ->
                drawCircle(Color(0xFF4CAF50), radius = 5f, center = pt(p.income, i))
                drawCircle(Color(0xFFF44336), radius = 5f, center = pt(p.expense, i))
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // 底部标签（首/中/尾）
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val idxs = listOf(0, points.size / 2, points.size - 1).distinct()
            idxs.forEach { i ->
                Text(points[i].label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 图例
        Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
            LegendDot(Color(0xFF4CAF50), "收入")
            Spacer(modifier = Modifier.width(20.dp))
            LegendDot(Color(0xFFF44336), "支出")
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Spacer(modifier = Modifier.width(6.dp))
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
