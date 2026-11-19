package com.monsoonscript.vault

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Phase 0 spike ONLY (never for main): the CI sends a broadcast here to prove that the SHRUNK release build still creates,
 * saves and reloads a vault with both key-derivation families (native code + shrinker). Result goes to the log tag VaultSmoke.
 */
class VaultSmokeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val pending = goAsync()
        Thread {
            val result = try {
                "ok " + VaultSpike.runAll(context.cacheDir).joinToString()
            } catch (t: Throwable) {
                var cause: Throwable? = t
                val chain = StringBuilder()
                while (cause != null) {
                    chain.append(cause.javaClass.name).append(": ").append(cause.message)
                    chain.append(" @ ").append(cause.stackTrace.take(5).joinToString(" <- ") { "${it.className}.${it.methodName}:${it.lineNumber}" })
                    cause = cause.cause
                    if (cause != null) chain.append(" || CAUSED BY ")
                }
                "FAILED: $chain"
            }
            Log.i("VaultSmoke", "RESULT: $result")
            pending.finish()
        }.start()
    }
}
