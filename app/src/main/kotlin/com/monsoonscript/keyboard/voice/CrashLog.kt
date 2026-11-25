package com.monsoonscript.keyboard.voice

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A small record of problems for the owner and for me (G5): uncaught exceptions of the app process are written to a file before the process dies, and caught
 * ones (the voice code is wrapped so it cannot take the keyboard down) are written the same way. Shown and copied from Settings > Voice typing > Problem report.
 */
object CrashLog {
    private const val FILE = "problem-log.txt"
    private var installed = false

    fun install(context: Context) {
        if (installed) return
        installed = true
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                record(app, "UNCAUGHT in thread ${t.name}", e)
            } catch (x: Throwable) {
            }
            previous?.uncaughtException(t, e)
        }
    }

    @Synchronized
    fun record(context: Context, what: String, e: Throwable) {
        val sw = StringWriter()
        e.printStackTrace(PrintWriter(sw))
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val entry = "== $stamp  $what\n${sw.toString().take(3500)}\n"
        val f = File(context.filesDir, FILE)
        val old = if (f.isFile) f.readText() else ""
        f.writeText((entry + old).take(14_000))
    }

    fun read(context: Context): String = File(context.filesDir, FILE).let { if (it.isFile) it.readText() else "" }

    fun clear(context: Context) {
        File(context.filesDir, FILE).delete()
    }
}
