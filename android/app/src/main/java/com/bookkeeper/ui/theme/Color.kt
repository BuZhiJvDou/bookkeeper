package com.bookkeeper.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 主题色：温柔 + 精致
 * 主色从纯紫（#6C63FF）改为带蓝调的渐变色系
 * 收入绿改成更有质感的薄荷绿
 * 支出红改为珊瑚红（更柔和）
 * 背景用米白代替纯白（减少刺眼）
 */

// === 主色：紫蓝渐变 ===
val Primary = Color(0xFF6750A4)        // Material 3 标准紫
val PrimaryContainer = Color(0xFFEADDFF) // 浅紫容器
val OnPrimary = Color(0xFFFFFFFF)
val OnPrimaryContainer = Color(0xFF21005D)

val Secondary = Color(0xFF625B71)
val SecondaryContainer = Color(0xFFE8DEF8)
val Tertiary = Color(0xFF7D5260)        // 玫红辅色

// === 收入/支出/转账（更柔和的版本） ===
val IncomeColor = Color(0xFF2E7D32)      // 深绿
val IncomeContainer = Color(0xFFC8E6C9) // 浅绿
val ExpenseColor = Color(0xFFC62828)     // 深红
val ExpenseContainer = Color(0xFFFFCDD2) // 浅红
val TransferColor = Color(0xFF1565C0)    // 深蓝
val TransferContainer = Color(0xFFBBDEFB) // 浅蓝

// === 背景：暖米白 ===
val Background = Color(0xFFFAF8FC)       // 暖米白（不是纯白）
val Surface = Color(0xFFFFFFFF)
val SurfaceVariant = Color(0xFFF3EDF7)   // 浅紫灰
val SurfaceContainer = Color(0xFFF7F2FA) // 卡片背景

// === 文字 ===
val TextPrimary = Color(0xFF1C1B1F)     // 不是纯黑
val TextSecondary = Color(0xFF49454F)
val TextHint = Color(0xFF79747E)
val OnSurfaceVariant = Color(0xFF49454F)

// === 边框/分割 ===
val Outline = Color(0xFFCAC4D0)
val OutlineVariant = Color(0xFFE7E0EC)

// === 状态色 ===
val ErrorColor = Color(0xFFB3261E)
val WarningColor = Color(0xFFF57C00)
val SuccessColor = Color(0xFF388E3C)

// === 分类颜色（更丰富、更有质感的色板） ===
val CategoryColors = listOf(
    Color(0xFFEF5350), // 珊瑚红
    Color(0xFFEC407A), // 玫红
    Color(0xFFAB47BC), // 紫
    Color(0xFF7E57C2), // 深紫
    Color(0xFF5C6BC0), // 靛
    Color(0xFF42A5F5), // 蓝
    Color(0xFF29B6F6), // 亮蓝
    Color(0xFF26C6DA), // 青
    Color(0xFF26A69A), // 蓝绿
    Color(0xFF66BB6A), // 绿
    Color(0xFF9CCC65), // 嫩绿
    Color(0xFFD4E157), // 黄绿
    Color(0xFFFFCA28), // 琥珀
    Color(0xFFFFA726), // 橙
    Color(0xFFFF7043), // 深橙
    Color(0xFF8D6E63), // 棕
    Color(0xFF78909C), // 蓝灰
)
