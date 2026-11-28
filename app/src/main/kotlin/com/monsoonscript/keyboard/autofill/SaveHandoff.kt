package com.monsoonscript.keyboard.autofill

import java.util.UUID

/** A login the owner typed in another app, waiting for the save screen (E1.20). */
internal class PendingSave(val packageName: String, val webDomain: String?, val username: String, val password: String)

/**
 * Carries a login from the Autofill service to the save screen without putting the password in an Intent: both run in the app's process, so the screen takes it
 * out of this holder by a random token. It is kept in memory only, for two minutes, and handed out once.
 */
internal object SaveHandoff {
    private const val LIFETIME_MS = 2 * 60_000L
    private val items = HashMap<String, Pair<PendingSave, Long>>()

    @Synchronized
    fun put(save: PendingSave, now: Long = System.currentTimeMillis()): String {
        purge(now)
        val token = UUID.randomUUID().toString()
        items[token] = save to now
        return token
    }

    @Synchronized
    fun take(token: String?, now: Long = System.currentTimeMillis()): PendingSave? {
        purge(now)
        return if (token == null) null else items.remove(token)?.first
    }

    private fun purge(now: Long) {
        items.entries.removeAll { now - it.value.second > LIFETIME_MS }
    }
}
