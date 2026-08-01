package com.bookkeeper.data.sync

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * 局域网发现 — 基于 Android NSD（mDNS / Bonjour）
 *
 * 发现 _bookkeeper._tcp.local 服务
 * 监听生命周期，释放 NSD 资源
 */
class NsdHelper(private val context: Context) {

    private val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager

    /**
     * 启动局域网发现，返回 Flow of LanPeer
     */
    fun discover(): Flow<LanPeer> = callbackFlow {
        val listener = object : NsdManager.DiscoveryListener {
            private val resolvedHosts = mutableSetOf<String>()

            override fun onDiscoveryStarted(regType: String) {
                Log.d("BkSyncNsd", "discover started: $regType")
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                if (service.serviceType.contains("_bookkeeper._tcp")) {
                    nsd.resolveService(service, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(s: NsdServiceInfo, error: Int) {
                            Log.w("BkSyncNsd", "resolve failed: $error for ${s.serviceName}")
                        }
                        override fun onServiceResolved(s: NsdServiceInfo) {
                            val host = s.host?.hostAddress ?: return
                            val name = s.serviceName
                            // TXT 记录里有 deviceId（API 21+ getAttributes）
                            val deviceId = if (Build.VERSION.SDK_INT >= 21) {
                                s.attributes?.get("deviceId")?.let { String(it) }
                            } else null
                            val peerKey = "$name@$host:${s.port}"
                            if (resolvedHosts.add(peerKey)) {
                                trySend(LanPeer(
                                    name = name,
                                    host = host,
                                    port = s.port,
                                    deviceId = deviceId
                                ))
                            }
                        }
                    })
                }
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                Log.d("BkSyncNsd", "service lost: ${service.serviceName}")
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.d("BkSyncNsd", "discover stopped: $serviceType")
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e("BkSyncNsd", "start discover failed: $errorCode")
                close(SyncException("NSD start failed: $errorCode"))
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e("BkSyncNsd", "stop discover failed: $errorCode")
            }
        }

        nsd.discoverServices(SyncConfig.MDNS_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        awaitClose {
            try { nsd.stopServiceDiscovery(listener) } catch (_: Exception) {}
        }
    }
}
