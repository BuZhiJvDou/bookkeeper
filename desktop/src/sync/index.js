/**
 * Sync 包聚合入口
 *
 * 把所有 sync 子模块的能力一并导出，方便主进程 / 测试 / 调试时一个 require 拿到全部。
 *
 * 修复：之前 `index.js` 既定义 SyncManager 又自引用 `require('./index')`，
 * 导致 Node 循环依赖。现在 SyncManager 单独在 `./manager.js`，
 * 本文件只做干净的 re-export。
 */
const { SyncManager, getDeviceId } = require('./manager');
const { SyncServer, SyncClient } = require('./server');
const { LanSyncMdns, getLocalIP } = require('./mdns');
const { buildPayload, applyPayload, PROTOCOL_VERSION, SCHEMA_VERSION } = require('./protocol');
const { encryptPayload, decryptPayload, signRequest, verifyRequest } = require('./crypto');

module.exports = {
  SyncManager, getDeviceId,
  SyncServer, SyncClient,
  LanSyncMdns, getLocalIP,
  buildPayload, applyPayload, PROTOCOL_VERSION, SCHEMA_VERSION,
  encryptPayload, decryptPayload, signRequest, verifyRequest
};
