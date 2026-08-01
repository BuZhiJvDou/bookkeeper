package com.bookkeeper.data.sync

import android.content.Context
import android.os.Build
import android.provider.Settings
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/**
 * 同步层 v2 — 网络同步（局域网 + 跨网段）
 *
 * 协议与桌面端 src/sync 完全一致：
 *   - deviceId: 启动时随机生成并存到 app 文件
 *   - 头: X-Bk-Device / X-Bk-Ts / X-Bk-Sig
 *   - body: AES-256-GCM(JSON) envelope
 *   - 端口: 17860
 *
 * 桌面端 README 注释也写了相同逻辑，互通。
 */

object SyncConfig {
    const val PROTOCOL_VERSION = 2
    const val SCHEMA_VERSION = 1
    const val DEFAULT_PORT = 17860
    const val MDNS_SERVICE_TYPE = "_bookkeeper._tcp."
    const val HTTP_TIMEOUT_MS = 30000L
    const val SIG_TIMESTAMP_WINDOW_MS = 5 * 60 * 1000L

    const val HDR_DEVICE = "X-Bk-Device"
    const val HDR_TS = "X-Bk-Ts"
    const val HDR_SIG = "X-Bk-Sig"

    const val PATH_PING = "/api/v2/ping"
    const val PATH_SYNC = "/api/v2/sync"
    const val PATH_SNAPSHOT = "/api/v2/snapshot"
}

@Serializable
data class SyncEnvelope(
    val v: Int = 2,
    val iv: String,    // base64
    val ct: String,    // base64
    val tag: String    // base64
)

@Serializable
data class SyncPayload(
    val version: Int = SyncConfig.PROTOCOL_VERSION,
    val schemaVersion: Int = SyncConfig.SCHEMA_VERSION,
    val deviceId: String,
    val clientTs: Long,
    val data: SyncData
)

@Serializable
data class SyncData(
    val transactions: List<com.bookkeeper.domain.model.Transaction> = emptyList(),
    val categories: List<com.bookkeeper.domain.model.Category> = emptyList(),
    val accounts: List<com.bookkeeper.domain.model.Account> = emptyList(),
    val budgets: List<com.bookkeeper.domain.model.Budget> = emptyList(),
    val recurring: List<com.bookkeeper.domain.model.RecurringRule> = emptyList()
)

@Serializable
data class SyncApplyResult(
    val inserted: Int,
    val updated: Int,
    val tombstoned: Int,
    val at: Long
)

@Serializable
data class PingResponse(
    val ok: Boolean,
    val version: Int,
    val deviceId: String,
    val ts: Long
)

@Serializable
data class SyncResponse(
    val ok: Boolean,
    val applied: SyncApplyResult? = null,
    val envelope: SyncEnvelope? = null,
    val error: String? = null
)

/**
 * 设备 ID 生成与持久化
 * 跨局域网/公网模式下保持稳定，便于增量同步
 */
object DeviceId {
    private const val FILE = "device_id"

    fun get(context: Context): String {
        val f = File(context.filesDir, FILE)
        if (f.exists()) {
            return f.readText().trim()
        }
        val raw = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?: UUID.randomUUID().toString()
        // 取 SHA-like short id：8 字节 hex
        val id = UUID.nameUUIDFromBytes(raw.toByteArray()).toString().take(16)
        f.writeText(id)
        return id
    }
}

object SyncJson {
    val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
}

data class LanPeer(
    val name: String,
    val host: String,
    val port: Int,
    val deviceId: String? = null
)
