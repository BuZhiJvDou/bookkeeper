# 架构设计文档

> 最后更新: 2026-07-25

## 1. 整体架构

```
┌─────────────┐     ┌─────────────┐
│   Android   │     │   Desktop   │
│  (Compose)  │     │ (Electron)  │
└──────┬──────┘     └──────┬──────┘
       │                   │
┌──────┴──────┐     ┌──────┴──────┐
│  ViewModel  │     │  React Hook │
└──────┬──────┘     └──────┬──────┘
       │                   │
┌──────┴──────┐     ┌──────┴──────┐
│    Room     │     │   SQLite    │
│  (SQLite)   │     │(better-sqlite)│
└──────┬──────┘     └──────┬──────┘
       │                   │
       └─────────┬─────────┘
                 │
        ┌────────┴────────┐
        │   Sync Layer    │
        │  (JSON/REST)    │
        └─────────────────┘
```

## 2. Android 端架构

采用 **MVVM** + **Repository 模式**：

```
UI Layer (Compose)
    ↓ Events
ViewModel Layer
    ↓ Calls
Domain Layer (Use Cases)
    ↓ Queries
Data Layer (Repository → Room DAO)
```

### 关键依赖

| 库 | 用途 |
|---|---|
| Jetpack Compose | 声明式 UI |
| Room | 本地数据库 |
| Hilt | 依赖注入 |
| Navigation Compose | 页面导航 |
| Material 3 | 设计系统 |
| MPAndroidChart | 图表 |
| Kotlin Coroutines | 异步处理 |
| kotlinx-serialization | JSON 序列化 |

### 包结构

```
com.bookkeeper/
├── BookkeeperApp.kt          # Application
├── data/
│   ├── local/
│   │   ├── AppDatabase.kt    # Room Database
│   │   ├── dao/
│   │   │   ├── TransactionDao.kt
│   │   │   ├── CategoryDao.kt
│   │   │   ├── AccountDao.kt
│   │   │   └── BudgetDao.kt
│   │   └── entity/
│   │       ├── TransactionEntity.kt
│   │       ├── CategoryEntity.kt
│   │       ├── AccountEntity.kt
│   │       └── BudgetEntity.kt
│   ├── repository/
│   │   ├── TransactionRepository.kt
│   │   ├── CategoryRepository.kt
│   │   ├── AccountRepository.kt
│   │   └── BudgetRepository.kt
│   └── sync/
│       ├── SyncManager.kt
│       └── DataExporter.kt
├── domain/
│   └── model/
│       ├── Transaction.kt
│       ├── Category.kt
│       ├── Account.kt
│       └── Budget.kt
├── ui/
│   ├── theme/
│   │   ├── Theme.kt
│   │   ├── Color.kt
│   │   └── Type.kt
│   ├── navigation/
│   │   └── NavGraph.kt
│   ├── home/
│   │   ├── HomeScreen.kt
│   │   └── HomeViewModel.kt
│   ├── transaction/
│   │   ├── AddTransactionScreen.kt
│   │   ├── TransactionListScreen.kt
│   │   └── TransactionViewModel.kt
│   ├── statistics/
│   │   ├── StatisticsScreen.kt
│   │   └── StatisticsViewModel.kt
│   ├── category/
│   │   ├── CategoryScreen.kt
│   │   └── CategoryViewModel.kt
│   ├── account/
│   │   ├── AccountScreen.kt
│   │   └── AccountViewModel.kt
│   ├── budget/
│   │   ├── BudgetScreen.kt
│   │   └── BudgetViewModel.kt
│   └── settings/
│       ├── SettingsScreen.kt
│       └── SettingsViewModel.kt
└── di/
    ├── AppModule.kt
    ├── DatabaseModule.kt
    └── RepositoryModule.kt
```

## 3. Desktop 端架构

采用 **Electron + React** 架构：

```
┌──────────────────────────────────┐
│         Electron Main            │
│  ┌────────────────────────────┐  │
│  │  SQLite Database Layer     │  │
│  │  (better-sqlite3)          │  │
│  └────────────────────────────┘  │
│  ┌────────────────────────────┐  │
│  │  IPC Handlers              │  │
│  │  (database, sync, export)  │  │
│  └────────────────────────────┘  │
└───────────────┬──────────────────┘
                │ IPC Bridge
┌───────────────┴──────────────────┐
│       Electron Renderer          │
│  ┌────────────────────────────┐  │
│  │  React App                 │  │
│  │  ├── Pages/                │  │
│  │  ├── Components/           │  │
│  │  ├── Hooks/                │  │
│  │  ├── Store/ (Zustand)      │  │
│  │  └── Services/             │  │
│  └────────────────────────────┘  │
└──────────────────────────────────┘
```

### 关键依赖

| 库 | 用途 |
|---|---|
| Electron | 桌面壳 |
| React 18 | UI 框架 |
| TypeScript | 类型安全 |
| Zustand | 状态管理 |
| better-sqlite3 | SQLite 数据库 |
| Recharts | 图表 |
| Ant Design | UI 组件库 |
| electron-builder | 打包 |

## 4. 设计决策记录

### 为什么选 Room 而不是直接用 SQLite？
- Room 提供编译时 SQL 验证
- 与 Kotlin 协程无缝集成
- 减少样板代码

### 为什么选 better-sqlite3 而不是 Sequelize/Prisma？
- 同步 API，Electron 主进程友好
- 性能优异，比 async SQLite 快 5-10 倍
- 与 Android Room 数据库格式兼容

### 为什么选 Zustand 而不是 Redux？
- 极简 API，无 boilerplate
- TypeScript 友好
- 体积小（~1KB）

## 5. 数据同步策略 (v1)

### 导出/导入模式
1. 用户在一台设备上导出数据为 JSON 文件
2. 将文件传输到另一台设备
3. 在另一台设备上导入数据
4. 合并策略：以时间戳较新的记录为准

### 导出格式
```json
{
  "version": "1.0",
  "exported_at": "2026-07-25T10:00:00Z",
  "device_id": "android-xxx",
  "data": {
    "transactions": [...],
    "categories": [...],
    "accounts": [...],
    "budgets": [...]
  }
}
```
