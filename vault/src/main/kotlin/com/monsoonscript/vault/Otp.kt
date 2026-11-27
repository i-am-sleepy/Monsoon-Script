package com.monsoonscript.vault

import com.kunzisoft.keepass.otp.OtpElement
import com.kunzisoft.keepass.otp.OtpEntryFields
import com.kunzisoft.keepass.otp.OtpTokenType
import com.kunzisoft.keepass.otp.OtpType

/**
 * One-time codes (E1.18): reading what an entry stores and working out the current code, using the engine's own `otp` package (the same parsing as
 * KeePassDX, so a code stored by KeePassDX/KeePassXC works here and the other way round). Runs in the vault process only.
 */
internal object Otp {
    /**
     * What the owner typed or pasted: an `otpauth://` link (as in a QR code) or only the secret key (base32, spaces and dashes allowed; then 6 digits every
     * 30 seconds, SHA-1, like most websites). Anything else, or a link without a usable secret, is refused with INVALID.
     */
    fun parseInput(text: String): OtpElement {
        val clean = text.trim()
        if (clean.isEmpty()) throw VaultException(VaultError.INVALID, "The one-time code key is empty")
        if (OtpEntryFields.isOTPUri(clean)) {
            val element = OtpEntryFields.parseOTPUri(clean) ?: throw VaultException(VaultError.INVALID, "This one-time code link is not valid")
            if (element.secret == null) throw VaultException(VaultError.INVALID, "This one-time code link has no usable secret key")
            return element
        }
        val key = clean.filter { it != ' ' && it != '-' }.toCharArray()
        if (key.size < OtpElement.MIN_OTP_SECRET || !OtpElement.isValidBase32(key)) {
            throw VaultException(VaultError.INVALID, "This is neither an otpauth:// link nor a valid secret key (letters A-Z and digits 2-7)")
        }
        val element = OtpElement()
        element.type = OtpType.TOTP
        try {
            element.setBase32Secret(key)
        } catch (e: IllegalArgumentException) {
            throw VaultException(VaultError.INVALID, "This is not a valid secret key", e)
        }
        return element
    }

    /** The field value to store for [element]: the standard `otpauth://` link that KeePassDX and KeePassXC also write. */
    fun storedValue(element: OtpElement): String = OtpEntryFields.buildOtpField(element).protectedValue.toString()

    /** The code valid right now, or null when [element] has no secret. */
    fun codeOf(element: OtpElement): OtpCode? {
        if (element.secret == null) return null
        if (element.type == OtpType.HOTP) return OtpCode(String(element.token), 0, 0, OtpKind.HOTP)
        val period = element.period
        // The code and the seconds left must belong to the same step: compute again if the clock stepped over a boundary in between.
        var now = System.currentTimeMillis() / 1000
        var token = element.token
        val after = System.currentTimeMillis() / 1000
        if (after / period != now / period) {
            now = after
            token = element.token
        }
        val kind = if (element.tokenType == OtpTokenType.STEAM) OtpKind.STEAM else OtpKind.TOTP
        return OtpCode(String(token), (period - (now % period)).toInt(), period, kind)
    }
}
