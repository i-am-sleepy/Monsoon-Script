package com.monsoonscript.keyboard.autofill

/**
 * What the Android Autofill framework tells us about one input field, reduced to plain values so that [FieldClassifier] is pure Kotlin and testable on a JVM
 * (E1.19, D100). Filled from a `ViewNode` by [AutofillParser].
 */
internal data class FieldInfo(
    /** `android:autofillHints` / the page's own hints ("username", "password", "emailAddress", "newPassword", "oneTimeCode"...). */
    val hints: List<String> = emptyList(),
    /** `android.text.InputType` bits. */
    val inputType: Int = 0,
    /** For a field in a web page: the `type`, `name`, `id` and `autocomplete` attributes of the `<input>`. */
    val htmlType: String? = null,
    val htmlName: String? = null,
    val htmlId: String? = null,
    val htmlAutocomplete: String? = null,
    /** For a native app: the field's resource entry name (`R.id.xxx`) and its hint text. */
    val idEntry: String? = null,
    val hint: String? = null,
)

/** What a field is for, as far as filling logins is concerned. */
internal enum class FieldRole { USERNAME, PASSWORD, NEW_PASSWORD, OTP, OTHER }

/**
 * Decides whether a field is a user name, a password (current or new), a one-time code, or none of these. The explicit signs come first (autofill hints, the
 * HTML `autocomplete` attribute, `type=password`, the input type) and only then the words in the field's name, id or hint. The words are chosen narrowly:
 * a wrong "yes" offers a password in a search box, and the owner would soon switch autofill off.
 */
internal object FieldClassifier {
    // android.text.InputType, as plain numbers so this stays pure Kotlin
    private const val MASK_CLASS = 0xf
    private const val MASK_VARIATION = 0xff0
    private const val CLASS_TEXT = 0x1
    private const val CLASS_NUMBER = 0x2
    private const val TEXT_EMAIL = 0x20
    private const val TEXT_PASSWORD = 0x80
    private const val TEXT_WEB_EMAIL = 0xd0
    private const val TEXT_VISIBLE_PASSWORD = 0x90
    private const val TEXT_WEB_PASSWORD = 0xe0
    private const val NUMBER_PASSWORD = 0x10

    private val NEW_WORDS = listOf("new", "confirm", "retype", "repeat", "again", "verify")

    // Short words must be a whole word ("otp_input", "enterOtp"), or "footprint" and "slot_picker" would count. Longer ones may sit inside a longer name.
    private val OTP_TOKENS = setOf("otp", "totp", "2fa", "mfa")
    private val OTP_PHRASES = listOf("one time", "onetime", "authenticator", "verification code", "verificationcode", "auth code", "authcode", "authentication code")
    private val PASSWORD_TOKENS = setOf("pwd", "pw", "pass")
    private val PASSWORD_WORDS = listOf("password", "passwd", "passwort", "passcode")
    private val USERNAME_WORDS = listOf("username", "user name", "userid", "user id", "login", "email", "e mail", "signin", "sign in", "identifier", "account")

    /** "newPassword", "login-user_id" and "E-mail or phone" become "new password", "login user id" and "e mail or phone". */
    private fun words(text: String): String =
        text.replace(Regex("([a-z])([A-Z])"), "$1 $2").lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()

    fun classify(f: FieldInfo): FieldRole {
        fromHints(f.hints)?.let { return it }
        fromAutocomplete(f.htmlAutocomplete)?.let { return it }
        val text = words(listOfNotNull(f.idEntry, f.htmlName, f.htmlId, f.hint).joinToString(" "))
        val tokens = text.split(' ')
        when (f.htmlType?.lowercase()) {
            "password" -> return passwordRole(text)
            "email" -> return FieldRole.USERNAME
        }
        val variation = f.inputType and MASK_VARIATION
        when (f.inputType and MASK_CLASS) {
            CLASS_TEXT -> when (variation) {
                TEXT_PASSWORD, TEXT_WEB_PASSWORD, TEXT_VISIBLE_PASSWORD -> return passwordRole(text)
                TEXT_EMAIL, TEXT_WEB_EMAIL -> return FieldRole.USERNAME
            }
            CLASS_NUMBER -> if (variation == NUMBER_PASSWORD) return passwordRole(text)
        }
        if (text.isBlank()) return FieldRole.OTHER
        if (tokens.any { it in OTP_TOKENS } || OTP_PHRASES.any { text.contains(it) }) return FieldRole.OTP
        if (tokens.any { it in PASSWORD_TOKENS } || PASSWORD_WORDS.any { text.contains(it) }) return passwordRole(text)
        if (USERNAME_WORDS.any { text.contains(it) }) return FieldRole.USERNAME
        return FieldRole.OTHER
    }

    private fun passwordRole(text: String) = if (NEW_WORDS.any { text.contains(it) }) FieldRole.NEW_PASSWORD else FieldRole.PASSWORD

    private fun fromHints(hints: List<String>): FieldRole? {
        for (h in hints) {
            when (h.lowercase().replace("-", "").replace("_", "")) {
                "newpassword" -> return FieldRole.NEW_PASSWORD
                "password", "currentpassword" -> return FieldRole.PASSWORD
                "username", "emailaddress", "email" -> return FieldRole.USERNAME
                "onetimecode", "smsotpcode", "smsotp" -> return FieldRole.OTP
            }
        }
        return null
    }

    /** The HTML `autocomplete` attribute: space separated tokens, the last one says what the field is ("section-blue shipping username"). */
    private fun fromAutocomplete(value: String?): FieldRole? =
        when (value?.trim()?.lowercase()?.split(' ', '\t')?.lastOrNull { it.isNotEmpty() }) {
            "username", "email" -> FieldRole.USERNAME
            "current-password" -> FieldRole.PASSWORD
            "new-password" -> FieldRole.NEW_PASSWORD
            "one-time-code" -> FieldRole.OTP
            else -> null
        }
}

/** The positions (in the list of the form's text fields) of the fields a login needs; null where the form has none. */
internal data class FormPick(val username: Int?, val password: Int?, val newPassword: Int?, val otp: Int?) {
    /** The form has something we could fill or save. */
    val interesting: Boolean get() = username != null || password != null || newPassword != null || otp != null
}

/** Chooses, from the roles of all text fields of a screen in reading order, the ones that make up the login. [focused] is the field being tapped (-1 = none). */
internal fun pickForm(roles: List<FieldRole>, focused: Int): FormPick {
    fun first(role: FieldRole): Int? = if (focused in roles.indices && roles[focused] == role) focused else roles.indexOf(role).takeIf { it >= 0 }
    val password = first(FieldRole.PASSWORD)
    val newPassword = first(FieldRole.NEW_PASSWORD)
    val otp = first(FieldRole.OTP)
    // The user name is the nearest user-name field above the password; a form without one above it takes the first one anywhere.
    val anchor = password ?: newPassword
    var username: Int? = null
    if (anchor != null) {
        username = (anchor - 1 downTo 0).firstOrNull { roles[it] == FieldRole.USERNAME }
        // Plain apps often mark nothing: then the field right above the password is the user name.
        if (username == null && anchor > 0 && roles[anchor - 1] == FieldRole.OTHER) username = anchor - 1
    }
    if (username == null) username = if (focused in roles.indices && roles[focused] == FieldRole.USERNAME) focused else roles.indexOf(FieldRole.USERNAME).takeIf { it >= 0 }
    return FormPick(username, password, newPassword, otp)
}
