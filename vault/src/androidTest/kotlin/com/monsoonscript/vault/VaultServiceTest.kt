package com.monsoonscript.vault

import android.content.Context
import android.net.Uri
import android.os.Process
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/**
 * E1.13.2, emulator only: the real bound service in its own process (D44), called through VaultClient the way the app and the keyboard will.
 * Covers: separate process, a full create / edit / lock / reopen cycle across the boundary, errors keeping their code, auto-lock (D38),
 * the vault staying open when the client disconnects, and several threads calling at once.
 */
class VaultServiceTest {
    private lateinit var ctx: Context
    private lateinit var client: VaultClient
    private lateinit var dir: File
    private lateinit var file: File
    private val pw get() = "master-pw".toCharArray()

    @Before
    fun setUp() {
        ctx = InstrumentationRegistry.getInstrumentation().targetContext
        client = VaultClient(ctx)
        dir = File(ctx.cacheDir, "vsvc-${System.nanoTime()}").also { it.mkdirs() }
        file = File(dir, "svc.kdbx")
    }

    @After
    fun tearDown() {
        try {
            client.call { it.setAutoLock(VaultService.DEFAULT_INACTIVITY_MS) }
            client.call { it.lock() }
        } catch (_: Exception) {
        }
        client.close()
        dir.deleteRecursively()
    }

    private fun uri(): Uri = Uri.fromFile(file)
    private fun create() = client.call { it.create(uri(), "Svc", pw, null, false) }
    private fun open() = client.call { it.open(uri(), pw, null, false) }

    private fun draft(title: String, group: String, id: String? = null) =
        EntryDraft(id, group, title, "user-$title", "secret-$title", "https://$title.example", "", emptyList())

    private fun expectError(code: Int, block: () -> Unit) {
        try {
            block()
            fail("expected VaultException with code $code")
        } catch (e: VaultException) {
            assertEquals("${e.message}", code, e.code)
        }
    }

    @Test
    fun theVaultRunsInItsOwnProcess() {
        val vaultPid = client.call { it.processId() }
        assertTrue("vault pid $vaultPid must differ from ours", vaultPid != Process.myPid())
    }

    @Test
    fun createEditLockAndReopenAcrossTheProcessBoundary() {
        create()
        assertTrue(client.call { it.status() }.unlocked)
        val root = client.call { it.info() }.rootId
        val work = client.call { it.createGroup(root, "Work") }.id!!
        val saved = client.call { it.saveEntry(draft("Gmail", work)) }
        assertTrue(saved.saved)

        client.call { it.lock() }
        assertFalse(client.call { it.status() }.unlocked)
        expectError(VaultError.LOCKED) { client.call { it.list(null) } }
        expectError(VaultError.WRONG_CREDENTIALS) { client.call { it.open(uri(), "nope".toCharArray(), null, false) } }

        open()
        val listing = client.call { it.list(work) }
        assertEquals("Work", listing.group.name)
        val entry = listing.entries.single()
        assertEquals("Gmail", entry.title)
        assertEquals("secret-Gmail", String(client.call { it.password(entry.id) }))
        assertEquals(listOf("Gmail"), client.call { it.search("gma") }.map { it.title })
        assertEquals(1, client.call { it.info() }.entryCount)
    }

    @Test
    fun errorsKeepTheirCodeAcrossTheBoundary() {
        create()
        expectError(VaultError.NOT_FOUND) { client.call { it.entry("00000000-0000-0000-0000-000000000000") } }
        expectError(VaultError.INVALID) { client.call { it.createGroup(it.info().rootId, "  ") } }
        expectError(VaultError.FILE_NOT_FOUND) {
            client.call { it.open(Uri.fromFile(File(dir, "missing.kdbx")), pw, null, false) }
        }
    }

    @Test
    fun theVaultLocksItselfAfterTheInactivityTime() {
        client.call { it.setAutoLock(2500) }
        create()
        assertTrue(client.call { it.status() }.unlocked)
        Thread.sleep(1500)
        assertTrue("a call re-arms the timer", client.call { it.status() }.unlocked)
        Thread.sleep(1500)
        assertTrue("1.5 s since the last call is still inside 2.5 s", client.call { it.status() }.unlocked)
        Thread.sleep(4000)
        assertFalse("locked after more than 2.5 s without a call", client.call { it.status() }.unlocked)
    }

    @Test
    fun theVaultStaysOpenWhenTheClientDisconnects() {
        create()
        val root = client.call { it.info() }.rootId
        client.call { it.saveEntry(draft("Keep", root)) }
        client.close()

        val second = VaultClient(ctx)
        try {
            assertTrue("closing the keyboard or a screen must not lock the vault (D38)", second.call { it.status() }.unlocked)
            assertEquals(setOf("Keep"), second.call { it.list(null) }.entries.map { it.title }.toSet())
        } finally {
            second.close()
        }
    }

    @Test
    fun callsFromSeveralThreadsAreSerialized() {
        create()
        val root = client.call { it.info() }.rootId
        val failures = AtomicInteger()
        val done = CountDownLatch(2)
        for (t in 0 until 2) {
            thread {
                try {
                    for (i in 0 until 4) client.call { it.saveEntry(draft("T$t-$i", root)) }
                } catch (_: Throwable) {
                    failures.incrementAndGet()
                } finally {
                    done.countDown()
                }
            }
        }
        done.await()
        assertEquals(0, failures.get())
        assertEquals(8, client.call { it.list(null) }.entries.size)
        client.call { it.lock() }
        open()
        assertEquals(8, client.call { it.list(null) }.entries.size)
    }
}
