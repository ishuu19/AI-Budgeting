package com.ledgerai.app.domain.util

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object PlaceLinks {
    fun googleMapsSearchUrl(query: String): String {
        val q = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8.toString())
        return "https://www.google.com/maps/search/?api=1&query=$q"
    }

    fun googleMapsUrl(lat: Double, lon: Double, label: String = ""): String {
        val q = if (label.isNotBlank()) {
            URLEncoder.encode(label, StandardCharsets.UTF_8.toString())
        } else {
            "$lat,$lon"
        }
        return "https://www.google.com/maps/search/?api=1&query=$q"
    }

    fun googleMapsUrlFromCoords(lat: Double, lon: Double): String =
        "https://www.google.com/maps?q=$lat,$lon"

    /** Append a maps URL to a links field (one per line). */
    fun mergeMapsLink(existingLinks: String, mapsUrl: String): String {
        if (mapsUrl.isBlank()) return existingLinks.trim()
        if (existingLinks.lines().any { it.trim() == mapsUrl }) return existingLinks.trim()
        return listOf(existingLinks.trim(), mapsUrl.trim())
            .filter { it.isNotEmpty() }
            .joinToString("\n")
    }
}
