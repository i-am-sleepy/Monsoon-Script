package com.monsoonscript.vault

/**
 * E1.23 (D104): reads the CSV file another password manager (or Google Password Manager / Chrome) exported, so the owner can bring his logins into the vault.
 * Pure Kotlin, no Android and no engine classes: text in, a list of logins out, so the rules are easy to test and never touch the vault.
 *
 * The columns are found by their NAMES in the first row (case, spaces, "_" and "-" do not matter), so one reader covers Google / Chrome
 * (`name,url,username,password,note`), Bitwarden (`login_uri, login_username, login_password, login_totp`), 1Password, KeePassXC, LastPass, Firefox and
 * most others. Rows that are not logins (Bitwarden notes and cards, LastPass secure notes) are counted and left out. Folders / groups of the old manager are
 * not kept: the owner names the folder for the whole import.
 */
object CsvImport {
    /** One login read from the file. [otp] is the one-time code key or otpauth link as written in the file, "" = none. */
    class Login(val title: String, val url: String, val username: String, val password: String, val notes: String, val otp: String)

    /**
     * [problem] is a sentence for the owner when the file cannot be used (then [logins] is empty and nothing should be imported).
     * [notLogins]: rows of another kind (notes, cards, identities). [noPassword]: logins without a password and without a one-time code.
     */
    class Parsed(val format: String, val logins: List<Login>, val notLogins: Int, val noPassword: Int, val problem: String? = null)

    private val TITLE = listOf("name", "title", "sitename", "itemname", "entryname", "label")
    private val URL = listOf("url", "loginuri", "website", "weburl", "loginurl", "uri", "webaddress", "address", "site", "origin", "hostname", "link")
    private val USER = listOf("username", "loginusername", "loginname", "login", "user", "userid", "email", "emailaddress", "loginemail", "mail")
    private val PASS = listOf("password", "loginpassword", "pass", "pwd")
    private val NOTES = listOf("note", "notes", "extra", "comments", "comment", "description")
    private val OTP = listOf("totp", "logintotp", "otpauth", "otp", "totpsecret", "otpsecret", "onetimepassword")
    private const val TYPE = "type"
    private const val FIELDS = "fields"

    /** Types that stand for a login (Bitwarden `login`, 1Password `Login` / `Password`); anything else in a "type" column is a note, card or identity. */
    private val LOGIN_TYPES = setOf("", "login", "password", "website", "logins")

    private const val LASTPASS_NOTE_URL = "http://sn"

    /** What we call the header names: lower case, only letters and digits ("Login URL" and "login_url" both become "loginurl"). */
    internal fun norm(name: String): String = name.lowercase().filter { it.isLetterOrDigit() }

    /** Reads [text] (the whole file). Never throws: a file that cannot be used comes back with a [Parsed.problem]. */
    fun parse(text: String): Parsed {
        val rows = readRows(text)
        if (rows.isEmpty()) return failed("The file is empty.")
        val header = rows[0].map { norm(it) }
        fun col(names: List<String>): Int = names.firstNotNullOfOrNull { n -> header.indexOf(n).takeIf { it >= 0 } } ?: -1
        val title = col(TITLE)
        val url = col(URL)
        val user = col(USER)
        val pass = col(PASS)
        val notes = col(NOTES)
        val otp = col(OTP)
        val type = header.indexOf(TYPE)
        val fields = header.indexOf(FIELDS)
        if (pass < 0) {
            return failed("No password column found. The first row of the file must name the columns, for example: name, url, username, password.")
        }
        if (title < 0 && url < 0 && user < 0) {
            return failed("The file has a password column but nothing to say which site or account each password is for.")
        }
        val format = formatOf(header)
        val logins = ArrayList<Login>()
        var notLogins = 0
        var noPassword = 0
        for (i in 1 until rows.size) {
            val row = rows[i]
            fun cell(c: Int): String = if (c in row.indices) row[c] else ""
            if (type >= 0 && cell(type).trim().lowercase() !in LOGIN_TYPES) {
                notLogins++
                continue
            }
            val address = cell(url).trim()
            if (address.equals(LASTPASS_NOTE_URL, ignoreCase = true)) {
                notLogins++
                continue
            }
            val password = cell(pass)
            val code = cell(otp).trim()
            if (password.isEmpty() && code.isEmpty()) {
                noPassword++
                continue
            }
            val account = cell(user).trim()
            var note = cell(notes)
            val custom = cell(fields)
            if (custom.isNotBlank()) note = if (note.isBlank()) custom else note + "\n\n" + custom
            val name = cell(title).trim().ifEmpty { SiteMatch.hostOf(address) ?: account.ifEmpty { "Login" } }
            logins.add(Login(name, address, account, password, note, code))
        }
        if (logins.isEmpty()) {
            return Parsed(format, emptyList(), notLogins, noPassword, "The file has no logins with a password.")
        }
        return Parsed(format, logins, notLogins, noPassword)
    }

    /**
     * What decides that two logins are "the same login": the site's host (as [SiteMatch] reads it, so https://www.example.com/a and example.com agree) plus the
     * user name; without a host (blank or an app link) the title takes the host's place. Case does not matter.
     */
    fun loginKey(title: String, url: String, username: String): String {
        val place = SiteMatch.hostOf(url) ?: "t:" + title.trim().lowercase()
        return place + "\u0000" + username.trim().lowercase()
    }

    private fun failed(problem: String) = Parsed("", emptyList(), 0, 0, problem)

    private fun formatOf(header: List<String>): String = when {
        "logintotp" in header || "loginuri" in header -> "Bitwarden"
        "grouping" in header && "extra" in header -> "LastPass"
        "group" in header && "title" in header && "totp" in header -> "KeePassXC"
        "httprealm" in header -> "Firefox"
        "otpauth" in header && "title" in header -> "1Password"
        "name" in header && "url" in header && "username" in header && "password" in header -> "Google / Chrome"
        else -> "CSV"
    }

    // ---------------------------------------------------------------- the CSV text itself

    /**
     * RFC 4180: fields may be in double quotes (a quote inside is doubled, a line break inside is kept as "\n"), lines end with \n, \r\n or \r. A leading byte
     * order mark is dropped. The separator is the comma, or a semicolon or tab when the first line has more of those (Excel in some regions). Blank lines are left out.
     */
    internal fun readRows(raw: String): List<List<String>> {
        val text = if (raw.startsWith("﻿")) raw.substring(1) else raw
        val sep = separatorOf(text)
        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val field = StringBuilder()
        var quoted = false
        var wasQuoted = false // the current field started with a quote: an empty "" is still a field
        var rowQuoted = false // the line held a quoted field: a line with just "" is a row, a really blank line is not
        var i = 0
        fun endField() {
            row.add(field.toString())
            field.setLength(0)
            wasQuoted = false
        }
        fun endRow() {
            endField()
            if (!(row.size == 1 && row[0].isEmpty() && !rowQuoted)) rows.add(row)
            row = ArrayList()
            rowQuoted = false
        }
        while (i < text.length) {
            val c = text[i]
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') {
                        field.append('"')
                        i++
                    } else {
                        quoted = false
                    }
                } else if (c == '\r') {
                    if (i + 1 < text.length && text[i + 1] == '\n') i++
                    field.append('\n')
                } else {
                    field.append(c)
                }
            } else {
                when {
                    c == '"' && field.isEmpty() && !wasQuoted -> {
                        quoted = true
                        wasQuoted = true
                        rowQuoted = true
                    }
                    c == sep -> endField()
                    c == '\n' -> endRow()
                    c == '\r' -> {
                        if (i + 1 < text.length && text[i + 1] == '\n') i++
                        endRow()
                    }
                    else -> field.append(c)
                }
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty() || wasQuoted) endRow()
        return rows
    }

    private fun separatorOf(text: String): Char {
        var commas = 0
        var semicolons = 0
        var tabs = 0
        var inQuotes = false
        for (c in text) {
            if (c == '"') inQuotes = !inQuotes
            else if (!inQuotes) {
                when (c) {
                    ',' -> commas++
                    ';' -> semicolons++
                    '\t' -> tabs++
                    '\n', '\r' -> return pick(commas, semicolons, tabs)
                }
            }
        }
        return pick(commas, semicolons, tabs)
    }

    private fun pick(commas: Int, semicolons: Int, tabs: Int): Char = when {
        semicolons > commas && semicolons >= tabs -> ';'
        tabs > commas && tabs > semicolons -> '\t'
        else -> ','
    }
}
