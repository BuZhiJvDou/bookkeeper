/**
 * 加密工具：复用 SQLCipher 钥匙做 AES-256-GCM + HMAC-SHA256
 *
 * 目的：把整个 JSON 同步包再用 AES-GCM 包一层，
 * 防止 Cloudflare / 中转服务器看到明文。
 *
 * - payloadEnc: AES-256-GCM(JSON.stringify(payload))
 *   输出格式：{ iv: base64, ct: base64, tag: base64 }
 * - sig: HMAC-SHA256(KEY, deviceId + ':' + ts) → hex（小写）
 *
 * 钥匙 32 字节从 SQLCipher 用的 base64 派生出来：
 *   key = SHA-256(KEY_B64) 前 32 字节
 *   （GCM 限制 IV 12 字节，HMAC 限制 32 字节；这里用 HKDF-like 派生）
 */

const crypto = require('crypto');
const { KEY_B64 } = require('../database/dbkey');

// 派生 32 字节对称钥匙（AES-256 + HMAC-256 共用）
const RAW_KEY = Buffer.from(KEY_B64, 'base64');
const DERIVED = crypto.createHash('sha256').update(RAW_KEY).digest(); // 32 bytes
// 用 label 区分 HMAC 和 GCM（防 cross-protocol 攻击）
const HMAC_KEY = crypto.createHmac('sha256', DERIVED).update('sync-hmac').digest();
const GCM_KEY = crypto.createHmac('sha256', DERIVED).update('sync-aesgcm').digest();

function encryptPayload(obj) {
  const iv = crypto.randomBytes(12);
  const cipher = crypto.createCipheriv('aes-256-gcm', GCM_KEY, iv);
  const plain = Buffer.from(JSON.stringify(obj), 'utf-8');
  const ct = Buffer.concat([cipher.update(plain), cipher.final()]);
  const tag = cipher.getAuthTag();
  return {
    v: 2,
    iv: iv.toString('base64'),
    ct: ct.toString('base64'),
    tag: tag.toString('base64')
  };
}

function decryptPayload(env) {
  if (!env || env.v !== 2) throw new Error('unsupported envelope version');
  const iv = Buffer.from(env.iv, 'base64');
  const ct = Buffer.from(env.ct, 'base64');
  const tag = Buffer.from(env.tag, 'base64');
  const decipher = crypto.createDecipheriv('aes-256-gcm', GCM_KEY, iv);
  decipher.setAuthTag(tag);
  const plain = Buffer.concat([decipher.update(ct), decipher.final()]);
  return JSON.parse(plain.toString('utf-8'));
}

function signRequest(deviceId, ts) {
  const mac = crypto.createHmac('sha256', HMAC_KEY);
  mac.update(`${deviceId}:${ts}`);
  return mac.digest('hex');
}

function verifyRequest(deviceId, ts, sig) {
  if (!deviceId || !ts || !sig) return false;
  const now = Date.now();
  if (Math.abs(now - Number(ts)) > 5 * 60 * 1000) return false; // ±5min
  const expected = signRequest(deviceId, ts);
  if (expected.length !== sig.length) return false;
  return crypto.timingSafeEqual(Buffer.from(expected), Buffer.from(sig));
}

module.exports = { encryptPayload, decryptPayload, signRequest, verifyRequest };
