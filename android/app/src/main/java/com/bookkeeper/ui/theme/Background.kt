package com.bookkeeper.ui.theme

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext

/**
 * 持久化：背景图 + 背景可见度
 * 用 SharedPreferences 存 base64（图片通常 <500KB，base64 后 <700KB，可接受）
 *
 * visibility: 0..1
 *   0.0 = 背景图完全看不见（纯色背景）
 *   1.0 = 背景图完全显示（无蒙版）
 *   默认 0.85（背景图很显眼）
 */
object BackgroundSettings {
    private const val PREFS = "background_prefs"
    private const val KEY_IMG = "img_base64"
    private const val KEY_VISIBILITY = "visibility"  // 0..1 背景图可见度

    fun saveImage(context: Context, base64: String) {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        sp.edit().putString(KEY_IMG, base64).apply()
    }

    fun clearImage(context: Context) {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        sp.edit().remove(KEY_IMG).apply()
    }

    fun getImage(context: Context): String? {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return sp.getString(KEY_IMG, null)
    }

    fun setVisibility(context: Context, visibility: Float) {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        sp.edit().putFloat(KEY_VISIBILITY, visibility).apply()
    }

    /** 0..1，1=完全可见 */
    fun getVisibility(context: Context): Float {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return sp.getFloat(KEY_VISIBILITY, 0.85f)
    }
}

/**
 * 全局背景图 + 背景层
 * 用法：包在 Scaffold 之外或根 Box 之内
 *   Box { BackgroundLayer(); Scaffold { ... } }
 *
 * 渲染策略：
 *   1. 最底层：背景色
 *   2. 背景图（铺满）
 *   3. 蒙版（黑色 alpha = 1 - visibility）
 *   4. Surface（半透明，让背景图微微透出）
 */
@Composable
fun BackgroundLayer(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var bgBitmap by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var visibility by remember { mutableStateOf(BackgroundSettings.getVisibility(context)) }

    // 加载背景图
    LaunchedEffect(Unit) {
        val b64 = BackgroundSettings.getImage(context)
        if (b64 != null) {
            try {
                val bytes = Base64.decode(b64, Base64.DEFAULT)
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                bgBitmap = bmp?.asImageBitmap()
            } catch (e: Exception) {
                // 图片损坏 → 清掉
                BackgroundSettings.clearImage(context)
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // 底层 1：背景色（保证文字始终可读）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        )
        // 底层 2：背景图（铺满，如果设置了）
        bgBitmap?.let { img ->
            Image(
                bitmap = img,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        // 顶层：黑色蒙版（控制背景图可见度）
        //   visibility = 1.0 → 蒙版 alpha 0 → 背景图全显
        //   visibility = 0.0 → 蒙版 alpha 1 → 背景图全黑（相当于没设）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 1f - visibility))
        )
    }
}
