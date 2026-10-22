package com.ledgerai.app.data.sync

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/**
 * PostgREST refuses a bulk insert whose rows do not all have the same keys ("All object keys must
 * match", HTTP 400). The sync DTOs leave out empty fields, so one empty date or note in a batch was
 * enough to stop that table, and with it the whole sync, from ever uploading.
 *
 * This splits such a batch into one request per set of keys. Rows keep their own fields, so an
 * omitted field still means "use the server default". The caller gets the first failing response, or
 * the last successful one.
 */
class UniformKeysInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val body = request.body
        val isBulkPost = request.method == "POST" && body != null &&
            body.contentType()?.subtype == "json" && request.url.encodedPath.contains("/rest/v1/")
        if (!isBulkPost) return chain.proceed(request)

        val array = try {
            val buffer = okio.Buffer().also { body!!.writeTo(it) }
            JsonParser.parseString(buffer.readUtf8()).takeIf { it.isJsonArray }?.asJsonArray
        } catch (e: Exception) {
            null
        } ?: return chain.proceed(request)

        val groups = groupByKeys(array)
        if (groups.size <= 1) return chain.proceed(request)

        val mediaType = body!!.contentType() ?: "application/json".toMediaType()
        var last: Response? = null
        for (rows in groups) {
            last?.close()
            val part = JsonArray().also { a -> rows.forEach(a::add) }
            val response = chain.proceed(
                request.newBuilder().post(part.toString().toRequestBody(mediaType)).build()
            )
            if (!response.isSuccessful) return response
            last = response
        }
        return last!!
    }

    companion object {
        /** Rows grouped by their exact key set, in first-seen order. Anything that is not an object stays whole. */
        fun groupByKeys(array: JsonArray): List<List<JsonObject>> {
            val groups = LinkedHashMap<Set<String>, MutableList<JsonObject>>()
            for (element in array) {
                val obj = element.takeIf { it.isJsonObject }?.asJsonObject ?: return listOf(emptyList())
                groups.getOrPut(obj.keySet().toSet()) { ArrayList() } += obj
            }
            return groups.values.toList()
        }
    }
}
