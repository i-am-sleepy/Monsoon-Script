package com.monsoonscript.keyboard.autofill

import org.junit.Assert.assertEquals
import org.junit.Test

class FieldClassifierTest {
    private fun role(f: FieldInfo) = FieldClassifier.classify(f)

    @Test
    fun autofillHintsDecideFirst() {
        assertEquals(FieldRole.USERNAME, role(FieldInfo(hints = listOf("username"))))
        assertEquals(FieldRole.USERNAME, role(FieldInfo(hints = listOf("emailAddress"))))
        assertEquals(FieldRole.PASSWORD, role(FieldInfo(hints = listOf("password"))))
        assertEquals(FieldRole.PASSWORD, role(FieldInfo(hints = listOf("current-password"))))
        assertEquals(FieldRole.NEW_PASSWORD, role(FieldInfo(hints = listOf("newPassword"))))
        assertEquals(FieldRole.NEW_PASSWORD, role(FieldInfo(hints = listOf("new-password"))))
        assertEquals(FieldRole.OTP, role(FieldInfo(hints = listOf("oneTimeCode"))))
        assertEquals(FieldRole.OTP, role(FieldInfo(hints = listOf("smsOTPCode"))))
        // a hint beats a misleading name
        assertEquals(FieldRole.USERNAME, role(FieldInfo(hints = listOf("username"), idEntry = "password_box")))
        // unrelated hints fall through to the other signs
        assertEquals(FieldRole.PASSWORD, role(FieldInfo(hints = listOf("creditCardNumber"), htmlType = "password")))
    }

    @Test
    fun theHtmlAutocompleteAttributeCounts() {
        assertEquals(FieldRole.USERNAME, role(FieldInfo(htmlAutocomplete = "username")))
        assertEquals(FieldRole.USERNAME, role(FieldInfo(htmlAutocomplete = "section-blue shipping email")))
        assertEquals(FieldRole.PASSWORD, role(FieldInfo(htmlAutocomplete = "current-password")))
        assertEquals(FieldRole.NEW_PASSWORD, role(FieldInfo(htmlAutocomplete = "new-password")))
        assertEquals(FieldRole.OTP, role(FieldInfo(htmlAutocomplete = "one-time-code")))
        assertEquals(FieldRole.OTHER, role(FieldInfo(htmlAutocomplete = "off")))
        assertEquals(FieldRole.OTHER, role(FieldInfo(htmlAutocomplete = "postal-code")))
    }

    @Test
    fun passwordAndEmailTypesAreRecognised() {
        assertEquals(FieldRole.PASSWORD, role(FieldInfo(htmlType = "password")))
        assertEquals(FieldRole.PASSWORD, role(FieldInfo(htmlType = "PASSWORD", htmlName = "pw")))
        assertEquals(FieldRole.USERNAME, role(FieldInfo(htmlType = "email")))
        assertEquals(FieldRole.PASSWORD, role(FieldInfo(inputType = 0x81))) // text + password
        assertEquals(FieldRole.PASSWORD, role(FieldInfo(inputType = 0xe1))) // text + web password
        assertEquals(FieldRole.PASSWORD, role(FieldInfo(inputType = 0x91))) // text + visible password
        assertEquals(FieldRole.PASSWORD, role(FieldInfo(inputType = 0x12))) // number + password (a PIN)
        assertEquals(FieldRole.USERNAME, role(FieldInfo(inputType = 0x21))) // text + email
        assertEquals(FieldRole.USERNAME, role(FieldInfo(inputType = 0xd1))) // text + web email
        assertEquals(FieldRole.OTHER, role(FieldInfo(inputType = 0x1))) // plain text
        assertEquals(FieldRole.OTHER, role(FieldInfo(inputType = 0x2))) // plain number
    }

    @Test
    fun aPasswordFieldAskedForAgainIsANewPassword() {
        assertEquals(FieldRole.NEW_PASSWORD, role(FieldInfo(htmlType = "password", htmlName = "confirm_password")))
        assertEquals(FieldRole.NEW_PASSWORD, role(FieldInfo(htmlType = "password", htmlId = "newPassword")))
        assertEquals(FieldRole.NEW_PASSWORD, role(FieldInfo(inputType = 0x81, idEntry = "repeat_password")))
        assertEquals(FieldRole.NEW_PASSWORD, role(FieldInfo(inputType = 0x81, hint = "Retype password")))
        assertEquals(FieldRole.NEW_PASSWORD, role(FieldInfo(idEntry = "new_password_input")))
        assertEquals(FieldRole.PASSWORD, role(FieldInfo(htmlType = "password", htmlName = "password")))
    }

    @Test
    fun wordsInTheNameCountOnlyWhenNothingMoreExplicitIsThere() {
        assertEquals(FieldRole.USERNAME, role(FieldInfo(idEntry = "login_username")))
        assertEquals(FieldRole.USERNAME, role(FieldInfo(htmlName = "user_id")))
        assertEquals(FieldRole.USERNAME, role(FieldInfo(hint = "E-mail or phone")))
        assertEquals(FieldRole.USERNAME, role(FieldInfo(htmlId = "identifierId")))
        assertEquals(FieldRole.PASSWORD, role(FieldInfo(idEntry = "edit_password")))
        assertEquals(FieldRole.PASSWORD, role(FieldInfo(hint = "Passwort")))
        assertEquals(FieldRole.OTP, role(FieldInfo(idEntry = "otp_input")))
        assertEquals(FieldRole.OTP, role(FieldInfo(htmlName = "totp")))
        assertEquals(FieldRole.OTP, role(FieldInfo(hint = "Enter your 2FA code")))
        assertEquals(FieldRole.OTP, role(FieldInfo(hint = "Verification code")))
        assertEquals(FieldRole.OTP, role(FieldInfo(idEntry = "enterOtp")))
        assertEquals(FieldRole.PASSWORD, role(FieldInfo(htmlId = "loginPwd")))
    }

    @Test
    fun ordinaryFieldsAreLeftAlone() {
        assertEquals(FieldRole.OTHER, role(FieldInfo()))
        assertEquals(FieldRole.OTHER, role(FieldInfo(idEntry = "search_box", hint = "Search")))
        assertEquals(FieldRole.OTHER, role(FieldInfo(htmlName = "q", htmlType = "text")))
        assertEquals(FieldRole.OTHER, role(FieldInfo(hint = "Promo code")))
        assertEquals(FieldRole.OTHER, role(FieldInfo(hint = "Security code"))) // a card's CVV, not a one-time code
        assertEquals(FieldRole.OTHER, role(FieldInfo(idEntry = "footprint_box")))
        assertEquals(FieldRole.OTHER, role(FieldInfo(idEntry = "slot_picker")))
        assertEquals(FieldRole.OTHER, role(FieldInfo(htmlName = "zip", hint = "Postal code")))
        assertEquals(FieldRole.OTHER, role(FieldInfo(idEntry = "passport_number")))
        assertEquals(FieldRole.OTHER, role(FieldInfo(htmlName = "comment")))
    }
}

class PickFormTest {
    private val U = FieldRole.USERNAME
    private val P = FieldRole.PASSWORD
    private val N = FieldRole.NEW_PASSWORD
    private val O = FieldRole.OTHER
    private val C = FieldRole.OTP

    @Test
    fun aNormalLoginFormIsFound() {
        val pick = pickForm(listOf(U, P), focused = 1)
        assertEquals(FormPick(username = 0, password = 1, newPassword = null, otp = null), pick)
    }

    @Test
    fun theUserNameIsTheNearestOneAboveThePassword() {
        // search box, e-mail, e-mail again (another form), password
        val pick = pickForm(listOf(O, U, U, P), focused = 3)
        assertEquals(2, pick.username)
        assertEquals(3, pick.password)
    }

    @Test
    fun anUnmarkedFieldRightAboveThePasswordIsTheUserName() {
        val pick = pickForm(listOf(O, P), focused = 0)
        assertEquals(0, pick.username)
        assertEquals(1, pick.password)
        // only the field right above counts, and only when it is unmarked: a search box higher up is not a user name, and a code field is not one either
        assertEquals(1, pickForm(listOf(O, O, P), focused = 2).username)
        assertEquals(null, pickForm(listOf(O, C, P), focused = 2).username)
    }

    @Test
    fun aUserNameOnlyPageHasNoPassword() {
        val pick = pickForm(listOf(O, U), focused = 1)
        assertEquals(FormPick(username = 1, password = null, newPassword = null, otp = null), pick)
        assertEquals(true, pick.interesting)
    }

    @Test
    fun aSignUpFormHasNewPasswordsAndNoCurrentOne() {
        val pick = pickForm(listOf(U, N, N), focused = 1)
        assertEquals(0, pick.username)
        assertEquals(null, pick.password)
        assertEquals(1, pick.newPassword)
    }

    @Test
    fun aChangePasswordFormKeepsBoth() {
        val pick = pickForm(listOf(P, N, N), focused = 2)
        assertEquals(0, pick.password)
        assertEquals(2, pick.newPassword) // the tapped one counts first
    }

    @Test
    fun aCodePageIsFoundAndAFormWithNothingIsNot() {
        val pick = pickForm(listOf(C), focused = 0)
        assertEquals(0, pick.otp)
        assertEquals(true, pick.interesting)
        assertEquals(false, pickForm(listOf(O, O), focused = 0).interesting)
        assertEquals(false, pickForm(emptyList(), focused = -1).interesting)
    }
}
