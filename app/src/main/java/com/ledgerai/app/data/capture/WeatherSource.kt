package com.ledgerai.app.data.capture

import com.google.gson.JsonParser
import com.ledgerai.app.data.local.room.LocationPointDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Current weather for outfit ideas, from Open-Meteo (no key, no account). It uses the last location
 * the app already stored, and only if that point is under a day old. With no recent location,
 * no network or any error it returns null, and the outfit prompt simply omits weather.
 */
@Singleton
class WeatherSource @Inject constructor(
    private val points: LocationPointDao,
) {
    private val client = OkHttpClient.Builder()
        .callTimeout(6, TimeUnit.SECONDS)
        .build()

    suspend fun describeNow(): String? = withContext(Dispatchers.IO) {
        val p = points.latest() ?: return@withContext null
        if (Duration.between(p.ts, LocalDateTime.now()).abs() > Duration.ofHours(24)) return@withContext null
        val url = "https://api.open-meteo.com/v1/forecast?latitude=${p.lat}&longitude=${p.lng}" +
            "&current=temperature_2m,precipitation,weather_code"
        runCatching {
            client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val cur = JsonParser.parseString(resp.body?.string().orEmpty()).asJsonObject.getAsJsonObject("current")
                val temp = cur.get("temperature_2m").asDouble
                val rain = cur.get("precipitation").asDouble
                "%.0f°C%s".format(temp, if (rain > 0.0) ", raining" else "")
            }
        }.getOrNull()
    }
}
