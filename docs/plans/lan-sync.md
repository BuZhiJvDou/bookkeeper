# 局域网 + 跨网段同步

**目标**: 同一 WiFi 自动发现设备、一键同步；公网/跨网段通过手填 URL 走 AES-GCM 端到端加密。

**分支**: `feat/lan-sync`
**基线**: `main` @ `924c0d9` (v1.1.0 SQLCipher)

---

## 总体架构

```
[桌面 hub]  ──mDNS/bonjour──→  [Android 客户端]   (同 WiFi)
[桌面 hub]  ──HTTPS URL──→    [任何能上网的设备]  (跨网段)
[Android A] ──HTTPS URL──→    [Android B]         (跨网段)

所有通信:
  HTTP POST/GET
  Body = AES-256-GCM( JSON payload, key = SQLCipher b64 key )
  Header: X-Bk-Ts, X-Bk-Device, X-Bk-Nonce
```

## API（HTTP）

| 路径 | 方法 | 用途 |
|---|---|---|
| `GET /api/v2/ping` | 鉴权 | 健康检查 + 服务版本 |
| `POST /api/v2/sync` | 鉴权 | 客户端带本地变更 → 服务端合并并返回服务端新增/更新的记录 |
| `GET /api/v2/snapshot?since={ts}` | 鉴权 | 服务端返回 `since` 之后变更的全量记录（增量） |

### 鉴权
- 客户端在 `X-Bk-Device` 头送 `deviceId`
- 客户端在 `X-Bk-Ts` 头送当前毫秒时间戳
- 客户端在 `X-Bk-Sig` 头送 `HMAC-SHA256(SQLCipher_key, deviceId + ':' + ts)` 的 hex
- 服务端用同一把钥匙校验；窗口 ±5 分钟防重放
- 错钥匙 / 错时间 → 401

### Sync payload（解密后）

```json
{
  "version": 2,
  "clientTs": 1753459200000,
  "deviceId": "android-abc123",
  "transactions": [
    { "id": 1, "type": "EXPENSE", "amount": 2500, ..., "updatedAt": 1753459200000, "isDeleted": false }
  ],
  "categories": [...],
  "accounts": [...],
  "budgets": [...],
  "recurring": [...]
}
```

合并策略:
- **同 ID**：`updatedAt` 较新者胜出
- **tombstone**：`isDeleted=true` 的记录也会被同步（防止被复活）
- **冲突**：保留双方（last-write-wins，单人记账场景足够）

### 增量传输
- 客户端记录 `last_sync_at`（每设备最后成功同步时间戳）
- 每次 sync 只带 `updatedAt > last_sync_at` 的记录
- 服务端返回同样条件的全量，客户端合并入库

## 桌面端实现

### `desktop/src/sync/`
- `server.js` — Node `http` 服务器，端口 17860，3 个 endpoint
- `mdns.js` — `bonjour-service` 广播 + 监听
- `client.js` — 主动连远端 URL（公网模式）
- `crypto.js` — AES-256-GCM 包装 / 解包，HMAC 签名
- `protocol.js` — 共享 JSON 协议 schema

### `desktop/src/sync/sync.js` — 入口
- `startServer()` — 启动 HTTP + mDNS
- `discoverPeers()` — 局域网发现
- `syncWith(peer)` — 执行完整 sync 流程

### IPC（main → renderer）
- `bk-sync:start-server` → `{running, url, peers}`
- `bk-sync:discover` → `[peer, ...]`
- `bk-sync:sync` (peer) → `{pushed, pulled, conflicts}`

### 设置页 UI
- 设置页加新分组 "同步"
- 显示本机同步服务状态（开/关 + URL）
- 局域网设备列表 + 一键同步按钮
- 公网 URL 输入框 + 测试连接
- 上次同步时间

## Android 端实现

### `android/app/src/main/java/com/bookkeeper/sync/`
- `LanSyncService.kt` — 同步服务（前台 service 或一次性协程）
- `NsdHelper.kt` — 局域网发现（`NsdManager`）
- `SyncClient.kt` — OkHttp 同步客户端
- `SyncCrypto.kt` — AES-256-GCM + HMAC（沿用 DbKey）
- `SyncProtocol.kt` — JSON 协议
- `SyncRepository.kt` — 与 Room 集成

### 设置页
- `SettingsScreen.kt` 新增分组 "局域网同步"
- 显示：局域网发现的设备列表（名字 + IP + 是否在线）
- 按钮：[开始本机服务] [同步选中] [添加公网 URL]

## 端口 / 协议

- HTTP 端口：**17860**（B-1-1-0 → B=1, k=1, K=1, 0=0；不重要，固定值）
- mDNS 服务类型：`_bookkeeper._tcp`
- 服务名：`Bookkeeper-{deviceId前8位}`

## 数据流（一次同步）

```
[Client]                       [Server]
  │                                │
  │  POST /api/v2/sync             │
  │  Header: X-Bk-Device, Ts, Sig  │
  │  Body: AES-GCM(JSON)           │
  │  ──────────────────────────→   │
  │                                │  验签 + 解密
  │                                │  合并事务（last-write-wins）
  │                                │  查 updatedAt > clientTs
  │  ←──────────────────────────  │  Body: AES-GCM(JSON 增量)
  │  验签 + 解密                    │
  │  upsert into Room               │
  │  更新 last_sync_at              │
  │                                │
```

## 验证清单

- [ ] 同 WiFi：手机自动发现电脑（无需输 IP）
- [ ] 一键同步：交易/账户/分类/预算/循环记账全部同步
- [ ] 公网 URL：能跨网段同步
- [ ] 抓包：Wireshark 看不到明文（TLS + AES-GCM）
- [ ] 错设备/错时间戳/错钥匙：401 拒绝
- [ ] 大数据量（1000+ 笔）：分页增量可工作
- [ ] tombstone：删了的交易不会因同步被复活

## 工作量

- 桌面：~500 行（http server + mdns + crypto）
- Android：~500 行（client + NSD + repo + UI）
- 共享协议：~50 行 schema
- 文档 + 测试：~200 行

## 风险 / 已知限制

1. **前台 service**：Android 12+ 对后台 service 严格，同步触发后短时间运行、不做常驻
2. **WiFi 隔离**：部分公共 WiFi 阻断 mDNS；提供 IP/URL 手填作为兜底
3. **大库同步**：10000+ 笔交易首次同步会慢；分页处理
4. **SQLCipher 钥匙相同**：双端数据库文件本身可互开；如果用户将来想"换钥匙"，需要重建

## 不做

- 实时双向推送（避免后台进程）
- 用户系统/账号
- 端到端 E2E 与 SQLCipher 钥匙绑定（保持简单）
