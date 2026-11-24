package com.monsoonscript.vault

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.DeadObjectException
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.RemoteException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * E1.13.2: the app's and the keyboard's way to the vault process (D44). Binds to [VaultService] on first use and keeps the
 * connection; if the vault process was killed it binds again, and the vault is then simply locked (state lives only in that process).
 *
 * - [call] blocks and must NOT be used on the main thread (the vault does real work: Argon2 takes about a second).
 * - [async] runs [call] on a background thread and delivers the result on the main thread, which is what screens use.
 * Every failure arrives as [VaultException] with a [VaultError] code.
 */
class VaultClient(context: Context) {
    private val app: Context = context.applicationContext
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "vault-client").apply { isDaemon = true } }
    private val main = Handler(Looper.getMainLooper())

    @Volatile
    private var service: IVault? = null
    private var connection: ServiceConnection? = null

    fun <T> call(block: (IVault) -> T): T {
        check(Looper.myLooper() != Looper.getMainLooper()) { "VaultClient.call blocks: use async() on the main thread" }
        var retried = false
        while (true) {
            val vault = connect()
            try {
                return block(vault)
            } catch (e: IllegalStateException) {
                throw VaultWire.decode(e) ?: e
            } catch (e: DeadObjectException) {
                service = null
                if (retried) throw VaultException(VaultError.LOCKED, "The vault process stopped, so the vault is locked", e)
                retried = true // the vault process was killed: bind again; the call then meets a locked vault and fails with LOCKED
            } catch (e: RemoteException) {
                throw VaultException(VaultError.INTERNAL, "The vault process could not be reached: ${e.message}", e)
            }
        }
    }

    /** Runs [block] on a background thread; [done] is called on the main thread. */
    fun <T> async(block: (IVault) -> T, done: (Result<T>) -> Unit) {
        worker.execute {
            val result = try {
                Result.success(call(block))
            } catch (e: VaultException) {
                Result.failure(e)
            } catch (e: Exception) {
                Result.failure(VaultException(VaultError.INTERNAL, e.message ?: e.javaClass.simpleName, e))
            }
            main.post { done(result) }
        }
    }

    /** Drops the connection (the vault stays open in its process: it locks by itself, D38, or on lock()). */
    @Synchronized
    fun close() {
        connection?.let { app.unbindService(it) }
        connection = null
        service = null
    }

    @Synchronized
    private fun connect(): IVault {
        service?.let { if (it.asBinder().isBinderAlive) return it }
        connection?.let { runCatching { app.unbindService(it) } }
        connection = null
        val latch = CountDownLatch(1)
        val conn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                service = IVault.Stub.asInterface(binder)
                latch.countDown()
            }

            override fun onServiceDisconnected(name: ComponentName) {
                service = null
            }
        }
        if (!app.bindService(Intent(app, VaultService::class.java), conn, Context.BIND_AUTO_CREATE)) {
            throw VaultException(VaultError.INTERNAL, "The vault service could not be started")
        }
        if (!latch.await(15, TimeUnit.SECONDS)) {
            app.unbindService(conn)
            throw VaultException(VaultError.INTERNAL, "The vault service did not start in time")
        }
        connection = conn
        return service ?: throw VaultException(VaultError.INTERNAL, "The vault service disconnected")
    }
}
