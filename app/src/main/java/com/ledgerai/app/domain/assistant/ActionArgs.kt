package com.ledgerai.app.domain.assistant

import java.time.LocalDate
import java.time.LocalTime

/**
 * Readers for model-supplied args. The plan parser turns every JSON number into a Double, so an id
 * sent as `5` arrives as `5.0`; these accept either form and return null for anything unusable.
 */
fun Map<String, Any?>.argStr(key: String): String? = when (val v = this[key]) {
    is String -> v.trim().ifEmpty { null }
    is Number -> v.toString()
    else -> null
}

fun Map<String, Any?>.argNum(key: String): Double? = when (val v = this[key]) {
    is Number -> v.toDouble()
    is String -> v.trim().replace(",", "").toDoubleOrNull()
    else -> null
}

fun Map<String, Any?>.argLong(key: String): Long? {
    val n = argNum(key) ?: return null
    return if (n == Math.rint(n) && n >= 0) n.toLong() else null
}

fun Map<String, Any?>.argInt(key: String): Int? = argLong(key)?.takeIf { it <= Int.MAX_VALUE }?.toInt()

fun Map<String, Any?>.argDate(key: String): LocalDate? = argStr(key)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

fun Map<String, Any?>.argTime(key: String): LocalTime? = argStr(key)?.let { runCatching { LocalTime.parse(it) }.getOrNull() }

fun Map<String, Any?>.argBool(key: String): Boolean? = when (val v = this[key]) {
    is Boolean -> v
    is String -> when (v.trim().lowercase()) { "true", "yes" -> true; "false", "no" -> false; else -> null }
    else -> null
}

/** A list arg. The parser hands nested JSON back as text, so a JSON array string is read too. */
fun Map<String, Any?>.argStrings(key: String): List<String> = when (val v = this[key]) {
    is List<*> -> v.mapNotNull { (it as? String)?.trim()?.ifEmpty { null } }
    is String -> runCatching {
        com.google.gson.JsonParser.parseString(v).takeIf { it.isJsonArray }?.asJsonArray
            ?.mapNotNull { e -> e.takeIf { it.isJsonPrimitive }?.asString?.trim()?.ifEmpty { null } }
    }.getOrNull() ?: v.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    else -> emptyList()
}

/** True when the model sent this key at all (even as null), so an edit can tell "clear it" from "leave it". */
fun Map<String, Any?>.has(key: String): Boolean = containsKey(key) && this[key] != null
