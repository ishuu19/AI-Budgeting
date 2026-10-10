package com.ledgerai.app.data.ai

import com.google.gson.Gson
import com.google.gson.JsonNull
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FastProxyPayloadTest {

    private val gson = Gson()
    private val voice = AiResponseValidator(gson)

    @Test
    fun requestIsFastCompletionWithoutProviderKey() {
        val request = fastCompletionRequest(system = "extract json", user = "coffee 4")

        assertEquals("fast_completion", request.type)
        assertEquals("extract json", request.system)
        assertEquals("coffee 4", request.user)

        val body = JsonParser.parseString(gson.toJson(request)).asJsonObject
        assertEquals("fast_completion", body.get("type").asString)
        assertEquals("extract json", body.get("system").asString)
        assertEquals("coffee 4", body.get("user").asString)
        listOf("provider-key", "provider_key", "providerKey", "api_key", "apiKey").forEach { key ->
            assertFalse(body.has(key))
        }
        assertFalse(gson.toJson(request).contains("provider"))
    }

    @Test
    fun objectDataRoundTripsNullFieldsForVoiceMapper() {
        val payload = """{"items":[{"intent":"TRANSACTION","amount":null,"merchant":null,"start_at":null}]}"""
        val response = AiProxyResponse(
            type = "fast_completion",
            data = JsonParser.parseString(payload),
        )

        val json = checkNotNull(fastCompletionJson(response))
        assertTrue(json.isNotBlank())
        val item = JsonParser.parseString(json).asJsonObject.getAsJsonArray("items")[0].asJsonObject
        assertEquals("TRANSACTION", item.get("intent").asString)
        assertTrue(item.get("amount").isJsonNull)
        assertTrue(item.get("merchant").isJsonNull)
        assertTrue(item.get("start_at").isJsonNull)
        assertFalse(Regex(""""amount"\s*:\s*0""").containsMatchIn(json))
        assertFalse(json.contains("\"merchant\":\"\""))

        val parsed = voice.validateVoiceItems(json)
        assertEquals(1, parsed.size)
        assertNull(parsed[0].amount)
        assertNull(parsed[0].merchant)
        assertNull(parsed[0].startAt)
    }

    @Test
    fun errorOrMissingDataReturnsNull() {
        val items = JsonParser.parseString(
            """{"items":[{"intent":"TRANSACTION","amount":4.5}]}"""
        )
        assertNull(fastCompletionJson(AiProxyResponse(data = items, error = "model failed")))
        assertNull(fastCompletionJson(AiProxyResponse(data = null, error = null)))
        assertNull(fastCompletionJson(AiProxyResponse(data = JsonNull.INSTANCE, error = null)))
    }
}
