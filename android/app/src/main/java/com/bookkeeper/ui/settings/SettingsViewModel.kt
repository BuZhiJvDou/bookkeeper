package com.bookkeeper.ui.settings

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bookkeeper.data.sync.SyncManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 设置页 ViewModel：负责数据导入/导出。
 * 实际读写文件由 UI 层通过 SAF (Storage Access Framework) 完成，
 * 这里只提供 JSON/CSV 字符串生成与 JSON 解析导入。
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val syncManager: SyncManager
) : ViewModel() {

    /** 一次性提示消息（Snackbar 用），消费后置空 */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val deviceId: String = "android-${Build.MODEL}".replace(" ", "_")

    /** 生成导出 JSON 字符串，交给回调写入用户选择的文件 */
    fun buildExportJson(onReady: (String) -> Unit) {
        viewModelScope.launch {
            try {
                onReady(syncManager.exportData(deviceId))
            } catch (e: Exception) {
                _message.value = "导出失败: ${e.message}"
            }
        }
    }

    /** 生成导出 CSV 字符串 */
    fun buildExportCsv(onReady: (String) -> Unit) {
        viewModelScope.launch {
            try {
                onReady(syncManager.exportToCsv())
            } catch (e: Exception) {
                _message.value = "导出失败: ${e.message}"
            }
        }
    }

    /** 从用户选择文件读取的 JSON 字符串导入 */
    fun importJson(jsonString: String) {
        viewModelScope.launch {
            val result = syncManager.importFromJson(jsonString)
            _message.value = result.fold(
                onSuccess = { "导入成功，新增 $it 条记录" },
                onFailure = { "导入失败: ${it.message}" }
            )
        }
    }

    fun setMessage(msg: String) { _message.value = msg }
    fun clearMessage() { _message.value = null }
}
