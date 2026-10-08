package com.ledgerai.app.data.places

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

data class PlaceSuggestion(
    val displayName: String,
    val latitude: Double,
    val longitude: Double,
    val mapsUrl: String
)

@Singleton
class PlaceSearchService @Inject constructor(
    @Named("aiOkHttp") private val okHttp: OkHttpClient
) {
    suspend fun search(query: String): List<PlaceSuggestion> = withContext(Dispatchers.IO) {
        if (query.trim().length < 2) return@withContext emptyList()
        val encoded = java.net.URLEncoder.encode(query.trim(), Charsets.UTF_8.name())
        val url =
            "https://nominatim.openstreetmap.org/search?q=$encoded&format=json&limit=6&addressdetails=0"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "LedgerAI/1.0 (school schedule app)")
            .get()
            .build()
        runCatching {
            okHttp.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val body = response.body?.string() ?: return@withContext emptyList()
                val arr = JSONArray(body)
                buildList {
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        val name = o.optString("display_name").takeIf { it.isNotBlank() } ?: continue
                        val lat = o.optString("lat").toDoubleOrNull() ?: continue
                        val lon = o.optString("lon").toDoubleOrNull() ?: continue
                        val short = name.split(",").take(2).joinToString(", ").trim().ifBlank { name }
                        add(
                            PlaceSuggestion(
                                displayName = short,
                                latitude = lat,
                                longitude = lon,
                                mapsUrl = com.ledgerai.app.domain.util.PlaceLinks.googleMapsUrlFromCoords(lat, lon)
                            )
                        )
                    }
                }
            }
        }.getOrElse { emptyList() }
    }
}
