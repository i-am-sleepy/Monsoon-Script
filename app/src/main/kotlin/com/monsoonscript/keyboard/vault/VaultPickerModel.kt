package com.monsoonscript.keyboard.vault

import com.monsoonscript.vault.EntrySummary
import com.monsoonscript.vault.GroupListing
import com.monsoonscript.vault.GroupRef
import com.monsoonscript.vault.VaultError
import com.monsoonscript.vault.VaultException
import com.monsoonscript.vault.VaultInfo
import com.monsoonscript.vault.VaultStatus

/**
 * What the keyboard's password picker shows and does (E1.14, D27 / D29 / D32 / D40 / D87), without any Android view: [VaultPanelView] only draws this and forwards
 * touches, and the unit tests drive it with a fake [Backend]. Nothing secret is ever held here: lists carry titles and user names only (D43); a password is fetched
 * when its chip is tapped, handed to [Host.typePassword] and wiped right after.
 *
 * Views of the list: "This app" (entries that store the app in front, only when there are some), "Recent" (what was typed last), and "All" (the folders).
 * A search query replaces whichever view is up. Tapping an entry opens it in place (chips Username / Password, D87); tapping a chip types it.
 *
 * Every [Backend] call answers on the main thread; a [generation] number drops answers that arrive after the view moved on.
 */
internal class VaultPickerModel(private val backend: Backend, private val host: Host) {

    interface Backend {
        fun status(done: (Result<VaultStatus>) -> Unit)
        fun info(done: (Result<VaultInfo>) -> Unit)
        fun forApp(packageName: String, done: (Result<List<EntrySummary>>) -> Unit)
        fun summaries(ids: List<String>, done: (Result<List<EntrySummary>>) -> Unit)
        fun list(groupId: String?, done: (Result<GroupListing>) -> Unit)
        fun search(query: String, done: (Result<List<EntrySummary>>) -> Unit)
        fun password(id: String, done: (Result<CharArray>) -> Unit)
    }

    interface Host {
        /** A vault file has been chosen in the app (otherwise the panel offers to open the app). */
        val vaultConfigured: Boolean

        /** The field in front is a password field (D40: a password typed anywhere else is asked about first). */
        fun fieldIsPassword(): Boolean

        /** Plain text into the app (a user name). */
        fun typeText(text: String)

        /** A password into the app. The model wipes [chars] after this returns, so copy nothing out of it. */
        fun typePassword(chars: CharArray)

        /** Ids typed last, newest first (kept by the keyboard, not by the vault: using an entry never rewrites the vault file). */
        fun recentIds(): List<String>

        fun noteUsed(id: String)

        /** Words for a failure, in plain language. */
        fun describe(e: Throwable): String

        /** Something visible changed: redraw. */
        fun changed()

        /** Back to the keys (after a password was typed, D87). */
        fun closePicker()
    }

    enum class State { NO_VAULT, LOCKED, LOADING, READY, CONFLICT, ERROR }
    enum class Tab { APP, RECENT, FOLDERS }
    enum class ChipKind { USERNAME, PASSWORD }

    sealed class Row {
        class Folder(val ref: GroupRef) : Row()

        /** "Up" one folder; [name] is the folder you would come back to (null = the top). */
        class Up(val name: String?) : Row()
        class Item(val entry: EntrySummary) : Row()
    }

    var state = State.LOADING
        private set
    var message = ""
        private set
    var tab = Tab.RECENT
        private set

    /** Entries stored for the app in front exist: the "This app" chip is shown. */
    val hasApp: Boolean get() = matches.isNotEmpty()
    var rows: List<Row> = emptyList()
        private set

    /** The folder shown by [Tab.FOLDERS]: its name, or null at the top. */
    var folderName: String? = null
        private set
    var query = ""
        private set

    /** The entry opened in place, or null. */
    var expandedId: String? = null
        private set

    /** The password chip was tapped in a field that is not a password field: the panel asks "Type it here anyway?" (D40). */
    var asking = false
        private set

    val searching: Boolean get() = query.isNotBlank()

    private var generation = 0
    private var packageName = ""
    private var binId: String? = null
    private var matches: List<EntrySummary> = emptyList()
    private var recents: List<EntrySummary> = emptyList()
    private var folderId: String? = null
    private var folderPath: List<GroupRef> = emptyList()

    /** The panel was opened over [pkg] (the app in front; blank when unknown). Checks the vault and loads the first view. */
    fun open(pkg: String) {
        packageName = pkg
        val g = ++generation
        clearView()
        if (!host.vaultConfigured) {
            state = State.NO_VAULT
            host.changed()
            return
        }
        state = State.LOADING
        host.changed()
        backend.status { r ->
            if (g != generation) return@status
            r.onFailure { fail(it) }.onSuccess { s ->
                when {
                    !s.unlocked -> setState(State.LOCKED)
                    s.conflict -> setState(State.CONFLICT)
                    else -> load(g)
                }
            }
        }
    }

    /** The panel was closed: nothing stays in memory and answers still on their way are dropped. */
    fun close() {
        generation++
        clearView()
        matches = emptyList()
        recents = emptyList()
        state = State.LOADING
    }

    private fun clearView() {
        rows = emptyList()
        message = ""
        query = ""
        expandedId = null
        asking = false
        folderId = null
        folderPath = emptyList()
        folderName = null
        binId = null
        matches = emptyList()
        recents = emptyList()
    }

    private fun setState(s: State) {
        state = s
        host.changed()
    }

    private fun fail(e: Throwable) {
        if ((e as? VaultException)?.code == VaultError.LOCKED) {
            setState(State.LOCKED)
            return
        }
        message = host.describe(e)
        setState(State.ERROR)
    }

    /** Info, then the entries for this app, then the recent ones; then the first view is chosen. */
    private fun load(g: Int) {
        backend.info { ri ->
            if (g != generation) return@info
            ri.onFailure { fail(it) }.onSuccess { info ->
                binId = info.recycleBinId
                val afterApp = { found: List<EntrySummary> ->
                    matches = found
                    backend.summaries(host.recentIds()) { rr ->
                        if (g == generation) {
                            rr.onFailure { fail(it) }.onSuccess {
                                recents = it
                                state = State.READY
                                selectTab(if (matches.isNotEmpty()) Tab.APP else if (recents.isNotEmpty()) Tab.RECENT else Tab.FOLDERS)
                            }
                        }
                    }
                }
                if (packageName.isBlank()) {
                    afterApp(emptyList())
                } else {
                    backend.forApp(packageName) { rf ->
                        if (g == generation) rf.onFailure { fail(it) }.onSuccess(afterApp)
                    }
                }
            }
        }
    }

    /** One of the chips. Leaves a search. */
    fun selectTab(t: Tab) {
        if (state != State.READY) return
        query = ""
        expandedId = null
        asking = false
        tab = t
        when (t) {
            Tab.APP -> rows = matches.map { Row.Item(it) }
            Tab.RECENT -> rows = recents.map { Row.Item(it) }
            Tab.FOLDERS -> {
                openFolder(folderId)
                return
            }
        }
        host.changed()
    }

    /** [id] null = the top folder. */
    fun openFolder(id: String?) {
        if (state != State.READY) return
        val g = ++generation
        expandedId = null
        asking = false
        backend.list(id) { r ->
            if (g != generation) return@list
            r.onFailure { fail(it) }.onSuccess { listing ->
                folderId = if (listing.path.size <= 1) null else listing.group.id
                folderPath = listing.path
                folderName = if (listing.path.size <= 1) null else listing.group.name
                if (searching) return@onSuccess
                val out = ArrayList<Row>()
                if (listing.path.size > 1) out.add(Row.Up(listing.path.getOrNull(listing.path.size - 2)?.takeIf { listing.path.size > 2 }?.name))
                listing.groups.filter { it.id != binId }.forEach { out.add(Row.Folder(it)) }
                listing.entries.forEach { out.add(Row.Item(it)) }
                rows = out
                host.changed()
            }
        }
    }

    private fun up() {
        val parent = if (folderPath.size >= 2) folderPath[folderPath.size - 2].id else null
        openFolder(if (folderPath.size <= 2) null else parent)
    }

    /** The text of the search bar changed. Blank = back to the view that was up. */
    fun setQuery(q: String) {
        if (state != State.READY) return
        val clean = q.trim()
        if (clean == query) return
        expandedId = null
        asking = false
        if (clean.isEmpty()) {
            query = ""
            selectTab(tab)
            return
        }
        query = clean
        val g = ++generation
        backend.search(clean) { r ->
            if (g != generation) return@search
            r.onFailure { fail(it) }.onSuccess { found ->
                rows = found.map { Row.Item(it) }
                host.changed()
            }
        }
    }

    /** A row was tapped: a folder opens, "up" goes up, an entry opens in place (or closes when it already was). */
    fun tapRow(index: Int) {
        val row = rows.getOrNull(index) ?: return
        when (row) {
            is Row.Folder -> openFolder(row.ref.id)
            is Row.Up -> up()
            is Row.Item -> {
                expandedId = if (expandedId == row.entry.id) null else row.entry.id
                asking = false
                host.changed()
            }
        }
    }

    fun expandedEntry(): EntrySummary? {
        val id = expandedId ?: return null
        for (r in rows) if (r is Row.Item && r.entry.id == id) return r.entry
        return null
    }

    /** The chips an opened entry offers: nothing for a blank user name, nothing for an entry without a password. */
    fun chipsFor(e: EntrySummary): List<ChipKind> {
        val out = ArrayList<ChipKind>(2)
        if (e.username.isNotEmpty()) out.add(ChipKind.USERNAME)
        if (e.hasPassword) out.add(ChipKind.PASSWORD)
        return out
    }

    fun tapChip(kind: ChipKind) {
        val e = expandedEntry() ?: return
        when (kind) {
            ChipKind.USERNAME -> {
                host.typeText(e.username)
                host.noteUsed(e.id)
                host.changed()
            }
            ChipKind.PASSWORD -> {
                if (!host.fieldIsPassword()) {
                    asking = true
                    host.changed()
                } else {
                    typePassword(e)
                }
            }
        }
    }

    /** The answer to "Type it here anyway?". */
    fun answerAsk(yes: Boolean) {
        if (!asking) return
        asking = false
        val e = expandedEntry()
        if (yes && e != null) typePassword(e) else host.changed()
    }

    private fun typePassword(e: EntrySummary) {
        val g = generation
        backend.password(e.id) { r ->
            if (g != generation) {
                r.getOrNull()?.fill('\u0000') // the panel closed meanwhile: nothing is typed
                return@password
            }
            r.onFailure { fail(it) }.onSuccess { chars ->
                try {
                    host.typePassword(chars)
                } finally {
                    chars.fill('\u0000')
                }
                host.noteUsed(e.id)
                host.closePicker()
            }
        }
    }

    companion object {
        const val RECENT_MAX = 8

        /** [id] first, any earlier copy of it dropped, at most [max] kept. Pure so the keyboard's storage can be tested. */
        fun pushRecent(list: List<String>, id: String, max: Int = RECENT_MAX): List<String> {
            val out = ArrayList<String>(max)
            out.add(id)
            for (x in list) if (x != id && out.size < max) out.add(x)
            return out
        }
    }
}
