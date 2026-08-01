package com.bookkeeper.ui.sync

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bookkeeper.data.sync.LanPeer
import com.bookkeeper.data.sync.NsdHelper
import com.bookkeeper.data.sync.SyncException
import com.bookkeeper.data.sync.SyncRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SyncViewModel @Inject constructor(
    application: Application,
    private val repo: SyncRepository,
    private val nsd: NsdHelper
) : AndroidViewModel(application) {

    private val _ui = MutableStateFlow(SyncUiState())
    val ui: StateFlow<SyncUiState> = _ui.asStateFlow()

    private var discoveryJob: Job? = null
    private val seenPeerKeys = mutableSetOf<String>()

    fun toggleDiscovery(enable: Boolean) {
        if (enable) startDiscovery() else stopDiscovery()
    }

    private fun startDiscovery() {
        if (discoveryJob?.isActive == true) return
        seenPeerKeys.clear()
        _ui.value = _ui.value.copy(discoveryRunning = true, lastError = null)
        discoveryJob = viewModelScope.launch {
            try {
                nsd.discover()
                    .catch { e ->
                        Log.e("SyncViewModel", "discover error", e)
                        _ui.value = _ui.value.copy(
                            discoveryRunning = false,
                            lastError = e.message ?: "发现失败"
                        )
                    }
                    .collect { peer ->
                        val key = "${peer.host}:${peer.port}"
                        if (seenPeerKeys.add(key)) {
                            _ui.value = _ui.value.copy(
                                peers = _ui.value.peers + peer
                            )
                        }
                    }
            } finally {
                _ui.value = _ui.value.copy(discoveryRunning = false)
            }
        }
    }

    private fun stopDiscovery() {
        discoveryJob?.cancel()
        discoveryJob = null
        _ui.value = _ui.value.copy(discoveryRunning = false, peers = emptyList())
    }

    fun syncWith(peer: LanPeer) {
        if (_ui.value.syncing) return
        _ui.value = _ui.value.copy(syncing = true, lastError = null, lastResult = null)
        viewModelScope.launch {
            val result = repo.syncWith(peer)
            _ui.value = result.fold(
                onSuccess = { _ui.value.copy(
                    syncing = false,
                    lastResult = it,
                    lastSyncAt = System.currentTimeMillis()
                ) },
                onFailure = { _ui.value.copy(
                    syncing = false,
                    lastError = it.message ?: "同步失败"
                ) }
            )
        }
    }

    fun syncWithUrl(url: String) {
        if (url.isBlank() || _ui.value.syncing) return
        _ui.value = _ui.value.copy(syncing = true, lastError = null, lastResult = null)
        viewModelScope.launch {
            val result = repo.syncWithUrl(url.trim())
            _ui.value = result.fold(
                onSuccess = { _ui.value.copy(
                    syncing = false,
                    lastResult = it,
                    lastSyncAt = System.currentTimeMillis()
                ) },
                onFailure = { _ui.value.copy(
                    syncing = false,
                    lastError = it.message ?: "同步失败"
                ) }
            )
        }
    }

    fun clearError() {
        _ui.value = _ui.value.copy(lastError = null)
    }

    override fun onCleared() {
        super.onCleared()
        stopDiscovery()
    }
}

data class SyncUiState(
    val discoveryRunning: Boolean = false,
    val syncing: Boolean = false,
    val peers: List<LanPeer> = emptyList(),
    val lastError: String? = null,
    val lastResult: com.bookkeeper.data.sync.SyncSummary? = null,
    val lastSyncAt: Long = 0L
)
