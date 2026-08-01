package com.bookkeeper.data.sync

import com.bookkeeper.data.local.DbKey
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 同步层加密 — 与桌面端 src/sync/crypto.js 完全一致
 *
 * - AES-256-GCM 包装 JSON payload（envelope: { v, iv, ct, tag }，全 base64）
 * - HMAC-SHA256 签名头 (X-Bk-Sig) — 防止网络中间人篡改
 *
 * 钥匙派生：SQLCipher KEY_B64 → SHA-256 → 32 字节
 *   HMAC_KEY = HMAC-SHA256(派生, "sync-hmac")
 *   GCM_KEY  = HMAC-SHA256(派生, "sync-aesgcm")
 *   防止 GCM / HMAC 之间的跨协议攻击
 */
object SyncCrypto {

    @PublishedApi
    internal val gcmKey: SecretKeySpec by lazy {
        val raw = android.util.Base64.decode(DbKey.KEY_B64_VALUE, android.util.Base64.DEFAULT)
        val derived = sha256(raw)
        val label = "sync-aesgcm".toByteArray(Charsets.UTF_8)
        SecretKeySpec(hmac(derived, label), "AES")
    }

    @PublishedApi
    internal val hmacKey: ByteArray by lazy {
        val raw = android.util.Base64.decode(DbKey.KEY_B64_VALUE, android.util.Base64.DEFAULT)
        val derived = sha256(raw)
        hmac(derived, "sync-hmac".toByteArray(Charsets.UTF_8))
    }

    fun encryptPayload(obj: Any): SyncEnvelope {
        val plain = SyncJson.json.encodeToString(
            kotlinx.serialization.serializer(obj::class.java), obj
        ).toByteArray(Charsets.UTF_8)
        val iv = ByteArray(12).also { java.security.SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, gcmKey, GCMParameterSpec(128, iv))
        val ct = cipher.doFinal(plain)
        return SyncEnvelope(
            v = 2,
            iv = android.util.Base64.encodeToString(iv, android.util.Base64.NO_WRAP),
            ct = android.util.Base64.encodeToString(ct.copyOfRange(0, ct.size - 16), android.util.Base64.NO_WRAP),
            tag = android.util.Base64.encodeToString(ct.copyOfRange(ct.size - 16, ct.size), android.util.Base64.NO_WRAP)
        )
    }

    inline fun <reified T> decryptPayload(env: SyncEnvelope): T {
        val iv = android.util.Base64.decode(env.iv, android.util.Base64.DEFAULT)
        val ct = android.util.Base64.decode(env.ct, android.util.Base64.DEFAULT)
        val tag = android.util.Base64.decode(env.tag, android.util.Base64.DEFAULT)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, gcmKey, GCMParameterSpec(128, iv))
        val combined = ct + tag
        val plain = cipher.doFinal(combined)
        return SyncJson.json.decodeFromString<T>(String(plain, Charsets.UTF_8))
    }

    fun signRequest(deviceId: String, ts: Long): String {
        val data = "$deviceId:$ts".toByteArray(Charsets.UTF_8)
        return hex(hmac(hmacKey, data))
    }

    fun verifyRequest(deviceId: String, ts: Long, sig: String): Boolean {
        if (deviceId.isEmpty() || sig.isEmpty()) return false
        if (Math.abs(System.currentTimeMillis() - ts) > SyncConfig.SIG_TIMESTAMP_WINDOW_MS) return false
        val expected = signRequest(deviceId, ts)
        return constantTimeEquals(expected, sig)
    }

    private fun sha256(data: ByteArray): ByteArray =
        java.security.MessageDigest.getInstance("SHA-256").digest(data)

    private fun hmac(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    private fun hex(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) sb.append(String.format("%02x", b.toInt() and 0xff))
        return sb.toString()
    }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].code xor b[i].code)
        return diff == 0
    }
}
