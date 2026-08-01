package com.bookkeeper.data.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * 同步客户端 — OkHttp 实现
 *
 * 桌面端 src/sync/server.js 用 Node.js http 模块
 * 这里用 OkHttp 互相对接，body/header 一致
 */
class SyncClient(
    val deviceId: String,
    var baseUrl: String
) {
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(SyncConfig.HTTP_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .readTimeout(SyncConfig.HTTP_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .writeTimeout(SyncConfig.HTTP_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .build()

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    private fun signHeaders(req: Request.Builder): Request.Builder {
        val ts = System.currentTimeMillis()
        val sig = SyncCrypto.signRequest(deviceId, ts)
        return req
            .header(SyncConfig.HDR_DEVICE, deviceId)
            .header(SyncConfig.HDR_TS, ts.toString())
            .header(SyncConfig.HDR_SIG, sig)
    }

    private suspend inline fun <reified T> request(
        method: String,
        path: String,
        body: String? = null
    ): T = withContext(Dispatchers.IO) {
        val url = URL(baseUrl.trimEnd('/') + path)
        val builder = Request.Builder().url(url)
        val signed = signHeaders(builder)
        val rb = body?.toRequestBody(jsonMedia)
        val finalReq = when (method) {
            "GET" -> signed.get().build()
            "POST" -> signed.post(rb ?: byteArrayOf().toRequestBody(jsonMedia)).build()
            else -> throw IllegalArgumentException("unsupported method: $method")
        }
        http.newCall(finalReq).execute().use { resp ->
            val text = resp.body?.string() ?: ""
            if (!resp.isSuccessful) {
                throw SyncException("HTTP ${resp.code}: $text")
            }
            SyncJson.json.decodeFromString<T>(text)
        }
    }

    suspend fun ping(): PingResponse =
        request<PingResponse>("GET", SyncConfig.PATH_PING)

    suspend fun sync(payload: SyncPayload): SyncResponse {
        val env = SyncCrypto.encryptPayload(payload)
        val body = SyncJson.json.encodeToString(SyncEnvelope.serializer(), env)
        return request<SyncResponse>("POST", SyncConfig.PATH_SYNC, body)
    }

    suspend fun snapshot(sinceTs: Long = 0): SyncPayload {
        val resp = request<SyncResponse>("GET", "${SyncConfig.PATH_SNAPSHOT}?since=$sinceTs")
        val env = resp.envelope ?: throw SyncException("no envelope in snapshot")
        return SyncCrypto.decryptPayload<SyncPayload>(env)
    }
}

class SyncException(message: String) : RuntimeException(message)
