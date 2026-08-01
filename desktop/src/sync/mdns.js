/**
 * 局域网 mDNS 广播 + 发现
 *
 * 使用 bonjour-service 库（Windows 自带 mDNS 协议栈，无需额外配置）
 * - 本机作为 hub：广播 _bookkeeper._tcp.local:17860
 * - 客户端：发现同 WiFi 下的所有 _bookkeeper._tcp 服务
 */

const os = require('os');
const Bonjour = require('bonjour-service');

const { MDNS_SERVICE_TYPE, MDNS_DOMAIN, DEFAULT_PORT } = require('./protocol');

function getLocalIP() {
  // 取第一张非 127.0.0.1 的 IPv4
  const ifaces = os.networkInterfaces();
  for (const name of Object.keys(ifaces)) {
    for (const iface of ifaces[name]) {
      if (iface.family === 'IPv4' && !iface.internal) return iface.address;
    }
  }
  return '127.0.0.1';
}

class LanSyncMdns {
  constructor() {
    this.bonjour = null;
    this.advertiser = null;
    this.browser = null;
    this._peers = new Map();
    this._onPeersChanged = null;
  }

  startAdvertising(deviceId, port = DEFAULT_PORT) {
    this.bonjour = this.bonjour || Bonjour();
    this.stopAdvertising();
    const localIP = getLocalIP();
    this.advertiser = this.bonjour.publish({
      name: `Bookkeeper-${deviceId.slice(0, 8)}`,
      type: MDNS_SERVICE_TYPE,
      port,
      host: localIP,
      txt: { deviceId, version: '2' }
    });
    return { ip: localIP, port };
  }

  stopAdvertising() {
    if (this.advertiser) {
      try { this.advertiser.stop(); } catch (e) {}
      this.advertiser = null;
    }
  }

  startDiscovery(onPeersChanged) {
    this.bonjour = this.bonjour || Bonjour();
    this._onPeersChanged = onPeersChanged;
    this.stopDiscovery();
    this.browser = this.bonjour.find({ type: MDNS_SERVICE_TYPE });
    this.browser.on('up', (svc) => {
      this._peers.set(svc.name, {
        name: svc.name,
        host: svc.host || svc.addresses?.[0],
        port: svc.port,
        deviceId: svc.txt?.deviceId,
        version: svc.txt?.version
      });
      this._onPeersChanged?.(this.getPeers());
    });
    this.browser.on('down', (svc) => {
      this._peers.delete(svc.name);
      this._onPeersChanged?.(this.getPeers());
    });
  }

  stopDiscovery() {
    if (this.browser) {
      try { this.browser.stop(); } catch (e) {}
      this.browser = null;
    }
  }

  getPeers() {
    return Array.from(this._peers.values());
  }

  stop() {
    this.stopAdvertising();
    this.stopDiscovery();
    if (this.bonjour) {
      try { this.bonjour.destroy(); } catch (e) {}
      this.bonjour = null;
    }
  }
}

module.exports = { LanSyncMdns, getLocalIP };
