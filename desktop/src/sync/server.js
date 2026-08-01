/**
 * 同步 HTTP 服务 + 客户端
 *
 * - 桌面端可作为服务端（局域网 hub）开启 HTTP，监听 17860
 * - 任何设备（桌面 / Android）可作为客户端连服务端 URL
 *
 * 三接口：
 *   GET  /api/v2/ping        健康检查
 *   POST /api/v2/sync        客户端推送 + 服务端返回增量
 *   GET  /api/v2/snapshot    服务端返回指定时间戳后的全量（拉取）
 */

const http = require('http');
const { URL } = require('url');
const { DEFAULT_PORT, HDR_DEVICE, HDR_TS, HDR_SIG,
        HDR_NONCE, PATH_PING, PATH_SYNC, PATH_SNAPSHOT,
        HTTP_TIMEOUT_MS } = require('./protocol');
const { encryptPayload, decryptPayload, signRequest, verifyRequest } = require('./crypto');
const { PROTOCOL_VERSION, buildPayload, applyPayload } = require('./protocol');

class SyncServer {
  constructor({ db, deviceId, port = DEFAULT_PORT }) {
    this.db = db;
    this.deviceId = deviceId;
    this.port = port;
    this.server = null;
    this.lastSyncAt = {}; // deviceId → ms
  }

  start() {
    return new Promise((resolve, reject) => {
      this.server = http.createServer((req, res) => this._handle(req, res));
      this.server.once('error', reject);
      this.server.listen(this.port, () => {
        const addr = this.server.address();
        this.port = addr.port;
        resolve({ port: this.port });
      });
    });
  }

  stop() {
    return new Promise((resolve) => {
      if (!this.server) return resolve();
      this.server.close(() => resolve());
    });
  }

  _readBody(req) {
    return new Promise((resolve, reject) => {
      const chunks = [];
      req.on('data', c => chunks.push(c));
      req.on('end', () => {
        const buf = Buffer.concat(chunks);
        if (buf.length === 0) return resolve(null);
        try {
          resolve(JSON.parse(buf.toString('utf-8')));
        } catch (e) { reject(e); }
      });
      req.on('error', reject);
    });
  }

  _send(res, code, obj) {
    const body = JSON.stringify(obj);
    res.writeHead(code, { 'Content-Type': 'application/json' });
    res.end(body);
  }

  async _handle(req, res) {
    try {
      const url = new URL(req.url, `http://localhost:${this.port}`);
      // 鉴权
      const device = req.headers[HDR_DEVICE.toLowerCase()];
      const ts = req.headers[HDR_TS.toLowerCase()];
      const sig = req.headers[HDR_SIG.toLowerCase()];
      if (!verifyRequest(device, ts, sig)) {
        return this._send(res, 401, { error: 'unauthorized' });
      }

      if (url.pathname === PATH_PING && req.method === 'GET') {
        return this._send(res, 200, {
          ok: true,
          version: PROTOCOL_VERSION,
          deviceId: this.deviceId,
          ts: Date.now()
        });
      }

      if (url.pathname === PATH_SYNC && req.method === 'POST') {
        const env = await this._readBody(req);
        if (!env) return this._send(res, 400, { error: 'empty body' });
        let payload;
        try { payload = decryptPayload(env); }
        catch (e) { return this._send(res, 400, { error: 'decrypt failed' }); }

        if (payload.version !== PROTOCOL_VERSION) {
          return this._send(res, 400, { error: 'version mismatch' });
        }

        // 1. 接收客户端推送 → 写入本地
        const result = applyPayload(this.db, payload);
        // 2. 返回服务端增量（clientTs 之后的服务端变更）
        const respPayload = buildPayload(this.db, this.deviceId, payload.clientTs);
        const respEnv = encryptPayload(respPayload);
        this.lastSyncAt[device] = Date.now();
        return this._send(res, 200, { ok: true, applied: result, envelope: respEnv });
      }

      if (url.pathname === PATH_SNAPSHOT && req.method === 'GET') {
        const since = Number(url.searchParams.get('since')) || 0;
        const payload = buildPayload(this.db, this.deviceId, since);
        const env = encryptPayload(payload);
        return this._send(res, 200, { ok: true, envelope: env });
      }

      this._send(res, 404, { error: 'not found' });
    } catch (e) {
      this._send(res, 500, { error: e.message });
    }
  }
}

class SyncClient {
  constructor({ deviceId, baseUrl }) {
    this.deviceId = deviceId;
    this.baseUrl = baseUrl.replace(/\/$/, '');
  }

  _request({ method = 'GET', path, body }) {
    return new Promise((resolve, reject) => {
      const u = this.baseUrl + path;
      const ts = String(Date.now());
      const sig = signRequest(this.deviceId, ts);
      let urlObj;
      try { urlObj = new URL(u); }
      catch (e) { return reject(new Error('invalid baseUrl: ' + u)); }
      const opts = {
        method,
        hostname: urlObj.hostname,
        port: urlObj.port || (urlObj.protocol === 'https:' ? 443 : 80),
        path: (urlObj.pathname || '/') + (urlObj.search || ''),
        headers: {
          'Content-Type': 'application/json',
          [HDR_DEVICE]: this.deviceId,
          [HDR_TS]: ts,
          [HDR_SIG]: sig
        },
        timeout: HTTP_TIMEOUT_MS
      };
      const lib = urlObj.protocol === 'https:' ? require('https') : http;
      const req = lib.request(opts, (res) => {
        const chunks = [];
        res.on('data', c => chunks.push(c));
        res.on('end', () => {
          const text = Buffer.concat(chunks).toString('utf-8');
          if (res.statusCode !== 200) {
            return reject(new Error(`HTTP ${res.statusCode}: ${text}`));
          }
          try { resolve(JSON.parse(text)); }
          catch (e) { reject(e); }
        });
      });
      req.on('error', reject);
      req.on('timeout', () => { req.destroy(new Error('timeout')); });
      if (body != null) req.write(JSON.stringify(body));
      req.end();
    });
  }

  async ping() {
    return this._request({ method: 'GET', path: PATH_PING });
  }

  /**
   * 推送本地 payload，并接收服务端增量
   */
  async sync(localPayload) {
    const env = encryptPayload(localPayload);
    const resp = await this._request({ method: 'POST', path: PATH_SYNC, body: env });
    if (!resp.envelope) return { ok: resp.ok, applied: resp.applied, peer: null };
    const peerPayload = decryptPayload(resp.envelope);
    return { ok: true, applied: resp.applied, peer: peerPayload };
  }

  /**
   * 拉取服务端指定时间戳之后的全量
   */
  async snapshot(sinceTs = 0) {
    const resp = await this._request({ method: 'GET', path: `${PATH_SNAPSHOT}?since=${sinceTs}` });
    if (!resp.envelope) return null;
    return decryptPayload(resp.envelope);
  }
}

module.exports = { SyncServer, SyncClient };
