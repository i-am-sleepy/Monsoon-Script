package com.monsoonscript.keyboard.autofill

/** An entry of the vault that belongs to the same site or app as a login being saved, with whether its stored password is the one being saved. */
internal class Candidate(val id: String, val username: String, val samePassword: Boolean)

internal sealed class SaveDecision {
    /** The same login is already stored: ask nothing. */
    object Nothing : SaveDecision()

    /** A new entry. */
    object New : SaveDecision()

    /** The same site and user name are stored with another password: offer to replace it. */
    class Update(val id: String) : SaveDecision()
}

/** What to do with a login the owner just typed in another app (E1.20, D100). Pure, so it is tested on a JVM. */
internal object SaveRules {
    fun decide(username: String, candidates: List<Candidate>): SaveDecision {
        if (username.isBlank()) return SaveDecision.New // nothing tells entries apart: never overwrite one
        val same = candidates.filter { it.username.equals(username.trim(), ignoreCase = true) }
        if (same.any { it.samePassword }) return SaveDecision.Nothing
        same.firstOrNull()?.let { return SaveDecision.Update(it.id) }
        return SaveDecision.New
    }

    /** The title offered for a new entry: the site without "www.", or the app's name (its package name when that cannot be read). */
    fun defaultTitle(webDomain: String?, packageName: String, appLabel: String?): String {
        val site = webDomain?.trim()?.lowercase()?.removePrefix("www.").orEmpty()
        if (site.isNotEmpty()) return site
        return appLabel?.trim().orEmpty().ifEmpty { packageName }
    }
}
