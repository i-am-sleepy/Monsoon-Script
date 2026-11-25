package com.monsoonscript.vault

import com.kunzisoft.keepass.database.crypto.EncryptionAlgorithm
import com.kunzisoft.keepass.database.crypto.kdf.KdfFactory
import com.kunzisoft.keepass.database.crypto.kdf.KdfLimits
import com.kunzisoft.keepass.database.crypto.kdf.Limits
import com.kunzisoft.keepass.database.element.Entry
import com.kunzisoft.keepass.database.element.Field
import com.kunzisoft.keepass.database.element.Group
import com.kunzisoft.keepass.database.element.MasterCredential
import com.kunzisoft.keepass.database.element.database.DatabaseKDBX
import com.kunzisoft.keepass.database.element.node.NodeId
import com.kunzisoft.keepass.database.element.node.NodeIdInt
import com.kunzisoft.keepass.database.element.node.NodeIdUUID
import com.kunzisoft.keepass.database.element.security.ProtectedString
import com.kunzisoft.keepass.database.exception.CorruptedDatabaseException
import com.kunzisoft.keepass.database.exception.DatabaseException
import com.kunzisoft.keepass.database.exception.DatabaseOutputException
import com.kunzisoft.keepass.database.exception.DuplicateUuidDatabaseException
import com.kunzisoft.keepass.database.exception.EmptyKeyDatabaseException
import com.kunzisoft.keepass.database.exception.FileNotFoundDatabaseException
import com.kunzisoft.keepass.database.exception.HardwareKeyDatabaseException
import com.kunzisoft.keepass.database.exception.HeaderHmacMismatchException
import com.kunzisoft.keepass.database.exception.InvalidAlgorithmDatabaseException
import com.kunzisoft.keepass.database.exception.InvalidCredentialsDatabaseException
import com.kunzisoft.keepass.database.exception.KDFIterationDatabaseException
import com.kunzisoft.keepass.database.exception.KDFMemoryDatabaseException
import com.kunzisoft.keepass.database.exception.KDFParallelismDatabaseException
import com.kunzisoft.keepass.database.exception.NoMemoryDatabaseException
import com.kunzisoft.keepass.database.exception.SignatureDatabaseException
import com.kunzisoft.keepass.database.exception.VersionDatabaseException
import com.kunzisoft.keepass.database.exception.XMLMalformedDatabaseException
import com.kunzisoft.keepass.database.file.DatabaseHeaderKDBX
import com.kunzisoft.keepass.hardware.HardwareKey
import com.kunzisoft.keepass.model.DatabaseInfo
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.UUID

/**
 * E1.13 (D26, D44): one open vault. This and VaultEngine are the only classes that touch the KeePassDX engine (`:database`), so an
 * upstream API change is fixed here and nowhere else. The service in the `:vault` process owns exactly one session and calls it from
 * a single thread; every public method is also @Synchronized because the engine's KDF objects are shared singletons.
 *
 * Rules the code follows (all from docs/PASSWORD_MANAGER.md):
 * - D36 every change is saved right away, the engine writes the whole file to a private temp file first, and the version being
 *   replaced is kept as a backup (VaultBackups).
 * - D50 when the file changed since it was opened, nothing is written: the change stays in memory (`dirty` + `conflict`) until
 *   [resolve] is called with the owner's choice.
 * - D43 lists never contain secrets; a password or a protected field leaves the session only through [password] / [fieldValue].
 * - The password is NOT kept after unlocking: the engine keeps its own derived composite key, which later saves reuse.
 *
 * The edit sequences (add, edit with history, delete via the recycle bin) copy what KeePassDX itself does in its
 * AddEntryRunnable / UpdateEntryRunnable / DeleteNodesRunnable, so a file edited here stays valid for KeePassDX and KeePassXC.
 */
class VaultSession(
    private val workDir: File,
    private val limits: Limits = PERMISSIVE,
    private val kdfLimits: () -> KdfLimits = { DEFAULT_KDF_LIMITS },
) {
    private var db: DatabaseInfo? = null
    private var store: VaultStore? = null
    private var openedStamp: String? = null
    private var dirty = false
    private var conflict = false
    private var backedUpThisSession = false

    // ---------------------------------------------------------------- opening, creating, locking

    /**
     * Creates a new vault at [store] and leaves it unlocked. [tune] runs the key-derivation benchmark (about a second) so the strength
     * fits this phone, like KeePassDX does; tests switch it off.
     */
    @Synchronized
    fun create(store: VaultStore, name: String, password: CharArray?, keyFile: ByteArray?, tune: Boolean = true) {
        lock()
        requireCredential(password, keyFile)
        val credential = MasterCredential(password?.copyOf(), keyFile?.copyOf())
        val database = DatabaseInfo()
        try {
            workDir.mkdirs()
            database.createData(name.ifBlank { "Vault" }, ROOT_NAME, null)
            database.kdfEngine = KdfFactory.argon2idKdf
            database.encryptionAlgorithm = EncryptionAlgorithm.AESRijndael
            val engine = database.kdfEngine ?: throw VaultException(VaultError.INTERNAL, "No key derivation engine")
            engine.randomize()
            if (tune) {
                val masterKey = credential.toMasterKey(database.passwordEncoding, database.transformSeed, NO_HARDWARE_KEY)
                try {
                    engine.optimizeByBenchmark(masterKey = masterKey, limits = kdfLimits())
                } finally {
                    masterKey.fill(0)
                }
            }
            database.enableRecycleBin(true, RECYCLE_BIN_NAME)
            database.loaded = true
            this.db = database
            this.store = store
            this.openedStamp = null
            this.backedUpThisSession = false
            writeToStore(credential) // the first save derives the master key from the credential
        } catch (e: Throwable) {
            wipe(database)
            this.db = null
            this.store = null
            throw map(e)
        } finally {
            credential.clear()
        }
    }

    /** Opens and decrypts the vault. The previous one (and any unsaved change in it) is dropped. */
    @Synchronized
    fun open(store: VaultStore, password: CharArray?, keyFile: ByteArray?, readOnly: Boolean = false) {
        lock()
        requireCredential(password, keyFile)
        val credential = MasterCredential(password?.copyOf(), keyFile?.copyOf())
        val database = DatabaseInfo()
        try {
            workDir.mkdirs()
            val stamp = store.stamp() // taken BEFORE reading, so a change during the read is noticed later
            checkKdfLimits(store)
            store.openInput().use { input ->
                database.loadData(
                    databaseStream = input,
                    masterCredential = credential,
                    challengeResponseRetriever = NO_HARDWARE_KEY,
                    readOnly = readOnly,
                    allowUserVerification = false,
                    cacheDirectory = workDir,
                    limits = limits,
                    fixDuplicateUUID = false,
                    progressTaskUpdater = null,
                )
            }
            this.db = database
            this.store = store
            this.openedStamp = stamp
            this.backedUpThisSession = false
            this.dirty = false
            this.conflict = false
        } catch (e: Throwable) {
            wipe(database)
            this.db = null
            this.store = null
            throw map(e)
        } finally {
            credential.clear()
        }
    }

    /** Wipes everything from memory (and the engine's attachment cache on disk). Unsaved changes are lost; there normally are none. */
    @Synchronized
    fun lock() {
        db?.let { wipe(it) }
        db = null
        store = null
        openedStamp = null
        dirty = false
        conflict = false
        backedUpThisSession = false
    }

    @Synchronized
    fun status(): VaultStatus = VaultStatus(db != null, dirty, conflict, db?.isReadOnly == true)

    @Synchronized
    fun info(): VaultInfo {
        val d = requireDb()
        val root = d.rootGroup ?: throw VaultException(VaultError.INTERNAL, "The vault has no root folder")
        var count = 0
        walk(root, binId(d)) { count++ }
        return VaultInfo(
            name = d.name,
            entryCount = count,
            cipher = d.getEncryptionAlgorithmName(),
            formatVersion = d.version,
            rootId = idOf(root.nodeId),
            recycleBinId = binId(d),
        )
    }

    // ---------------------------------------------------------------- reading

    /** [groupId] null = the root folder. */
    @Synchronized
    fun list(groupId: String?): GroupListing {
        val d = requireDb()
        val group = group(d, groupId)
        return GroupListing(
            group = ref(group),
            path = pathOf(group),
            groups = group.getChildGroups().map { ref(it) }.sortedBy { it.name.lowercase() },
            entries = group.getChildEntries().map { summary(it) }.sortedBy { it.title.lowercase() },
        )
    }

    /** Case-insensitive; every word must appear in the title, user name, address or notes. The recycle bin is not searched. */
    @Synchronized
    fun search(query: String): List<EntrySummary> {
        val d = requireDb()
        val words = query.lowercase().split(' ', '\t').filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()
        val root = d.rootGroup ?: return emptyList()
        val found = ArrayList<EntrySummary>()
        walk(root, binId(d)) { e ->
            val haystack = "${e.title}\n${e.username}\n${e.url}\n${e.notes}".lowercase()
            if (words.all { haystack.contains(it) }) found.add(summary(e))
        }
        return found.sortedBy { it.title.lowercase() }
    }

    /**
     * Entries that store [packageName] as an app link (E1.14, D32): the custom field "AndroidApp", "AndroidApp1", ... that KeePassDX writes.
     * The recycle bin is not searched. A blank package name finds nothing.
     */
    @Synchronized
    fun forApp(packageName: String): List<EntrySummary> {
        val d = requireDb()
        if (packageName.isBlank()) return emptyList()
        val root = d.rootGroup ?: return emptyList()
        val found = ArrayList<EntrySummary>()
        walk(root, binId(d)) { e ->
            if (e.getExtraFields().any { isAppField(it.name) && it.protectedValue.toString() == packageName }) found.add(summary(e))
        }
        return found.sortedBy { it.title.lowercase() }
    }

    /** The summaries of [ids] in the same order; ids that do not exist any more (or are in the recycle bin) are left out. For the picker's "recent" list. */
    @Synchronized
    fun summaries(ids: List<String>): List<EntrySummary> {
        val d = requireDb()
        val bin = d.recycleBin
        val out = ArrayList<EntrySummary>(ids.size)
        for (id in ids) {
            val e = try {
                entryById(d, id)
            } catch (_: VaultException) {
                continue
            }
            if (bin != null && isUnder(e, bin)) continue
            out.add(summary(e))
        }
        return out
    }

    @Synchronized
    fun entry(id: String): EntryDetail {
        val d = requireDb()
        val e = entryById(d, id)
        return EntryDetail(
            summary = summary(e),
            notes = e.notes,
            fields = e.getExtraFields().map { f ->
                val protect = f.protectedValue.isProtected
                FieldItem(f.name, if (protect) null else f.protectedValue.toString(), protect)
            },
            created = e.creationTime.toMilliseconds(),
            expires = if (e.expires) e.expiryTime.toMilliseconds() else null,
            historyCount = e.getHistory().size,
        )
    }

    /** The stored password. The caller should wipe the array after use. */
    @Synchronized
    fun password(id: String): CharArray = entryById(requireDb(), id).password.copyOf()

    @Synchronized
    fun fieldValue(id: String, name: String): String? =
        entryById(requireDb(), id).getExtraFields().firstOrNull { it.name == name }?.protectedValue?.toString()

    // ---------------------------------------------------------------- changing (each call saves, D36)

    @Synchronized
    fun saveEntry(draft: EntryDraft): Change {
        val d = requireWritable()
        checkFields(draft.fields)
        val parent = group(d, draft.groupId)
        return if (draft.id == null) {
            val entry = d.createEntry() ?: throw VaultException(VaultError.INTERNAL, "The engine could not create an entry")
            fill(entry, draft, keepFrom = null)
            entry.touch(modified = true, touchParents = true)
            parent.touch(modified = true, touchParents = true)
            d.addEntryTo(entry, parent)
            changed(idOf(entry.nodeId))
        } else {
            val old = entryById(d, draft.id)
            val updated = Entry(old) // a clone, like KeePassDX: the old one goes into the history
            fill(updated, draft, keepFrom = old)
            updated.addParentFrom(old)
            updated.clearHistory()
            old.getHistory().forEach { updated.addEntryToHistory(it) }
            updated.touch(modified = true, touchParents = true)
            updated.addEntryToHistory(Entry(old, copyHistory = false))
            d.removeOldestEntryHistory(updated, d.attachmentPool)
            d.updateEntry(updated)
            if (idOf(parent.nodeId) != idOf(old.parent?.nodeId ?: parent.nodeId)) {
                d.moveEntryTo(entryById(d, draft.id), parent) // the form also lets the owner pick another folder
            }
            changed(draft.id)
        }
    }

    /** Recycle bin when the vault has one and the entry is not in it yet, otherwise gone for good. */
    @Synchronized
    fun deleteEntry(id: String): Change {
        val d = requireWritable()
        val entry = entryById(d, id)
        val oldParent = entry.parent
        entry.touch(modified = true, touchParents = true)
        if (d.isRecycleBinEnabled) d.ensureRecycleBinExists(RECYCLE_BIN_NAME)
        if (d.canRecycle(entry)) {
            d.recycle(entry, RECYCLE_BIN_NAME)
            entry.setPreviousParentGroup(oldParent)
            entry.touch(modified = true, touchParents = true)
        } else {
            d.deleteEntry(entry)
        }
        return changed(id)
    }

    @Synchronized
    fun moveEntry(id: String, groupId: String): Change {
        val d = requireWritable()
        val entry = entryById(d, id)
        val target = group(d, groupId)
        entry.touch(modified = true, touchParents = true)
        d.moveEntryTo(entry, target)
        return changed(id)
    }

    @Synchronized
    fun createGroup(parentId: String, name: String): Change {
        val d = requireWritable()
        val parent = group(d, parentId)
        val clean = checkGroupName(d, name)
        val group = d.createGroup() ?: throw VaultException(VaultError.INTERNAL, "The engine could not create a folder")
        group.title = clean
        group.touch(modified = true, touchParents = true)
        parent.touch(modified = true, touchParents = true)
        d.addGroupTo(group, parent)
        return changed(idOf(group.nodeId))
    }

    @Synchronized
    fun renameGroup(id: String, name: String): Change {
        val d = requireWritable()
        val group = group(d, id)
        if (group.parent == null) throw VaultException(VaultError.INVALID, "The root folder cannot be renamed")
        group.title = checkGroupName(d, name)
        group.touch(modified = true, touchParents = true)
        d.updateGroup(group)
        return changed(id)
    }

    /** Like [deleteEntry]: the folder and everything in it go to the recycle bin, or are removed for good when it is already there. */
    @Synchronized
    fun deleteGroup(id: String): Change {
        val d = requireWritable()
        val group = group(d, id)
        if (group.parent == null) throw VaultException(VaultError.INVALID, "The root folder cannot be deleted")
        if (idOf(group.nodeId) == binId(d)) throw VaultException(VaultError.INVALID, "The recycle bin itself cannot be deleted")
        val oldParent = group.parent
        group.touch(modified = true, touchParents = true)
        if (d.isRecycleBinEnabled) d.ensureRecycleBinExists(RECYCLE_BIN_NAME)
        if (d.canRecycle(group)) {
            d.recycle(group, RECYCLE_BIN_NAME)
            group.setPreviousParentGroup(oldParent)
            group.touch(modified = true, touchParents = true)
        } else {
            d.deleteGroup(group)
        }
        return changed(id)
    }

    /** Removes everything in the recycle bin for good, with ONE save (not one Argon2 save per item). Nothing to do = no change and no save. */
    @Synchronized
    fun emptyRecycleBin(): Change {
        val d = requireWritable()
        val bin = d.recycleBin ?: return Change(null, !dirty)
        val entries = bin.getChildEntries().toList()
        val groups = bin.getChildGroups().toList()
        if (entries.isEmpty() && groups.isEmpty()) return Change(null, !dirty)
        entries.forEach { d.deleteEntry(it) }
        groups.forEach { d.deleteGroup(it) }
        return changed(null)
    }

    // ---------------------------------------------------------------- saving and the "file changed elsewhere" question (D50)

    /** Tries again to write pending changes, for instance after a write error. A pending conflict stays a conflict. */
    @Synchronized
    fun retrySave(): Change {
        requireDb()
        return if (dirty) Change(null, persist()) else Change(null, true)
    }

    /**
     * The owner's answer to the D50 question. MERGE and KEEP_FILE need the master password again because the other copy has to be read
     * (the session does not keep it). OVERWRITE needs nothing.
     */
    @Synchronized
    fun resolve(choice: Int, password: CharArray?, keyFile: ByteArray?): Change {
        val d = requireDb()
        val st = store ?: throw VaultException(VaultError.LOCKED, "The vault is locked")
        if (!conflict) return Change(null, !dirty)
        when (choice) {
            Resolve.OVERWRITE -> writeToStore(null, forceBackup = true) // the other version is kept as a backup first
            Resolve.MERGE -> {
                requireCredential(password, keyFile)
                val credential = MasterCredential(password?.copyOf(), keyFile?.copyOf())
                try {
                    if (!d.isMergeDataAllowed()) throw VaultException(VaultError.UNSUPPORTED, "This file format cannot be merged")
                    st.openInput().use { input -> d.mergeData(input, credential, NO_HARDWARE_KEY, limits, null) }
                } catch (e: Throwable) {
                    throw map(e)
                } finally {
                    credential.clear()
                }
                writeToStore(null, forceBackup = true)
            }
            Resolve.KEEP_FILE -> open(st, password, keyFile, d.isReadOnly)
            else -> throw VaultException(VaultError.INVALID, "Unknown choice $choice")
        }
        return Change(null, true)
    }

    // ---------------------------------------------------------------- internals

    private fun changed(id: String?): Change {
        dirty = true
        return Change(id, persist())
    }

    /** True = written. False = a conflict is waiting (D50). Throws VaultException on a write error; the change then stays pending. */
    private fun persist(): Boolean {
        val st = store ?: throw VaultException(VaultError.LOCKED, "The vault is locked")
        val now = st.stamp()
        if (conflict || (openedStamp != null && now != null && now != openedStamp)) {
            conflict = true
            return false
        }
        writeToStore(null)
        return true
    }

    /** [credential] only on the very first save of a new vault; afterwards the engine reuses the key it derived when unlocking. */
    private fun writeToStore(credential: MasterCredential?, forceBackup: Boolean = false) {
        val d = requireDb()
        val st = store ?: throw VaultException(VaultError.LOCKED, "The vault is locked")
        val tmp = File(workDir, "save-${System.nanoTime()}.tmp")
        try {
            VaultBackups.backup(st, force = forceBackup || !backedUpThisSession)
            backedUpThisSession = true
            // The engine serializes into `tmp` first and only then copies into the real stream: a failure while encoding never touches the file.
            d.saveData(tmp, { st.openOutput() }, credential, NO_HARDWARE_KEY, limits)
        } catch (e: Throwable) {
            throw map(e)
        } finally {
            tmp.delete()
        }
        openedStamp = st.stamp()
        dirty = false
        conflict = false
    }

    /**
     * KeePassDX checks the key-derivation memory only when SAVING, never when opening, so a vault asking for gigabytes of Argon2 memory
     * would be attempted and could take the phone down. Here the header (no key work yet) is read first and the engine's own check
     * ([limits]) is applied to it: such a vault is refused with TOO_HEAVY instead.
     */
    private fun checkKdfLimits(store: VaultStore) {
        try {
            store.openInput().use { input ->
                val probe = DatabaseKDBX()
                DatabaseHeaderKDBX(probe).loadFromFile(input)
                probe.kdfEngine?.checkLimits(limits)
            }
        } catch (e: KDFMemoryDatabaseException) {
            throw e
        } catch (_: Exception) {
            // not a KDBX file, or unreadable: the real open below reports exactly what is wrong
        }
    }

    private fun requireDb(): DatabaseInfo = db ?: throw VaultException(VaultError.LOCKED, "The vault is locked")

    private fun requireWritable(): DatabaseInfo {
        val d = requireDb()
        if (d.isReadOnly) throw VaultException(VaultError.READ_ONLY, "The vault was opened read-only")
        return d
    }

    private fun requireCredential(password: CharArray?, keyFile: ByteArray?) {
        if ((password == null || password.isEmpty()) && (keyFile == null || keyFile.isEmpty())) {
            throw VaultException(VaultError.INVALID, "A password or a key file is needed")
        }
    }

    private fun wipe(database: DatabaseInfo) {
        try {
            database.clearAndClose(workDir)
        } catch (_: Exception) {
            // best effort: the files are in the app's private cache and are removed again on the next lock
        }
    }

    private fun group(d: DatabaseInfo, id: String?): Group {
        if (id == null) return d.rootGroup ?: throw VaultException(VaultError.INTERNAL, "The vault has no root folder")
        return d.getGroupById(parseGroupId(id)) ?: throw VaultException(VaultError.NOT_FOUND, "Folder not found")
    }

    private fun entryById(d: DatabaseInfo, id: String): Entry {
        val uuid = try {
            UUID.fromString(id)
        } catch (_: IllegalArgumentException) {
            throw VaultException(VaultError.NOT_FOUND, "Entry not found")
        }
        return d.getEntryById(NodeIdUUID(uuid)) ?: throw VaultException(VaultError.NOT_FOUND, "Entry not found")
    }

    private fun isAppField(name: String): Boolean =
        name.startsWith(APP_FIELD) && name.length >= APP_FIELD.length && name.substring(APP_FIELD.length).all { it.isDigit() }

    private fun isUnder(e: Entry, group: Group): Boolean {
        var p: Group? = e.parent
        while (p != null) {
            if (p.nodeId == group.nodeId) return true
            p = p.parent
        }
        return false
    }

    private fun binId(d: DatabaseInfo): String? = d.recycleBin?.let { idOf(it.nodeId) }

    private fun walk(group: Group, skipId: String?, visit: (Entry) -> Unit) {
        group.getChildEntries().forEach(visit)
        for (child in group.getChildGroups()) {
            if (skipId != null && idOf(child.nodeId) == skipId) continue
            walk(child, skipId, visit)
        }
    }

    private fun ref(g: Group) = GroupRef(idOf(g.nodeId), g.title, g.getChildEntries().size, g.getChildGroups().size)

    private fun pathOf(g: Group): List<GroupRef> {
        val path = ArrayList<GroupRef>()
        var current: Group? = g
        while (current != null) {
            path.add(0, ref(current))
            current = current.parent
        }
        return path
    }

    private fun summary(e: Entry): EntrySummary {
        val title = e.title.ifBlank { e.username.ifBlank { e.url } }
        return EntrySummary(
            id = idOf(e.nodeId),
            groupId = e.parent?.let { idOf(it.nodeId) } ?: "",
            title = title,
            username = e.username,
            url = e.url,
            hasPassword = e.password.isNotEmpty(),
            modified = e.lastModificationTime.toMilliseconds(),
        )
    }

    private fun checkFields(fields: List<FieldItem>) {
        val seen = HashSet<String>()
        for (f in fields) {
            val name = f.name.trim()
            if (name.isEmpty()) throw VaultException(VaultError.INVALID, "A custom field needs a name")
            if (name.lowercase() in STANDARD_FIELDS) throw VaultException(VaultError.INVALID, "\"$name\" is a standard field")
            if (!seen.add(name.lowercase())) throw VaultException(VaultError.INVALID, "Two fields are named \"$name\"")
        }
    }

    private fun checkGroupName(d: DatabaseInfo, name: String): String {
        val clean = name.trim()
        if (clean.isEmpty()) throw VaultException(VaultError.INVALID, "A folder needs a name")
        if (d.groupNamesNotAllowed.contains(clean)) throw VaultException(VaultError.INVALID, "\"$clean\" is not allowed as a folder name")
        return clean
    }

    /** Writes the draft into [entry]. [keepFrom]: the stored entry, to keep a protected field's value or the password when the draft sends none. */
    private fun fill(entry: Entry, draft: EntryDraft, keepFrom: Entry?) {
        val stored = keepFrom?.getExtraFields().orEmpty()
        val fields = draft.fields.map { f ->
            val name = f.name.trim()
            val value = f.value ?: stored.firstOrNull { it.name == name }?.protectedValue?.toString() ?: ""
            Field(name, ProtectedString(f.protect, value))
        }
        // Same order as KeePassDX's saveEntryInfo: drop the custom fields first, then set the standard ones.
        entry.removeAllFields()
        entry.addExtraFields(fields)
        entry.title = draft.title
        entry.username = draft.username
        entry.password = draft.password?.toCharArray() ?: keepFrom?.password ?: CharArray(0)
        entry.url = draft.url
        entry.notes = draft.notes
    }

    private fun map(e: Throwable): VaultException = when (e) {
        is VaultException -> e
        is InvalidCredentialsDatabaseException, is HeaderHmacMismatchException ->
            VaultException(VaultError.WRONG_CREDENTIALS, "Wrong password or key file (or the file is damaged)", e)
        is EmptyKeyDatabaseException -> VaultException(VaultError.INVALID, "A password or a key file is needed", e)
        is FileNotFoundDatabaseException -> VaultException(VaultError.FILE_NOT_FOUND, "The vault file was not found", e)
        is SignatureDatabaseException, is CorruptedDatabaseException, is XMLMalformedDatabaseException, is DuplicateUuidDatabaseException ->
            VaultException(VaultError.NOT_A_VAULT, "This is not a valid KeePass file, or it is damaged", e)
        is VersionDatabaseException, is InvalidAlgorithmDatabaseException, is HardwareKeyDatabaseException ->
            VaultException(VaultError.UNSUPPORTED, "This vault uses a format or key type that is not supported", e)
        is KDFMemoryDatabaseException, is KDFIterationDatabaseException, is KDFParallelismDatabaseException, is NoMemoryDatabaseException ->
            VaultException(VaultError.TOO_HEAVY, "This vault's key settings need more memory than this phone has free right now", e)
        is OutOfMemoryError -> VaultException(VaultError.TOO_HEAVY, "Not enough memory to open this vault", e)
        is DatabaseOutputException -> VaultException(VaultError.IO, "The vault could not be written: ${e.innerMessage ?: ""}".trim(), e)
        is DatabaseException -> VaultException(VaultError.INTERNAL, e.innerMessage ?: e.javaClass.simpleName, e)
        is FileNotFoundException -> VaultException(VaultError.FILE_NOT_FOUND, "The vault file was not found", e)
        is IOException -> VaultException(VaultError.IO, "The vault file could not be read or written: ${e.message ?: ""}".trim(), e)
        else -> VaultException(VaultError.INTERNAL, e.javaClass.simpleName + (e.message?.let { ": $it" } ?: ""), e)
    }

    companion object {
        const val ROOT_NAME = "Root"
        const val RECYCLE_BIN_NAME = "Recycle Bin"

        /** The custom field KeePassDX uses for an Android app link (AppOriginEntryField.APPLICATION_ID_FIELD_NAME); more apps: "AndroidApp1", "AndroidApp2" ... */
        const val APP_FIELD = "AndroidApp"

        /** The engine's own standard field names: a custom field may not use them. */
        private val STANDARD_FIELDS = setOf("title", "username", "password", "url", "notes")

        /** Accepts every key-derivation setting. Tests and callers that did their own check use it. */
        val PERMISSIVE = Limits(isMemorySufficient = { _, _ -> true }, parallelism = 1L)

        /** What the benchmark may use on a phone when the caller gives nothing better: 64 MiB, 2 lanes. */
        val DEFAULT_KDF_LIMITS = KdfLimits(memory = 64UL * 1024UL * 1024UL, parallelism = 2L)

        private val NO_HARDWARE_KEY: (HardwareKey, ByteArray?) -> ByteArray = { _, _ -> ByteArray(0) }

        fun idOf(id: NodeId<*>): String = when (id) {
            is NodeIdInt -> "i${id.id}"
            is NodeIdUUID -> id.id.toString()
            else -> id.toVisualString() ?: ""
        }

        fun parseGroupId(id: String): NodeId<*> = try {
            if (id.startsWith("i")) NodeIdInt(id.substring(1).toInt()) else NodeIdUUID(UUID.fromString(id))
        } catch (_: IllegalArgumentException) {
            throw VaultException(VaultError.NOT_FOUND, "Folder not found")
        }
    }
}
