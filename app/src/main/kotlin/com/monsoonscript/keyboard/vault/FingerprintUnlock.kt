package com.monsoonscript.keyboard.vault

import android.annotation.TargetApi
import android.app.Activity
import android.os.Build
import android.view.View
import android.view.ViewTreeObserver
import android.view.inputmethod.InputMethodManager
import com.monsoonscript.keyboard.R
import com.monsoonscript.vault.VaultError
import com.monsoonscript.vault.VaultException

/**
 * The fingerprint part of every unlock screen (the keyboard's unlock card and the app's unlock page share it, E1.15, D91):
 *  - [begin] when the screen opens: if the fingerprint is set up and the rules allow it, the phone's prompt shows at once; its button, a cancel or too many
 *    wrong fingers lead to the master password field, with a line saying why when the fingerprint switched itself off (restart, 3 days, new fingerprints ...);
 *  - [passwordRejected] when the stored password did not open the vault any more (it was changed elsewhere): switched off, the owner types the new one;
 *  - [afterTyped] when the master password was typed and was right: the 72 hours start again, or, when the switch in the vault settings is on but nothing is
 *    set up yet (first time, after a restart, after new fingerprints), the prompt asks for one touch to finish the set-up.
 * Every password copy that passes through here is wiped by whoever ends up holding it.
 */
@TargetApi(Build.VERSION_CODES.P)
internal class FingerprintUnlock(private val activity: Activity, private val prefs: VaultPrefs) {
    private val store = FingerprintStore(activity)
    private val prompt = FingerprintPrompt(activity)
    private var pending: CharArray? = null // the typed password copy while the set-up prompt is up

    /** True when [begin] is going to show the prompt (the screen can keep its soft keyboard down until it fails). Cheap, no side effects. */
    fun willPrompt(): Boolean =
        prefs.fingerprintOn && store.availability() == FingerprintStore.Availability.READY && store.armed && store.verdict() == FingerprintPolicy.Verdict.OK

    /** [onPassword] gets the stored master password (wipe it); [onTyping] says to show the password field, with a line to show above it or null. */
    fun begin(onPassword: (CharArray) -> Unit, onTyping: (String?) -> Unit) {
        if (!prefs.fingerprintOn) {
            if (store.armed) store.turnOff()
            onTyping(null)
            return
        }
        if (store.availability() != FingerprintStore.Availability.READY) {
            if (store.armed) store.disarm(FingerprintStore.Reason.CHANGED)
            onTyping(note(store.takeReason()))
            return
        }
        if (!store.armed) {
            onTyping(note(store.takeReason()))
            return
        }
        when (store.verdict()) {
            FingerprintPolicy.Verdict.OK -> Unit
            FingerprintPolicy.Verdict.AFTER_RESTART -> return off(FingerprintStore.Reason.RESTART, onTyping)
            FingerprintPolicy.Verdict.TOO_OLD -> return off(FingerprintStore.Reason.TOO_OLD, onTyping)
            FingerprintPolicy.Verdict.NOT_SET_UP -> return onTyping(null)
        }
        val cipher = store.decryptCipher()
        if (cipher == null) { // the key is gone or was destroyed: decryptCipher switched everything off and noted why
            onTyping(note(store.takeReason()))
            return
        }
        prompt.show(cipher, activity.getString(R.string.fp_prompt_title), prefs.fileName.ifEmpty { null }, activity.getString(R.string.fp_use_password)) { outcome, c ->
            when (outcome) {
                FingerprintPrompt.Outcome.OK -> {
                    val pw = c?.let { store.read(it) }
                    if (pw == null) off(FingerprintStore.Reason.BROKEN, onTyping) else onPassword(pw)
                }
                FingerprintPrompt.Outcome.LOCKED_OUT -> onTyping(activity.getString(R.string.fp_note_locked))
                else -> onTyping(null) // the button, a cancel, an error: the master password
            }
        }
    }

    private fun off(why: FingerprintStore.Reason, onTyping: (String?) -> Unit) {
        store.disarm(why)
        onTyping(note(store.takeReason()))
    }

    /** The unlock with the stored password failed with [e]. True when the stored password is the reason (it is thrown away and the line to show comes back via [passwordNote]). */
    fun passwordRejected(e: Throwable): Boolean {
        if ((e as? VaultException)?.code != VaultError.WRONG_CREDENTIALS) return false
        store.disarm(FingerprintStore.Reason.PASSWORD)
        return true
    }

    val passwordNote: String get() = activity.getString(R.string.fp_note_password)

    /**
     * The master password was typed and opened the vault. [typed] is a copy that this call wipes. [then] runs when everything here is done (after the set-up
     * prompt if there was one); it runs exactly once, unless the screen was closed meanwhile ([stop]).
     */
    fun afterTyped(typed: CharArray, then: () -> Unit) {
        if (!prefs.fingerprintOn || store.availability() != FingerprintStore.Availability.READY) {
            typed.fill('\u0000')
            then()
            return
        }
        if (store.armed) {
            store.noteMasterPasswordUnlock()
            typed.fill('\u0000')
            then()
            return
        }
        val cipher = if (typed.isEmpty() || store.setupSkipped) null else store.encryptCipher() // a key-file-only vault has no password text to keep
        if (cipher == null) {
            typed.fill('\u0000')
            then()
            return
        }
        pending = typed
        prompt.show(cipher, activity.getString(R.string.fp_setup_title), activity.getString(R.string.fp_setup_subtitle), activity.getString(R.string.fp_not_now)) { outcome, c ->
            pending = null
            if (outcome == FingerprintPrompt.Outcome.OK && c != null) {
                store.store(c, typed)
            } else {
                store.disarm(FingerprintStore.Reason.NONE) // the key made for it goes
                if (outcome != FingerprintPrompt.Outcome.ERROR) store.skipSetupThisBoot()
            }
            typed.fill('\u0000')
            then()
        }
    }

    /** The screen is going away: take a prompt down (nothing is delivered). */
    fun stop() {
        prompt.cancel()
        val p = pending ?: return
        p.fill('\u0000')
        pending = null
        store.disarm(FingerprintStore.Reason.NONE) // the key made for a set-up that never finished is useless
    }

    private fun note(r: FingerprintStore.Reason): String? = when (r) {
        FingerprintStore.Reason.NONE -> null
        FingerprintStore.Reason.RESTART -> activity.getString(R.string.fp_note_restart)
        FingerprintStore.Reason.TOO_OLD -> activity.getString(R.string.fp_note_old)
        FingerprintStore.Reason.CHANGED -> activity.getString(R.string.fp_note_changed)
        FingerprintStore.Reason.PASSWORD -> activity.getString(R.string.fp_note_password)
        FingerprintStore.Reason.BROKEN -> activity.getString(R.string.fp_note_broken)
    }
}

/** Puts the soft keyboard up for [view]: at once when its window has focus, otherwise as soon as it gets it (the fingerprint prompt has just closed). */
internal object SoftKeyboard {
    fun show(view: View) {
        view.requestFocus()
        val imm = view.context.getSystemService(InputMethodManager::class.java) ?: return
        if (view.hasWindowFocus()) {
            imm.showSoftInput(view, 0)
            return
        }
        view.viewTreeObserver.addOnWindowFocusChangeListener(object : ViewTreeObserver.OnWindowFocusChangeListener {
            override fun onWindowFocusChanged(hasFocus: Boolean) {
                if (!hasFocus) return
                if (view.viewTreeObserver.isAlive) view.viewTreeObserver.removeOnWindowFocusChangeListener(this)
                view.post { imm.showSoftInput(view, 0) }
            }
        })
    }
}
