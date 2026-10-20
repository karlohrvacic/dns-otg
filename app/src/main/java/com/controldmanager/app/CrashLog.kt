package com.controldmanager.app

import android.content.Context
import android.os.Build
import java.io.File

/**
 * Keeps the last crash on the phone, so the app can show it next time it opens and the person can copy it into
 * a bug report. Nothing is sent anywhere; the text is the error and its stack trace, with app and Android version.
 */
object CrashLog {
    private fun file(ctx: Context) = File(ctx.filesDir, "last_crash.txt")

    fun install(ctx: Context) {
        val app = ctx.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            runCatching {
                val version = runCatching { app.packageManager.getPackageInfo(app.packageName, 0).versionName }.getOrNull()
                file(app).writeText(
                    "DNS OTG $version on Android ${Build.VERSION.RELEASE} (${Build.MANUFACTURER} ${Build.MODEL})\n" +
                        "Thread: ${thread.name}\n\n" + e.stackTraceToString().take(12_000)
                )
            }
            previous?.uncaughtException(thread, e)
        }
    }

    fun read(ctx: Context): String? = runCatching { file(ctx).takeIf { it.exists() }?.readText() }.getOrNull()

    fun clear(ctx: Context) { runCatching { file(ctx).delete() } }
}
