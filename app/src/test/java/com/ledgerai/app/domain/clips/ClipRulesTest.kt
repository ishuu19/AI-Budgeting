package com.ledgerai.app.domain.clips

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipRulesTest {

    @Test
    fun cardNumberIsSensitive() {
        val text = "Pay with 4111 1111 1111 1111 today"
        assertTrue(ClipRules.hasCardNumber(text))
        assertEquals(ClipGate.ConfirmSensitive, ClipRules.gate(text, descriptionSensitive = false))
    }

    @Test
    fun otpSmsNeedsConfirm() {
        val text = "Your code is 482913"
        assertTrue(ClipRules.isSensitiveText(text))
        assertEquals(ClipGate.ConfirmSensitive, ClipRules.gate(text, descriptionSensitive = false))
    }

    @Test
    fun passwordManagerFlagIsNotSaved() {
        assertEquals(ClipGate.Password, ClipRules.gate("anything", descriptionSensitive = true))
    }

    @Test
    fun normalSentenceIsKept() {
        val text = "Shinjuku Gyoen opens 9:00, closed Mondays."
        assertEquals(ClipGate.Ok, ClipRules.gate(text, descriptionSensitive = false))
        assertFalse(ClipRules.isSensitiveText(text))
        assertTrue(ClipRules.firstUrl("See https://example.com/park")!!.startsWith("https://example.com"))
    }

    @Test
    fun privateKeyAndIbanAreSensitive() {
        assertTrue(ClipRules.isSensitiveText("-----BEGIN PRIVATE KEY-----\nMII"))
        assertTrue(ClipRules.isSensitiveText("IBAN GB82WEST12345698765432"))
    }
}
