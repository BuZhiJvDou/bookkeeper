package com.bookkeeper

import android.app.Application
import android.util.Log
import dagger.hilt.android.HiltAndroidApp
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * 尽量精简 Application：不做字段注入、不做启动期重活，
 * 避免部分国产 ROM 在 Application 阶段初始化 Hilt 依赖时闪退。
 */
@HiltAndroidApp
class BookkeeperApp : Application() {

    override fun onCreate() {
        super.onCreate()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                writeCrashLog(throwable)
            } catch (_: Exception) {
                // ignore
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    private fun writeCrashLog(t: Throwable) {
        val sw = StringWriter()
        t.printStackTrace(PrintWriter(sw))
        val text = sw.toString()
        Log.e(TAG, text)
        val dir = getExternalFilesDir(null) ?: filesDir
        File(dir, "crash.log").writeText("${System.currentTimeMillis()}\n$text")
        // 再写一份到内部，双保险
        File(filesDir, "crash.log").writeText("${System.currentTimeMillis()}\n$text")
    }

    companion object {
        private const val TAG = "BookkeeperApp"
    }
}
