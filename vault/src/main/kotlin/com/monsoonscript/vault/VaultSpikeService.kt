package com.monsoonscript.vault

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log

/** Phase 0 spike: runs the round trip inside the ":vault" process. Referenced from the manifest so the shrinker keeps the whole path. */
class VaultSpikeService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Thread {
            val report = runCatching { VaultSpike.runAll(cacheDir).joinToString() }.getOrElse { "FAILED: $it" }
            Log.i("VaultSpike", report)
            stopSelf(startId)
        }.start()
        return START_NOT_STICKY
    }
}
