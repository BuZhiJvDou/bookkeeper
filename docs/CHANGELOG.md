# 变更日志

> 记录所有重要变更，方便回溯和维护。

## [1.2.7] Android / [1.2.6] 桌面 - 2026-08-04

### 修复
- **Android 背景图透明度逻辑反了**
  - 滑块原先 0.6 背景弱、0.05 背景强
  - 改为 `visibility`（0..1，越大越清晰），默认 0.85，滑块范围 0.2..1.0
  - `MainActivity` Surface 改为透明，背景图直接透出
  - 设置页加 Toast：改完需重启生效
- **桌面同步诊断**
  - 设置页「诊断」按钮；启动同步服务后自动弹出
  - 显示本机所有 IPv4、端口、服务状态、防火墙提示，以及公网同步 URL 模板

版本：Android 1.2.6 → 1.2.7（versionCode 12 → 13）；桌面 1.2.5 → 1.2.6

## [1.2.6] Android - 2026-08-03

### 修复
- **设置页内容被截断**
  - `SettingsScreen` 的 Column 未滚动，数据同步 / 个性化 / 关于 / 危险操作等下半部分看不到，容易误以为没装上新版
  - 加上 `verticalScroll`

版本：Android 1.2.5 → 1.2.6（versionCode 11 → 12）

## [1.2.5] - 2026-08-03

### 新增
- **Android 视觉升级 + 自定义背景图**
  - Material 3 紫调配色（主色 `#6750A4`，暖米白背景，收入/支出色更柔和，17 色分类色板）
  - Typography 加粗标题 + 完整深色方案
  - 设置页选图片作背景（Photo Picker，限制 2MB），滑块调透明度，可清除
- **桌面设置页补上同步 UI（此前后端就绪但渲染层没入口）**
  - 启动/停止同步服务（mDNS + HTTP 17860）
  - 发现的 peer 列表 + 一键同步
  - 跨网段/公网 URL 手填
  - 状态每 2 秒轮询

### 修复
- 桌面循环记账备注 label 误用 `t('transferNote')`，改为 `t('note')`

版本：Android 1.2.4 → 1.2.5（versionCode 10 → 11）；桌面 1.2.4 → 1.2.5

## [1.2.4] 桌面 - 2026-08-03

### 移除
- **桌面转账 UI 清理**（TransferDialog / store.transfer / 首页按钮 / i18n / CSS 变量）
  - 保留 `TransactionType.TRANSFER` 枚举和旧数据样式，避免历史转账记录坏掉

### 修复
- 删转账后漏掉 `showTransfer` 引用，HomePage `ReferenceError` 导致整应用白屏

版本：桌面 1.2.3 → 1.2.4

## [1.2.3] - 2026-08-03

### 移除
- **转账功能**（不接银行/支付，纯记账本用不到）
  - Android：删 TransferScreen / TransferViewModel / 首页顶栏入口 / 路由
  - 桌面：删 `db.addTransfer` / `transactions:transfer` IPC
  - 表结构保留 `toAccountId` / `TRANSFER` 类型，旧数据不丢

### 修复
- **Android 记账后总资产 / 今日收支 / 统计不刷新**
  - Dao 增加 `observeTotalBalance()` / `observeTotalByTypeAndDateRange()`
  - Home / 统计 ViewModel 改用 `combine` 订阅 Flow

### 新增
- **Android 设置页「重置」**
  - 底部危险操作区，输入 `RESET` 二次确认
  - 删除 db + `.shm` + `.wal` 后退出，下次启动重建空库和默认账户

版本：1.2.2 → 1.2.3（Android versionCode 8 → 9）

## [1.2.1] - 2026-08-02

### 修复
- **Android 1.2.0 闪退**（`UnsatisfiedLinkError: sqlcipher nativeOpen`）
  - SQLCipher 4.5.4 的 `libsqlcipher.so` 不会自动加载
  - 在 `DatabaseModule.provideDatabase()` 入口显式 `System.loadLibrary("sqlcipher")`
- **桌面 SyncManager 加载失败**
  - `index.js` 循环依赖，启动报 `Accessing non-existent property SyncManager`
  - 主体迁到 `manager.js`，`index.js` 只做 re-export

版本：Android 1.2.0 → 1.2.1

