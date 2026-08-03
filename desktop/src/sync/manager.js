/**
 * SyncManager 主体
 *
 * 暴露在主进程（`src/main/index.js`）中以单例方式管理：
 *  - 启动 / 停止同步 HTTP 服务
 *  - 启动 / 停止 mDNS 局域网广播与发现
 *  - 与指定 peer 或 URL 同步（推送 + 拉取）
 *
 * 之所以单独放在 `manager.js` 而不是 `index.js`，是因为
 * `index.js` 仍承担 re-export 聚合入口的角色（被主进程 require），
 * 如果让 `index.js` 又定义类又 require 自己，会触发 Node 循环依赖
 * 警告（"Accessing non-existent property 'SyncManager' of module
 * exports inside circular dependency"）。
 */

const { app } = require('electron');
const path = require('path');
const crypto = require('crypto');
const { SyncServer, SyncClient } = require('./server');
const { LanSyncMdns } = require('./mdns');
const { buildPayload, applyPayload } = require('./protocol');

// 设备 ID：启动时生成，存到 userData 目录
const DEVICE_ID_FILE = () => path.join(app.getPath('userData'), 'device-id');

function getDeviceId() {
  const fs = require('fs');
  const f = DEVICE_ID_FILE();
  try {
    if (fs.existsSync(f)) return fs.readFileSync(f, 'utf-8').trim();
  } catch (e) {}
  const id = crypto.randomBytes(8).toString('hex');
  try {
    fs.mkdirSync(path.dirname(f), { recursive: true });
    fs.writeFileSync(f, id, 'utf-8');
  } catch (e) {}
  return id;
}

class SyncManager {
  constructor({ db }) {
    this.db = db;
    this.deviceId = getDeviceId();
    this.server = null;
    this.mdns = new LanSyncMdns();
    this.state = {
      serverRunning: false,
      serverUrl: null,
      serverIP: null,
      serverPort: null,
      peers: [],
      lastSyncAt: {}
    };
    this._notify = null;
  }

  setNotifier(fn) { this._notify = fn; }

  async startServer() {
    if (this.server) return this.state;
    this.server = new SyncServer({ db: this.db, deviceId: this.deviceId });
    const { port } = await this.server.start();
    const ad = this.mdns.startAdvertising(this.deviceId, port);
    this.state.serverRunning = true;
    this.state.serverUrl = `http://${ad.ip}:${port}`;
    this.state.serverIP = ad.ip;
    this.state.serverPort = port;
    this._notify?.('server-started', this.state);
    return this.state;
  }

  async stopServer() {
    if (this.server) {
      await this.server.stop();
      this.server = null;
    }
    this.mdns.stopAdvertising();
    this.state.serverRunning = false;
    this.state.serverUrl = null;
    this.state.serverIP = null;
    this.state.serverPort = null;
    this._notify?.('server-stopped', this.state);
    return this.state;
  }

  startDiscovery() {
    this.mdns.startDiscovery((peers) => {
      this.state.peers = peers;
      this._notify?.('peers-changed', peers);
    });
  }

  stopDiscovery() {
    this.mdns.stopDiscovery();
    this.state.peers = [];
    this._notify?.('peers-changed', []);
  }

  async syncWith(peer, sinceTs = 0) {
    const url = `http://${peer.host}:${peer.port}`;
    const client = new SyncClient({ deviceId: this.deviceId, baseUrl: url });
    const localPayload = buildPayload(this.db, this.deviceId, sinceTs);
    const resp = await client.sync(localPayload);
    let applied = resp.applied;
    if (resp.peer) {
      const more = applyPayload(this.db, resp.peer);
      applied = {
        inserted: applied.inserted + more.inserted,
        updated: applied.updated + more.updated,
        tombstoned: applied.tombstoned + more.tombstoned,
        at: Date.now()
      };
    }
    this.state.lastSyncAt[peer.deviceId || peer.name] = applied.at;
    this._notify?.('sync-completed', { peer, applied });
    return applied;
  }

  async syncWithUrl(url, sinceTs = 0) {
    const client = new SyncClient({ deviceId: this.deviceId, baseUrl: url });
    await client.ping();
    const localPayload = buildPayload(this.db, this.deviceId, sinceTs);
    const resp = await client.sync(localPayload);
    let applied = resp.applied;
    if (resp.peer) {
      const more = applyPayload(this.db, resp.peer);
      applied = { ...applied,
        inserted: applied.inserted + more.inserted,
        updated: applied.updated + more.updated,
        tombstoned: applied.tombstoned + more.tombstoned,
        at: Date.now() };
    }
    this._notify?.('sync-completed', { peer: { host: url }, applied });
    return applied;
  }

  getState() { return { ...this.state, deviceId: this.deviceId }; }

  stop() {
    this.stopDiscovery();
    return this.stopServer();
  }
}

module.exports = { SyncManager, getDeviceId };
