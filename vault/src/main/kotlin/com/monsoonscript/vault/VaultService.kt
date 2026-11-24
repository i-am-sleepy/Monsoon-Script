package com.monsoonscript.vault

import android.app.ActivityManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Process
import com.kunzisoft.keepass.database.crypto.kdf.KdfLimits
import com.kunzisoft.keepass.database.crypto.kdf.Limits
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * E1.13.2 (D44): the vault process. Declared in the manifest with android:process=":vault", so the engine's memory and any crash stay
 * out of the typing process. One [VaultSession] lives here; every call from a client runs on ONE engine thread (the engine's key-derivation
 * objects are shared singletons) while Binder threads just wait for it.
 *
 * Auto-lock (D38): the open vault locks itself when the screen turns off and after [DEFAULT_INACTIVITY_MS] without a call (the time
 * will become a setting; [IVault.setAutoLock] is already the hook for it). Locking wipes the session and lets the service stop.
 * The service is started (not only bound) while a vault is open, so closing the keyboard or a screen does not lock it.
 */
class VaultService : Service() {
    private lateinit var session: VaultSession
    private val engine: ExecutorService = Executors.newSingleThreadExecutor { r -> Thread(r, "vault-engine") }
    private val main = Handler(Looper.getMainLooper())

    @Volatile
    private var inactivityMs = DEFAULT_INACTIVITY_MS
    private val autoLock = Runnable { engine.execute { lockNow() } }
    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            engine.execute { lockNow() }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val work = File(cacheDir, WORK_DIR)
        work.deleteRecursively() // whatever a killed vault process left behind (attachment cache, temp saves)
        session = VaultSession(work, deviceLimits(this)) { deviceKdfLimits(this) }
        registerReceiver(screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF))
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    override fun onDestroy() {
        unregisterReceiver(screenOff)
        main.removeCallbacksAndMessages(null)
        engine.execute { session.lock() }
        engine.shutdown()
        super.onDestroy()
    }

    /** Engine thread only. */
    private fun lockNow() {
        session.lock()
        main.removeCallbacks(autoLock)
        stopSelf()
    }

    /** Engine thread only: runs after every call. */
    private fun afterCall() {
        main.removeCallbacks(autoLock)
        if (session.status().unlocked) {
            val ms = inactivityMs
            if (ms > 0) main.postDelayed(autoLock, ms)
        } else {
            stopSelf()
        }
    }

    private fun <T> onEngine(block: () -> T): T {
        val future = engine.submit(
            Callable {
                try {
                    block()
                } finally {
                    afterCall()
                }
            }
        )
        try {
            return future.get()
        } catch (e: ExecutionException) {
            throw translate(e.cause ?: e)
        }
    }

    /**
     * Binder can carry only a few exception types; anything else thrown on a Binder thread would crash this process. So everything
     * becomes an IllegalStateException carrying a VaultError code (see VaultWire); VaultClient turns it back into a VaultException.
     */
    private fun translate(t: Throwable): RuntimeException = when (t) {
        is VaultException -> VaultWire.encode(t)
        else -> VaultWire.encode(
            VaultException(VaultError.INTERNAL, t.javaClass.simpleName + (t.message?.let { ": $it" } ?: ""), t)
        )
    }

    private fun storeFor(file: Uri): VaultStore = UriVaultStore(this, file)

    private val binder = object : IVault.Stub() {
        override fun processId(): Int = Process.myPid()
        override fun status(): VaultStatus = onEngine { session.status() }
        override fun info(): VaultInfo = onEngine { session.info() }

        override fun create(file: Uri, name: String?, password: CharArray?, keyFile: ByteArray?, tune: Boolean) {
            onEngine { session.create(storeFor(file), name ?: "", password, keyFile, tune) }
            keepAlive()
        }

        override fun open(file: Uri, password: CharArray?, keyFile: ByteArray?, readOnly: Boolean) {
            onEngine { session.open(storeFor(file), password, keyFile, readOnly) }
            keepAlive()
        }

        override fun lock() = onEngine { lockNow() }

        override fun setAutoLock(inactivityMs: Long) {
            this@VaultService.inactivityMs = inactivityMs.coerceAtLeast(0)
            onEngine { }
        }

        override fun list(groupId: String?): GroupListing = onEngine { session.list(groupId) }
        override fun search(query: String?): List<EntrySummary> = onEngine { session.search(query ?: "") }
        override fun entry(id: String?): EntryDetail = onEngine { session.entry(need(id)) }
        override fun password(id: String?): CharArray = onEngine { session.password(need(id)) }
        override fun fieldValue(id: String?, name: String?): String? = onEngine { session.fieldValue(need(id), need(name)) }

        override fun saveEntry(draft: EntryDraft?): Change = onEngine { session.saveEntry(need(draft)) }
        override fun deleteEntry(id: String?): Change = onEngine { session.deleteEntry(need(id)) }
        override fun moveEntry(id: String?, groupId: String?): Change = onEngine { session.moveEntry(need(id), need(groupId)) }
        override fun createGroup(parentId: String?, name: String?): Change = onEngine { session.createGroup(need(parentId), need(name)) }
        override fun renameGroup(id: String?, name: String?): Change = onEngine { session.renameGroup(need(id), need(name)) }
        override fun deleteGroup(id: String?): Change = onEngine { session.deleteGroup(need(id)) }

        override fun retrySave(): Change = onEngine { session.retrySave() }
        override fun resolve(choice: Int, password: CharArray?, keyFile: ByteArray?): Change =
            onEngine { session.resolve(choice, password, keyFile) }
    }

    /** Started as well as bound, so the vault stays open when the last client unbinds; lockNow() stops it again. */
    private fun keepAlive() {
        try {
            startService(Intent(this, VaultService::class.java))
        } catch (_: IllegalStateException) {
            // the app is in the background and may not start services: the vault then lives as long as a client is bound, which is safe
        }
    }

    private fun <T : Any> need(value: T?): T = value ?: throw VaultException(VaultError.INVALID, "Missing argument")

    companion object {
        private const val WORK_DIR = "vault-work"

        /** D38: five minutes without use; becomes a setting later. */
        const val DEFAULT_INACTIVITY_MS = 5 * 60 * 1000L

        private fun availableBytes(context: Context): Long {
            val info = ActivityManager.MemoryInfo()
            (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(info)
            return info.availMem
        }

        private fun safeBytes(context: Context, ratio: Float): ULong =
            (availableBytes(context).toDouble() * ratio.coerceIn(0f, 1f)).toLong().coerceAtLeast(0L).toULong()

        /** What this phone can afford right now: the engine's own per-operation ceilings, but never more than the free memory times its ratio. */
        fun deviceLimits(context: Context): Limits = Limits(
            isMemorySufficient = { wanted, type -> wanted <= minOf(type.maxMemory, safeBytes(context, type.ratioAvailableMemory)) },
            parallelism = Runtime.getRuntime().availableProcessors().toLong().coerceAtLeast(1L),
        )

        /** For the benchmark that sizes a NEW vault's key derivation: at most 64 MiB (so the vault also opens on a small phone) and 3/4 of free memory. */
        fun deviceKdfLimits(context: Context): KdfLimits = KdfLimits(
            memory = minOf(VaultSession.DEFAULT_KDF_LIMITS.memory, safeBytes(context, 3f / 4f)),
            parallelism = Runtime.getRuntime().availableProcessors().toLong().coerceIn(1L, 4L),
        )
    }
}
