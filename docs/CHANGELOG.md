# 变更日志

> 记录所有重要变更，方便回溯和维护。

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
