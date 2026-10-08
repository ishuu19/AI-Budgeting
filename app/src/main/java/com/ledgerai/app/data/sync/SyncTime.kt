package com.ledgerai.app.data.sync

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

internal object SyncTime {

    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

    fun millisToIso(ms: Long): String =
        Instant.ofEpochMilli(ms).atOffset(ZoneOffset.UTC).toString()

    fun isoToMillis(value: String?): Long {
        if (value.isNullOrBlank()) return 0L
        return try {
            Instant.parse(value).toEpochMilli()
        } catch (_: Exception) {
            try {
                OffsetDateTime.parse(value).toInstant().toEpochMilli()
            } catch (_: Exception) {
                0L
            }
        }
    }

    fun dateToString(date: LocalDate): String = date.toString()

    fun stringToDate(value: String?): LocalDate =
        value?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.EPOCH

    fun dateTimeToIso(value: LocalDateTime): String =
        value.atOffset(ZoneOffset.UTC).toString()

    fun isoToDateTime(value: String?): LocalDateTime? {
        if (value.isNullOrBlank()) return null
        return try {
            Instant.parse(value).atOffset(ZoneOffset.UTC).toLocalDateTime()
        } catch (_: Exception) {
            try {
                OffsetDateTime.parse(value).toLocalDateTime()
            } catch (_: Exception) {
                runCatching { LocalDateTime.parse(value) }.getOrNull()
            }
        }
    }

    fun timeToString(time: LocalTime): String = time.format(timeFormatter)

    fun stringToTime(value: String?): LocalTime =
        value?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: LocalTime.MIDNIGHT

    fun postgrestGtFilter(sinceMs: Long): String =
        "gt.${millisToIso(sinceMs.coerceAtLeast(0L))}"

    /**
     * Last-write-wins: remote wins when [remoteUpdatedMs] >= [localUpdatedMs]
     * (ties go to remote, matching SyncRepository pull).
     */
    fun remoteWins(remoteUpdatedMs: Long, localUpdatedMs: Long): Boolean =
        remoteUpdatedMs >= localUpdatedMs

    /** LWW compare using a remote ISO timestamp against local epoch millis. */
    fun remoteWins(remoteUpdatedAtIso: String?, localUpdatedMs: Long): Boolean =
        remoteWins(isoToMillis(remoteUpdatedAtIso), localUpdatedMs)
}
