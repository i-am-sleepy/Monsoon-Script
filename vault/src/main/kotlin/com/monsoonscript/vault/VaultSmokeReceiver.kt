package com.monsoonscript.vault

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.kunzisoft.keepass.database.exception.DatabaseException

/**
 * Phase 0 spike ONLY (never for main): the CI sends a broadcast here to prove that the SHRUNK release build still creates,
 * saves and reloads a vault with both key-derivation families (native code + shrinker). Result goes to the log tag VaultSmoke.
 * The engine's DatabaseException keeps the real cause in its own field (mThrowable), not in the standard cause chain.
 */
class VaultSmokeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val pending = goAsync()
        Thread {
            val result = try {
                "ok " + VaultSpike.runAll(context.cacheDir).joinToString()
            } catch (t: Throwable) {
                "FAILED: " + describe(t)
            }
            Log.i("VaultSmoke", "RESULT: $result")
            pending.finish()
        }.start()
    }

    private fun describe(t: Throwable): String {
        val sb = StringBuilder()
        var current: Throwable? = t
        var depth = 0
        while (current != null && depth < 6) {
            val c: Throwable = current
            sb.append(c.javaClass.name).append(": ").append(c.message)
            if (c is DatabaseException) sb.append(" [inner=").append(c.innerMessage).append("]")
            sb.append(" @ ").append(c.stackTrace.take(6).joinToString(" <- ") { "${it.className}.${it.methodName}:${it.lineNumber}" })
            current = if (c is DatabaseException && c.mThrowable != null) c.mThrowable else c.cause
            if (current != null) sb.append(" || CAUSED BY ")
            depth++
        }
        return sb.toString()
    }
}
