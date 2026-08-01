package com.bookkeeper.data.local

import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/**
 * 记账单 — 双端共享的数据库加密钥匙
 *
 * 两端（Android + Windows 桌面）的 SQLite 数据库用 SQLCipher 整库加密，
 * 钥匙统一硬编码在代码里。SQLCipher 默认对 key 派生 IV 和 HMAC 盐，
 * 所以同一把 32 字节 key 在两端互通。
 *
 * ⚠️ 注意：硬编码钥匙 = 拿到反编译代码 / 拆 APK 的人能解 db。
 * 适用场景：阻止「随手拷 db 看」「文件管理器翻到数据库」的被动泄露；
 * 不替代口令保护或服务端鉴权。
 */
object DbKey {
    // Base64 编码的 32 字节 key；运行期解码成 SecretKey。
    private const val B64: String = "uIXiS9OdESIRU8MQOKo6yjV1HhevuKoHc5K6r68PBuI="

    val secretKey: SecretKey by lazy {
        val raw = android.util.Base64.decode(B64, android.util.Base64.DEFAULT)
        SecretKeySpec(raw, "AES")
    }

    /** Passphrase 形式（hex），供 SQLCipher PRAGMA key 使用 */
    val passphraseHex: String by lazy {
        val raw = android.util.Base64.decode(B64, android.util.Base64.DEFAULT)
        raw.joinToString("") { "%02x".format(it) }
    }
}
