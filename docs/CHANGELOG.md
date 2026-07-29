# 变更日志

> 记录所有重要变更，方便回溯和维护。

## [0.7.0] - 2026-07-30

### 新增
- **循环记账 / 定期自动记账（双端）**
  - 桌面端：新增「循环记账」导航页（🔁），支持每天/每周/每月周期
  - Android 端：设置页新增「循环记账」入口 + 独立管理页
  - 规则：收支类型/金额/分类/账户/周期/下次记账日期，可增删改
  - **应用启动时自动处理到期规则**：生成对应交易 + 更新账户余额 + 推进到下一周期
  - 支持补齐错过的多个周期（长期未开应用不漏记），事务保证一致性
  - 数据库：桌面 `recurring_rules` 表 + Android `RecurringRuleEntity`（Room 升级到 v2）
  - 适用场景：房租、工资、订阅费等固定周期收支

## [0.6.0] - 2026-07-30

### 新增
- **Android 统计图表对齐**
  - 统计页新增支出分类占比**环形饼图**（Canvas 绘制，Top 6 + 其他，右侧图例）
  - 统计页新增收支趋势**双线折线图**（收入绿/支出红，周=按天/月=按周/年=按月分桶）
  - StatisticsViewModel 新增 `TrendPoint` 趋势分桶计算，与桌面端逻辑一致
  - 与周期筛选（周/月/年）联动
- **Android 数据导入导出 UI 补全**
  - 设置页「导出数据/导入数据/导出 CSV」三个入口接线完成（原为 TODO）
  - 用 SAF (Storage Access Framework) 让用户选择保存/读取路径
  - SettingsViewModel 复用现有 SyncManager 生成 JSON/CSV、解析导入
  - 操作结果通过 Snackbar 反馈

## [0.5.0] - 2026-07-30

### 新增
- **统计图表增强（桌面端）**
  - 新增支出分类占比**环形饼图**（Top 6 分类 + 其他，中心显示总额，含图例百分比）
  - 新增收支趋势**双线折线图**（收入绿/支出红，周=按天/月=按周/年=按月分桶）
  - 纯 SVG 手绘实现，无第三方图表库依赖，零新增体积
  - 与周期筛选（周/月/年）联动

## [0.4.0] - 2026-07-30

### 新增
- **账户间转账功能（双端）**
  - 桌面端：首页转账入口按钮 + 转账弹窗（金额/转出账户/转入账户/备注）
  - Android 端：首页顶栏转账图标 + 独立转账页（下拉选账户 + 箭头指示）
  - 记一条 TRANSFER 交易，转出账户扣款、转入账户到账（桌面用 SQLite 事务保证原子性）
  - 交易列表转账记录专属样式：🔄 蓝色图标 + "转出→转入" 展示
  - 校验：转出/转入账户不能相同、金额必须大于 0
  - 数据库层 `addTransfer()` + IPC `transactions:transfer`

## [0.3.0] - 2026-07-30

### 新增
- **预算管理功能（桌面端）**
  - 新增「预算」导航页（🎯）
  - 支持总预算（不限分类）和分类预算
  - 三种周期：每月 / 每周 / 每年
  - 实时进度条：绿色（正常）/ 橙色（≥80% 预警）/ 红色（超支）
  - 当期已用金额自动统计（按周期起止时间过滤支出）
  - 超支预警显示超出金额，接近上限提示剩余
  - 预算新增/编辑/删除（编辑弹窗 + 删除确认）
  - 数据导入导出包含预算（兼容旧版备份）
- **预算管理功能（Android 端对齐）**
  - `BudgetRepository`：预算 CRUD + 当期已用金额统计（月/周/年周期，与桌面逻辑一致）
  - `BudgetViewModel`：组合 budgets/categories/transactions 三条 Flow，记账后进度实时刷新
  - `BudgetScreen`：预算列表 + 进度条三色预警 + 新增/编辑对话框（金额/周期 Chip/分类下拉）
  - 设置页新增「预算管理」入口，导航接线完成
  - 复用现有 BudgetEntity / BudgetDao / DI（已就绪）
- **数据库层**
  - `db.js` 新增 budgets CRUD：getAllBudgets / addBudget / updateBudget / deleteBudget
  - `currentPeriodRange()` 计算周期起止时间戳
- **IPC 层**
  - preload.js 暴露 `budgets` API
  - index.js 新增 4 个 budgets IPC handlers

## [0.2.0] - 2026-07-25

### 修复
- **Android 构建问题**
  - `settings.gradle.kts`: `dependencyResolution` → `dependencyResolutionManagement`
  - AGP 8.2.2 → 8.7.3（支持 JDK 21 编译目标）
  - Kotlin 1.9.22 → 2.0.21 + Compose Compiler Plugin
  - KSP 1.9.22-1.0.17 → 2.0.21-1.0.27
  - Hilt 2.50 → 2.51.1
  - compileSdk/targetSdk 34 → 35
  - 移除过时的 `composeOptions` 块（Kotlin 2.0 使用 plugin 方式）
  - 添加 `gradlew.bat`（Windows Gradle wrapper）
- **序列化兼容性**
  - `TransactionEntity.kt`: 使用 `ListSerializer(String.serializer())` 替代内联全限定名
  - 添加 `kotlinx.serialization.plugin` 到根 build.gradle.kts

### 新增
- **桌面端 v1.1**
  - 日期选择器（支持选择任意日期记账）
  - 交易删除（带确认弹窗）
  - 搜索功能（按备注/分类搜索）
  - 每日支出柱状图（统计页）
  - 结余显示
  - 账户管理（设置页展示所有账户及余额）
  - 键盘快捷键（Ctrl+Enter 保存，Esc 关闭）
  - 交易数量统计

## [0.1.0] - 2026-07-25

### 新增
- 项目初始化
- **文档**
  - 架构设计文档 (`docs/ARCHITECTURE.md`)
  - 数据模型设计文档 (`docs/DATA_MODEL.md`)
  - 数据同步协议文档 (`docs/SYNC_PROTOCOL.md`)
  - 变更日志 (`docs/CHANGELOG.md`)
- **Android 客户端** (`android/`)
  - Kotlin + Jetpack Compose + Material 3
  - Room 数据库（4 张表：transactions, categories, accounts, budgets）
  - MVVM + Repository 架构
  - Hilt 依赖注入
  - 首页：余额卡片 + 今日收支 + 最近交易
  - 记账：收入/支出切换 + 分类选择 + 账户选择
  - 交易列表：按日期分组展示
  - 统计报表：周/月/年切换 + 分类占比
  - 设置：分类管理 + 账户管理 + 数据导入导出
  - 17 个默认支出/收入分类
  - 4 个默认账户（现金/银行卡/支付宝/微信）
  - JSON 导入/导出 + CSV 导出
- **桌面客户端** (`desktop/`)
  - Electron + React + better-sqlite3
  - 左侧导航栏 + 主内容区布局
  - 首页：余额 + 今日 + 本月 + 最近交易
  - 交易记录：按日分组 + 收支筛选
  - 统计：周/月/年 + 分类占比条形图
  - 设置：导入/导出 JSON + CSV 导出
  - 记账弹窗：金额 + 备注 + 分类 + 账户
  - IPC 通信（主进程 ↔ 渲染进程）
  - 自动初始化默认分类和账户
  - npm 依赖已安装（376 packages）

### 决策记录
- **[2026-07-25]** 选择 Room 作为 Android 数据库层
- **[2026-07-25]** 选择 better-sqlite3 作为桌面端数据库
- **[2026-07-25]** 金额以分为单位存储，避免浮点精度问题
- **[2026-07-25]** 采用软删除策略，保留数据可恢复性
- **[2026-07-25]** v1 采用 JSON 导入/导出实现数据同步
- **[2026-07-25]** Desktop 端使用 React CDN 而非打包工具（降低构建复杂度）
- **[2026-07-25]** 升级到 Kotlin 2.0 + Compose Compiler Plugin（兼容 JDK 21）

---

## 变更类型说明

- **新增 (Added)**: 新功能
- **修改 (Changed)**: 现有功能变更
- **弃用 (Deprecated)**: 即将移除的功能
- **移除 (Removed)**: 已移除的功能
- **修复 (Fixed)**: Bug 修复
- **安全 (Security)**: 安全相关
- **决策 (Decision)**: 重要技术决策记录
