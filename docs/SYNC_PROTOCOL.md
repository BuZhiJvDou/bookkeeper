# 数据同步协议

> 最后更新: 2026-07-25

## v1: JSON 导入/导出模式

### 概述
v1 采用手动文件传输方式实现数据同步。用户在一台设备上导出数据为 JSON 文件，通过任意方式（微信、邮件、AirDrop 等）传输到另一台设备，然后导入。

### 导出格式

```json
{
  "version": "1.0",
  "exportedAt": 1753459200000,
  "deviceId": "android-abc123",
  "data": {
    "transactions": [
      {
        "id": 1,
        "type": "EXPENSE",
        "amount": 2500,
        "categoryId": 1,
        "accountId": 1,
        "toAccountId": null,
        "note": "午餐",
        "tags": null,
        "date": 1753459200000,
        "createdAt": 1753459200000,
        "updatedAt": 1753459200000,
        "isDeleted": false
      }
    ],
    "categories": [...],
    "accounts": [...]
  }
}
```

### 合并策略
1. **按 ID 去重**: 如果目标设备已有相同 ID 的记录，跳过
2. **新增优先**: 只导入目标设备不存在的记录
3. **不覆盖**: 不更新已有记录（避免数据丢失）

### 字段映射

#### Android Room → JSON
```
TransactionEntity.categoryId → category_id
TransactionEntity.accountId → account_id
TransactionEntity.toAccountId → to_account_id
TransactionEntity.createdAt → created_at
TransactionEntity.updatedAt → updated_at
TransactionEntity.isDeleted → is_deleted
```

#### Desktop SQLite → JSON
```
transactions.category_id → category_id
transactions.account_id → account_id
transactions.to_account_id → to_account_id
transactions.created_at → created_at
transactions.updated_at → updated_at
transactions.is_deleted → is_deleted
```

> **注意**: Desktop 端使用 snake_case，Android 端使用 camelCase。导入时需要做字段名转换。

### 金额存储
- 统一以**分**为单位（Integer）
- ¥25.50 = 2550

### 时间存储
- 统一以 **Unix 毫秒时间戳** 存储

### 文件命名规则
```
bookkeeper-export-{timestamp}.json
bookkeeper-export-{timestamp}.csv
```

## v2: 自动同步（规划中）

### 方案选项
1. **WebDAV**: 坚果云等支持 WebDAV 的网盘
2. **自建同步服务**: 轻量 Node.js 服务 + SQLite
3. **Firebase/Supabase**: BaaS 实时同步

### 同步流程（v2 规划）
```
设备 A 修改数据
    ↓
本地保存 + 记录变更日志 (change_log 表)
    ↓
连接同步服务
    ↓
上传变更日志
    ↓
合并冲突（Last-Write-Wins）
    ↓
下载其他设备的变更
    ↓
应用到本地数据库
```

### 变更日志表（v2 规划）
```sql
CREATE TABLE change_log (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    table_name TEXT NOT NULL,
    record_id INTEGER NOT NULL,
    operation TEXT NOT NULL, -- INSERT, UPDATE, DELETE
    data TEXT,              -- JSON of the record
    timestamp INTEGER NOT NULL,
    synced INTEGER DEFAULT 0
);
```
