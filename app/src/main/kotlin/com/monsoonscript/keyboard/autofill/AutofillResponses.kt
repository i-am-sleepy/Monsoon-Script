package com.monsoonscript.keyboard.autofill

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.autofill.Dataset
import android.service.autofill.FillResponse
import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import com.monsoonscript.keyboard.R
import com.monsoonscript.vault.EntrySummary
import com.monsoonscript.vault.IVault
import java.util.concurrent.atomic.AtomicInteger

/**
 * Builds what the Autofill dropdown shows and what a tap hands back (E1.19, D100). Every item needs a tap on our own activity first ([AutofillFillActivity]),
 * so a password is fetched from the vault only after the owner chose the entry, and only when the vault is open: nothing secret is in a response.
 */
internal object AutofillResponses {
    const val MODE_UNLOCK = "unlock"
    const val MODE_FILL = "fill"
    const val MODE_SEARCH = "search"

    private val codes = AtomicInteger(1)

    /** The dropdown of a locked vault: one item that opens the unlock card and then shows the real list. Null when this screen has nothing to fill. */
    fun unlockResponse(context: Context, form: ParsedForm): FillResponse? {
        val ids = form.fillIds
        if (ids.isEmpty()) return null
        val sender = sender(context, Intent(context, AutofillFillActivity::class.java).putForm(form).putExtra(X_MODE, MODE_UNLOCK))
        return FillResponse.Builder()
            .setAuthentication(ids.toTypedArray(), sender, item(context, context.getString(R.string.autofill_unlock), null))
            .build()
    }

    /**
     * The dropdown of an open vault: one item per matching entry; when nothing matched and a password is asked for, one "Search vault…" item; otherwise
     * nothing at all (no response, so a username-only form with no match shows no menu).
     */
    fun matchResponse(context: Context, form: ParsedForm, matches: List<EntrySummary>): FillResponse? {
        val ids = form.fillIds
        if (ids.isEmpty()) return null
        val shown = if (form.codeOnly) matches.filter { it.hasOtp } else matches
        val builder = FillResponse.Builder()
        if (shown.isEmpty()) {
            if (!form.hasPassword) return null
            builder.addDataset(dataset(context, ids, item(context, context.getString(R.string.autofill_search), null), Intent(context, AutofillSearchActivity::class.java).putForm(form)))
        } else {
            for (e in shown) {
                val intent = Intent(context, AutofillFillActivity::class.java).putForm(form).putExtra(X_MODE, MODE_FILL).putExtra(X_ENTRY, e.id)
                val title = e.title.ifBlank { e.url }.ifBlank { context.getString(R.string.autofill_untitled) }
                builder.addDataset(dataset(context, ids, item(context, title, e.username), intent))
            }
        }
        return builder.build()
    }

    /** What a tap on an entry fills: fetched from the vault now (blocking: call it off the main thread). Null when the entry has nothing for these fields. */
    fun filled(vault: IVault, entryId: String, form: ParsedForm): Dataset? {
        val builder = Dataset.Builder()
        var any = false
        if (form.codeOnly) {
            val otp = form.otpId
            val code = if (otp != null) vault.otpCode(entryId) else null
            if (otp != null && code != null) {
                builder.setValue(otp, AutofillValue.forText(code.code))
                any = true
            }
        } else {
            val summary = vault.summaries(listOf(entryId)).firstOrNull() ?: return null
            val user = form.usernameId
            if (user != null && summary.username.isNotEmpty()) {
                builder.setValue(user, AutofillValue.forText(summary.username))
                any = true
            }
            val pass = form.passwordId
            if (pass != null) {
                val chars = vault.password(entryId)
                try {
                    if (chars.isNotEmpty()) {
                        builder.setValue(pass, AutofillValue.forText(String(chars)))
                        any = true
                    }
                } finally {
                    chars.fill('\u0000')
                }
            }
        }
        return if (any) builder.build() else null
    }

    // The Dataset.Builder(RemoteViews) form is the one that works on every Android from 8.0; the newer Presentations form needs API 33.
    @Suppress("DEPRECATION")
    private fun dataset(context: Context, ids: List<AutofillId>, presentation: RemoteViews, intent: Intent): Dataset {
        val b = Dataset.Builder(presentation)
        for (id in ids) b.setValue(id, null) // no value yet: it is fetched after the tap and the unlock
        b.setAuthentication(sender(context, intent))
        return b.build()
    }

    /** The framework fills extras into these Intents (the request's structure, client state), so they must be mutable; each has its own request code. */
    private fun sender(context: Context, intent: Intent) =
        PendingIntent.getActivity(
            context,
            codes.getAndIncrement(),
            intent,
            PendingIntent.FLAG_CANCEL_CURRENT or if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0,
        ).intentSender

    private fun item(context: Context, title: String, subtitle: String?): RemoteViews =
        RemoteViews(context.packageName, R.layout.autofill_item).apply {
            setTextViewText(R.id.autofill_title, title)
            if (subtitle.isNullOrBlank()) setViewVisibility(R.id.autofill_subtitle, android.view.View.GONE) else setTextViewText(R.id.autofill_subtitle, subtitle)
        }
}
