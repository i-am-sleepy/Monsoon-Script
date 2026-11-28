package com.monsoonscript.keyboard.autofill

import android.app.assist.AssistStructure
import android.content.Intent
import android.os.Build
import android.os.Parcelable
import android.view.View
import android.view.autofill.AutofillId

/**
 * What an Autofill request is about (E1.19, D100): which app or website, and which fields on the screen make up the login. The texts are what the fields hold
 * right now (the save prompt, E1.20, needs them); they are never written anywhere.
 */
internal class ParsedForm(
    val packageName: String,
    /** The website, when the screen is a web page in a browser (null for a native app). */
    val webDomain: String?,
    val usernameId: AutofillId?,
    val passwordId: AutofillId?,
    val newPasswordId: AutofillId?,
    val otpId: AutofillId?,
    val usernameText: String? = null,
    /** The text of the new-password field when there is one, else of the password field. */
    val passwordText: String? = null,
) {
    /** The fields a tap on an entry fills: the login (user name and password), or on a page with only a code field the code. Nothing on a sign-up page. */
    val fillIds: List<AutofillId>
        get() = when {
            passwordId == null && newPasswordId != null -> emptyList()
            usernameId != null || passwordId != null -> listOfNotNull(usernameId, passwordId)
            else -> listOfNotNull(otpId)
        }

    /** True for a page that has a code field and nothing else: the entries offered are the ones with a one-time code. */
    val codeOnly: Boolean get() = usernameId == null && passwordId == null && newPasswordId == null && otpId != null

    /** A password field (current one) is there: the "Search vault…" item is shown when nothing matched. */
    val hasPassword: Boolean get() = passwordId != null
}

/** Reads the screen the Autofill framework describes. */
internal object AutofillParser {
    private class Field(val id: AutofillId, val role: FieldRole, val focused: Boolean, val text: String?)

    /** Null when the screen is our own app (the vault's own password boxes are never filled) or has no field a login could use. */
    fun parse(structure: AssistStructure, ownPackage: String): ParsedForm? {
        val pkg = structure.activityComponent?.packageName ?: return null
        if (pkg == ownPackage) return null
        val fields = ArrayList<Field>()
        val web = arrayOfNulls<String>(1)
        for (i in 0 until structure.windowNodeCount) walk(structure.getWindowNodeAt(i).rootViewNode, fields, web)
        val pick = pickForm(fields.map { it.role }, fields.indexOfFirst { it.focused })
        if (!pick.interesting) return null
        fun id(i: Int?) = i?.let { fields[it].id }
        val passwordIndex = pick.newPassword ?: pick.password
        return ParsedForm(
            packageName = pkg,
            webDomain = web[0],
            usernameId = id(pick.username),
            passwordId = id(pick.password),
            newPasswordId = id(pick.newPassword),
            otpId = id(pick.otp),
            usernameText = pick.username?.let { fields[it].text }?.takeIf { it.isNotEmpty() },
            passwordText = passwordIndex?.let { fields[it].text }?.takeIf { it.isNotEmpty() },
        )
    }

    private fun walk(node: AssistStructure.ViewNode, out: MutableList<Field>, web: Array<String?>) {
        if (web[0] == null) web[0] = node.webDomain?.takeIf { it.isNotBlank() }
        val id = node.autofillId
        if (id != null && node.autofillType == View.AUTOFILL_TYPE_TEXT && node.visibility == View.VISIBLE) {
            val value = node.autofillValue?.takeIf { it.isText }?.textValue?.toString()
            out += Field(id, FieldClassifier.classify(infoOf(node)), node.isFocused, value)
        }
        for (i in 0 until node.childCount) walk(node.getChildAt(i), out, web)
    }

    private fun infoOf(node: AssistStructure.ViewNode): FieldInfo {
        var type: String? = null
        var name: String? = null
        var htmlId: String? = null
        var autocomplete: String? = null
        val html = node.htmlInfo
        if (html != null && "input".equals(html.tag, ignoreCase = true)) {
            for (p in html.attributes.orEmpty()) {
                when (p.first?.lowercase()) {
                    "type" -> type = p.second
                    "name" -> name = p.second
                    "id" -> htmlId = p.second
                    "autocomplete" -> autocomplete = p.second
                }
            }
        }
        return FieldInfo(
            hints = node.autofillHints?.toList().orEmpty(),
            inputType = node.inputType,
            htmlType = type,
            htmlName = name,
            htmlId = htmlId,
            htmlAutocomplete = autocomplete,
            idEntry = node.idEntry,
            hint = node.hint,
        )
    }
}

// ---------------------------------------------------------------------------------------------------------------------------------------------- intents
// The Autofill framework hands our own activities back an Intent we built ourselves, so the form travels in its extras (the texts do not: nothing the
// owner typed goes into an Intent).

internal const val X_PACKAGE = "af.package"
internal const val X_WEB = "af.web"
internal const val X_USER = "af.user"
internal const val X_PASS = "af.pass"
internal const val X_NEW = "af.new"
internal const val X_OTP = "af.otp"
internal const val X_MODE = "af.mode"
internal const val X_ENTRY = "af.entry"

internal fun Intent.putForm(form: ParsedForm): Intent = apply {
    putExtra(X_PACKAGE, form.packageName)
    putExtra(X_WEB, form.webDomain)
    putExtra(X_USER, form.usernameId)
    putExtra(X_PASS, form.passwordId)
    putExtra(X_NEW, form.newPasswordId)
    putExtra(X_OTP, form.otpId)
}

internal fun Intent.form(): ParsedForm? {
    val pkg = getStringExtra(X_PACKAGE) ?: return null
    return ParsedForm(pkg, getStringExtra(X_WEB), parcel(X_USER), parcel(X_PASS), parcel(X_NEW), parcel(X_OTP))
}

private inline fun <reified T : Parcelable> Intent.parcel(key: String): T? =
    if (Build.VERSION.SDK_INT >= 33) getParcelableExtra(key, T::class.java) else @Suppress("DEPRECATION") getParcelableExtra(key)
