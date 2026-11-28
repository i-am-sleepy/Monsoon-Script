package com.monsoonscript.keyboard.autofill

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.autofill.InlinePresentation
import android.view.autofill.AutofillManager
import android.view.inputmethod.InlineSuggestionsRequest
import android.annotation.TargetApi
import androidx.autofill.inline.UiVersions
import androidx.autofill.inline.v1.InlineSuggestionUi
import com.monsoonscript.keyboard.vault.VaultMainActivity

/**
 * The Autofill service's side of the chips in the keyboard strip (E1.21, D102): Android 11+ lets the keyboard ask for suggestions it can show itself. This turns
 * one entry into the content the keyboard's request allows. Only titles and user names go in; like the dropdown items, a chip needs a tap before any password
 * is fetched. Where the keyboard's style is not one this can draw, [presentation] gives null and that item stays in the dropdown only.
 */
@TargetApi(Build.VERSION_CODES.R)
internal class InlineChips(context: Context, private val request: InlineSuggestionsRequest) {
    // Long-pressing a chip opens this (a required part of the format); the vault is the natural place.
    private val attribution: PendingIntent =
        PendingIntent.getActivity(context, 0, Intent(context, VaultMainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
    private var next = 0

    fun presentation(title: String, subtitle: String?): InlinePresentation? {
        val specs = request.inlinePresentationSpecs
        if (specs.isEmpty()) return null
        val spec = specs[minOf(next++, specs.size - 1)]
        return try {
            if (!UiVersions.getVersions(spec.style).contains(UiVersions.INLINE_UI_VERSION_1)) return null
            val content = InlineSuggestionUi.newContentBuilder(attribution)
                .setContentDescription(listOfNotNull(title, subtitle).joinToString(", "))
                .setTitle(title)
                .apply { if (!subtitle.isNullOrBlank()) setSubtitle(subtitle) }
                .build()
            InlinePresentation(content.slice, spec, false)
        } catch (_: RuntimeException) {
            null // a chip that cannot be built must never break the dropdown: that item is simply not offered as a chip
        }
    }

    companion object {
        /** Null on Android 10 and older or when the keyboard asked for no chips. [request] is the framework's `InlineSuggestionsRequest` (typed loosely so old Androids never load it). */
        fun of(context: Context, request: Any?): InlineChips? =
            if (Build.VERSION.SDK_INT >= 30 && request is InlineSuggestionsRequest) InlineChips(context, request) else null

        /** The request handed to the unlock screen when the owner tapped the "Unlock vault" chip: the real list comes back as chips too. */
        fun fromIntent(context: Context, intent: Intent): InlineChips? {
            if (Build.VERSION.SDK_INT < 30) return null
            val request = if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(AutofillManager.EXTRA_INLINE_SUGGESTIONS_REQUEST, InlineSuggestionsRequest::class.java)
            } else {
                @Suppress("DEPRECATION") intent.getParcelableExtra(AutofillManager.EXTRA_INLINE_SUGGESTIONS_REQUEST)
            }
            return of(context, request)
        }
    }
}
