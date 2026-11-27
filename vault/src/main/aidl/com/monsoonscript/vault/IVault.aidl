package com.monsoonscript.vault;

import android.net.Uri;
import com.monsoonscript.vault.BackupInfo;
import com.monsoonscript.vault.Change;
import com.monsoonscript.vault.EntryDetail;
import com.monsoonscript.vault.EntryDraft;
import com.monsoonscript.vault.EntrySummary;
import com.monsoonscript.vault.GroupListing;
import com.monsoonscript.vault.VaultInfo;
import com.monsoonscript.vault.VaultStatus;

/**
 * E1.13 (D44): what the app and the keyboard may ask of the vault process. A thin mirror of VaultSession (see its documentation
 * for the meaning of each call). Failures arrive as an IllegalStateException carrying a VaultError code (VaultWire); VaultClient turns
 * them back into VaultException. Calls block until the vault's single engine thread has done the work, so never call from the main thread.
 */
interface IVault {
    /** Process id of the vault process (the tests prove it differs from the caller's). */
    int processId();
    VaultStatus status();
    VaultInfo info();

    /** [file] is a document Uri the owner picked (D35) or a file Uri. [tune]: run the key-derivation benchmark (tests pass false). */
    void create(in Uri file, String name, in char[] password, in byte[] keyFile, boolean tune);
    void open(in Uri file, in char[] password, in byte[] keyFile, boolean readOnly);
    void lock();
    /** Inactivity time after which the open vault locks by itself, in milliseconds; 0 = never (D38: 5 minutes until it becomes a setting). */
    void setAutoLock(long inactivityMs);

    GroupListing list(String groupId);
    List<EntrySummary> search(String query);
    /** Entries that store this Android package as an app link (E1.14, D32). */
    List<EntrySummary> forApp(String packageName);
    /** Summaries for these ids, same order; missing ones are left out (the picker's recent list). */
    List<EntrySummary> summaries(in List<String> ids);
    EntryDetail entry(String id);
    char[] password(String id);
    String fieldValue(String id, String name);
    /** Writes the decrypted attached file [name] of the entry to the picked place [dest] (E1.17); the bytes never travel through Binder. */
    void saveAttachment(String id, String name, in Uri dest);

    Change saveEntry(in EntryDraft draft);
    Change deleteEntry(String id);
    Change moveEntry(String id, String groupId);
    Change createGroup(String parentId, String name);
    Change renameGroup(String id, String name);
    Change deleteGroup(String id);
    Change emptyRecycleBin();

    /** The kept earlier versions of the open vault, newest first (E1.16). */
    List<BackupInfo> backups();
    /** Replaces the vault file with that kept version (the current one is kept first) and LOCKS the vault: the restored file has its own master password. */
    void restoreBackup(String id);
    /** The same with a KeePass file the owner picked (checked before anything is touched). */
    void restoreFromFile(in Uri file);

    Change retrySave();
    Change resolve(int choice, in char[] password, in byte[] keyFile);
}
