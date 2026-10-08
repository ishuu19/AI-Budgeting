package com.ledgerai.app.data.ai

import com.google.gson.Gson
import org.junit.Assert.assertEquals
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

    @Test
    fun voiceItems_acceptsItemsArrayWithBillAndBudget() {
        val raw = """{"items":[{"intent":"BILL","name":"Gym","amount":40},{"intent":"BUDGET","category":"FOOD","amount":300}]}"""
        val items = validator.validateVoiceItems(raw)
        assertEquals(listOf("BILL", "BUDGET"), items.map { it.intent })
    }

    @Test
    fun voiceItems_dropsUnknownIntentsAndBadAmounts() {
        val raw = """[{"intent":"HACK","amount":5},{"intent":"TRANSACTION","amount":-3},{"intent":"debt","amount":20,"name":"Sam"}]"""
        val items = validator.validateVoiceItems(raw)
        assertEquals(2, items.size)
        assertNull(items[0].amount)
        assertEquals("DEBT", items[1].intent)
    }

    @Test
    fun voiceItems_singleObjectAndNonJson() {
        assertEquals(1, validator.validateVoiceItems("""{"intent":"NOTE","title":"Hi"}""").size)
        assertTrue(validator.validateVoiceItems("nothing here").isEmpty())
    }
}
