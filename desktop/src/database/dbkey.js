/**
 * 记账单 — 双端共享的数据库加密钥匙
 *
 * 桌面端 (Windows / macOS / Linux) 与 Android 端共用同一把钥匙。
 * SQLCipher 默认对 key 派生 IV 和 HMAC 盐，所以两端互通。
 *
 * ⚠️ 硬编码钥匙 = 拿到反编译/拆包的人能解 db。
 * 适用场景：阻止「随手拷 db 看」「文件管理器翻到数据库」的被动泄露；
 * 不替代口令保护或服务端鉴权。
 */
const crypto = require('crypto');

const KEY_B64 = 'uIXiS9OdESIRU8MQOKo6yjV1HhevuKoHc5K6r68PBuI=';

// SQLCipher PRAGMA key 接受任意字符串；这里用 hex 形式确保两端字节一致
function getPassphrase() {
  const buf = Buffer.from(KEY_B64, 'base64');
  return buf.toString('hex');
}

module.exports = { getPassphrase, KEY_B64 };
