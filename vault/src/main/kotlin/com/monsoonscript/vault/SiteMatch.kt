package com.monsoonscript.vault

/**
 * Which stored entries belong to a website (E1.19, D100). Pure Kotlin: addresses in, a verdict out, so the rules are easy to test and never touch the vault.
 *
 * The rule is deliberately cautious, because a wrong match offers a password to the wrong site: an entry matches a site when its address names the same host
 * (a leading "www." and the scheme, port, path and query do not matter) or a parent domain of it ("example.com" matches "accounts.example.com"). A look-alike
 * ("evilexample.com", "example.com.evil.org") never matches, an entry for a bare ending such as "co.uk" or a shared host such as "github.io" matches only
 * itself, and IP addresses and "localhost" match only exactly.
 */
object SiteMatch {
    const val NONE = 0
    const val SUBDOMAIN = 1
    const val EXACT = 2

    /** Endings that many unrelated sites share: an entry for one of these must not match every site below it. */
    private val SHARED = setOf(
        "co.uk", "org.uk", "ac.uk", "gov.uk", "com.au", "net.au", "org.au", "co.jp", "co.in", "co.nz", "co.za", "co.kr", "com.br", "com.cn", "com.sg", "com.hk",
        "com.tr", "com.mx", "com.ar", "com.bd", "net.bd", "org.bd", "edu.bd", "gov.bd",
        "github.io", "gitlab.io", "blogspot.com", "herokuapp.com", "vercel.app", "netlify.app", "pages.dev", "workers.dev", "web.app", "firebaseapp.com",
        "azurewebsites.net", "amazonaws.com", "appspot.com",
    )

    /**
     * The host of [address] as typed in an entry's URL field or given by Android ("https://www.Example.com:8443/login?x=1" -> "example.com", "Example.com" ->
     * "example.com"), or null when there is no host to read (blank, an `androidapp://` link, free text with spaces).
     */
    fun hostOf(address: String): String? {
        var a = address.trim().lowercase()
        if (a.isEmpty() || a.startsWith("androidapp://")) return null
        val scheme = a.indexOf("://")
        if (scheme >= 0) a = a.substring(scheme + 3)
        a = a.takeWhile { it != '/' && it != '?' && it != '#' }
        a = a.substringAfterLast('@')
        if (a.startsWith("[")) { // an IPv6 address: kept whole, matches exactly only
            val end = a.indexOf(']')
            return if (end > 0) a.substring(0, end + 1) else null
        }
        a = a.substringBefore(':').trimEnd('.')
        if (a.startsWith("www.")) a = a.substring(4)
        if (a.isEmpty() || a.any { it.isWhitespace() }) return null
        return a
    }

    /** [EXACT] when [entryAddress] names [siteHost], [SUBDOMAIN] when it names a parent domain of it, otherwise [NONE]. */
    fun level(entryAddress: String, siteHost: String): Int {
        val entry = hostOf(entryAddress) ?: return NONE
        val site = hostOf(siteHost) ?: return NONE
        if (entry == site) return EXACT
        if (entry.startsWith("[") || !entry.contains('.') || isIpv4(entry) || entry in SHARED) return NONE
        return if (site.endsWith(".$entry")) SUBDOMAIN else NONE
    }

    private fun isIpv4(host: String) = host.all { it.isDigit() || it == '.' }
}
