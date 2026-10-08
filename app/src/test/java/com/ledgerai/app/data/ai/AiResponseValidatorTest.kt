package com.ledgerai.app.data.ai

import com.google.gson.Gson
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiResponseValidatorTest {

    private val validator = AiResponseValidator(Gson())

    @Test
    fun validatesTransactionJson() {
        val raw = """{"amount":12.5,"category":"FOOD","type":"EXPENSE","merchant":"Cafe","note":"coffee"}"""
        val result = validator.validate(AiResponseType.TRANSACTION, raw)
        assertNotNull(result)
        assertTrue(result is ValidatedAiResponse.Transaction)
    }

    @Test
    fun rejectsMissingCategory() {
        val raw = """{"amount":12.5,"type":"EXPENSE"}"""
        val result = validator.validate(AiResponseType.TRANSACTION, raw)
        assertNull(result)
    }

    @Test
    fun rejectsNonJsonForTransaction() {
        val result = validator.validate(AiResponseType.TRANSACTION, "not json at all")
        assertNull(result)
    }

    @Test
    fun validatesChatObject() {
        val result = validator.validate(AiResponseType.CHAT, """{"reply":"Hello"}""")
        assertNotNull(result)
        assertTrue(result is ValidatedAiResponse.Chat)
    }
}
