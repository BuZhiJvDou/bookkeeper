# 数据同步协议

> 最后更新: 2026-08-02

## v2: 局域网 / 跨网段自动同步（当前）

### 概述
v2 实现了局域网自动发现 + 跨网段（公网）双向同步。手动触发，一键同步。
数据流：HTTP + AES-256-GCM 端到端加密。底层传输 JSON 协议与 v1 兼容，但**两
端必须装同版本**才能互相同步。

### 协议常量
- HTTP 端口：**17860**（"Bookkeeper 1.0" → B-1-1-0）
- mDNS 服务类型：`_bookkeeper._tcp`
- HTTP 头：`X-Bk-Device`、`X-Bk-Ts`、`X-Bk-Sig`
- AES-GCM envelope：`{v, iv, ct, tag}` 全 base64
- 钥匙派生：SQLCipher KEY → SHA-256 → HMAC-SHA256("sync-hmac"|"sync-aesgcm") → 32 字节
- 鉴权窗口：±5 分钟

### 端点
| Method | Path | 用途 |
|---|---|---|
| GET | `/api/v2/ping` | 健康检查 |
| POST | `/api/v2/sync` | 客户端推 + 服务端返回增量 |
| GET | `/api/v2/snapshot?since={ts}` | 服务端返回指定时间戳之后的全量 |

### Payload schema
```json
{
  "version": 2,
  "schemaVersion": 1,
  "deviceId": "android-abc123",
  "clientTs": 1753459200000,
  "data": {
    "transactions": [...],
    "categories": [...],
    "accounts": [...],
    "budgets": [...],
    "recurring": [...]
  }
}
```

每条记录包含 `createdAt`、`updatedAt`、`isDeleted` 字段。**删除是软删除
（tombstone）**，防止被对方同步复活。

### 合并策略：last-write-wins
- 同 ID 比 `updatedAt`，较新者胜出
- 增量传输：只推 `updatedAt > since` 的记录
- tombstone：删除标记随同步传播

### 端到端加密
- 全 payload 用 AES-256-GCM 加密（envelope base64）
- HTTP 头 HMAC-SHA256 签名（防网络中间人篡改）
- 钥匙 = SQLCipher 整库加密同一把 key（硬编码）
- 即使 Cloudflare / 中转服务器转发也看不到明文

### 局域网发现
- 桌面：`bonjour-service`（Windows / macOS / Linux）
- Android：`NsdManager`（系统 API）
- 设备名：`Bookkeeper-{deviceId 前 8 位}`

### 公网模式
- 用户在 UI 填一个 HTTPS URL（Cloudflare Tunnel / Tailscale / 自有服务器）
- 服务端 URL 由**另一台设备**作为 hub 提供
- 软件本身不做公网中继，只做端到端
- 公网 URL 必须 HTTPS；局域网允许 HTTP

### 已知限制
- 单人记账模型，没有用户/账号系统
- 不做实时双向推送（避免后台进程被杀）
- 不处理同时编辑同一行的真正冲突（last-write-wins 即可）
- 钥匙硬编码 = 反编译可解（与 SQLCipher 整库加密策略一致）

## v1: JSON 导入/导出模式（已实现但被 v2 替代）

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
