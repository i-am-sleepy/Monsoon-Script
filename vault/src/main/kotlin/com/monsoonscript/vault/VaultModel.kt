package com.monsoonscript.vault

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/*
 * E1.13 (D26): the data that crosses the boundary between our app and the KeePassDX engine. Nothing here refers to an engine class,
 * so the app, the keyboard and the vault process share these types and an engine upgrade never reaches them (VaultSession is the only
 * adapter). Ids are strings: a KDBX UUID ("8f3a..."), or "i<number>" for a folder of an old KeePass 1 (KDB) file.
 * Secrets (password, protected fields) are NOT part of these lists: they are fetched one at a time (D43: shown only on tap).
 * Times are epoch milliseconds.
 */

/** Error codes of [VaultException]; the service carries them across the process boundary as ServiceSpecificException.errorCode. */
object VaultError {
    const val WRONG_CREDENTIALS = 1
    const val FILE_NOT_FOUND = 2
    /** Not a KeePass file, or damaged. */
    const val NOT_A_VAULT = 3
    /** Newer or unknown format, cipher or key type. */
    const val UNSUPPORTED = 4
    /** The key-derivation settings need more memory than the phone has free (the vault is not opened instead of crashing). */
    const val TOO_HEAVY = 5
    const val IO = 6
    const val LOCKED = 7
    const val NOT_FOUND = 8
    /** The vault file changed since it was opened and saving would overwrite it (D50): nothing was written. */
    const val CHANGED_ELSEWHERE = 9
    const val READ_ONLY = 10
    /** A bad argument (a folder name that is not allowed, a custom field named like a standard one, ...). */
    const val INVALID = 11
    const val INTERNAL = 99
}

/** How to settle a vault file that changed elsewhere (D50). */
object Resolve {
    /** Merge the file's current content into my changes, then save (needs the master password again). */
    const val MERGE = 1
    /** Keep my version: overwrite the file. */
    const val OVERWRITE = 2
    /** Keep the file's version: my unsaved changes are dropped (needs the master password again). */
    const val KEEP_FILE = 3
}

class VaultException(val code: Int, message: String, cause: Throwable? = null) : Exception(message, cause)

@Parcelize
data class VaultStatus(
    val unlocked: Boolean,
    /** There are changes that could not be written yet (see [conflict], or a write error: call retrySave). */
    val dirty: Boolean,
    /** The file changed elsewhere and the owner has to choose (D50); pending changes stay in memory meanwhile. */
    val conflict: Boolean,
    val readOnly: Boolean,
) : Parcelable

@Parcelize
data class VaultInfo(
    val name: String,
    val entryCount: Int,
    val cipher: String,
    val formatVersion: String,
    val rootId: String,
    val recycleBinId: String?,
) : Parcelable

/** Result of every call that changes the vault. [saved] is false when the change is only in memory (a conflict is waiting, D50). */
@Parcelize
data class Change(val id: String?, val saved: Boolean) : Parcelable

@Parcelize
data class GroupRef(val id: String, val name: String, val entries: Int, val groups: Int) : Parcelable

@Parcelize
data class EntrySummary(
    val id: String,
    val groupId: String,
    val title: String,
    val username: String,
    val url: String,
    val hasPassword: Boolean,
    val modified: Long,
) : Parcelable

/** One folder: its sub-folders and entries, and the path from the root (the last item of [path] is the folder itself). */
@Parcelize
data class GroupListing(
    val group: GroupRef,
    val path: List<GroupRef>,
    val groups: List<GroupRef>,
    val entries: List<EntrySummary>,
) : Parcelable

/** A custom field. [value] is null for a protected field (fetch it with fieldValue) and, in a draft, means "keep the stored value". */
@Parcelize
data class FieldItem(val name: String, val value: String?, val protect: Boolean) : Parcelable

@Parcelize
data class EntryDetail(
    val summary: EntrySummary,
    val notes: String,
    val fields: List<FieldItem>,
    val created: Long,
    /** Null when the entry never expires. */
    val expires: Long?,
    val historyCount: Int,
) : Parcelable

/**
 * What the edit form sends back. [id] null = a new entry in [groupId]. [password] null = keep the stored password.
 * [fields] is the complete list of custom fields after the edit: a stored field missing from it is removed.
 */
@Parcelize
data class EntryDraft(
    val id: String?,
    val groupId: String,
    val title: String,
    val username: String,
    val password: String?,
    val url: String,
    val notes: String,
    val fields: List<FieldItem>,
) : Parcelable
