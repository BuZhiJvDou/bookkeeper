package com.bookkeeper.ui.sync

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bookkeeper.data.sync.LanPeer
import com.bookkeeper.data.sync.SyncSummary

/**
 * 局域网 / 跨网段同步界面
 * - 同 WiFi 自动发现
 * - 一键同步
 * - 公网/跨网段 URL 输入
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncScreen(
    onBack: () -> Unit,
    viewModel: SyncViewModel = hiltViewModel()
) {
    val ui by viewModel.ui.collectAsState()
    var urlInput by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("局域网同步") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.Refresh, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 局域网发现
            Card(shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("局域网设备", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Switch(
                            checked = ui.discoveryRunning,
                            onCheckedChange = { viewModel.toggleDiscovery(it) }
                        )
                    }
                    if (ui.discoveryRunning && ui.peers.isEmpty()) {
                        Text("搜索中…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    ui.peers.forEach { peer ->
                        PeerRow(peer, syncing = ui.syncing) {
                            viewModel.syncWith(peer)
                        }
                    }
                }
            }

            // 公网 / 跨网段 URL
            Card(shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("跨网段 / 公网同步", fontWeight = FontWeight.SemiBold)
                    Text(
                        "在另一台设备打开「同步服务」开关，把这里的 URL 填进去即可（支持 Cloudflare Tunnel / Tailscale / 自有服务器）。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        label = { Text("https://… 或 http://192.168.x.x:17860") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = { viewModel.syncWithUrl(urlInput) },
                        enabled = urlInput.isNotBlank() && !ui.syncing,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("同步")
                    }
                }
            }

            // 状态
            if (ui.syncing) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text("同步中…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            ui.lastError?.let {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text(
                        it,
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
            ui.lastResult?.let { ResultCard(it) }
        }
    }
}

@Composable
private fun PeerRow(peer: LanPeer, syncing: Boolean, onSync: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(peer.name, fontWeight = FontWeight.Medium)
            Text(
                "${peer.host}:${peer.port}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Button(onClick = onSync, enabled = !syncing) { Text("同步") }
    }
}

@Composable
private fun ResultCard(r: SyncSummary) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("同步完成", fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSecondaryContainer)
            Text("推送到对方：新增 ${r.pushed.inserted} / 更新 ${r.pushed.updated} / 删除 ${r.pushed.tombstoned}",
                color = MaterialTheme.colorScheme.onSecondaryContainer)
            Text("从对方拉取：新增 ${r.pulled.inserted} / 更新 ${r.pulled.updated} / 删除 ${r.pulled.tombstoned}",
                color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}
