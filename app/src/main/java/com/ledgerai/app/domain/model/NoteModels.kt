package com.ledgerai.app.domain.model

import java.time.LocalDateTime

data class NoteItem(
    val id: Long = 0,
    val remoteId: String? = null,
    val title: String,
    val body: String = "",
    val tags: List<String> = emptyList(),
    val updatedAt: LocalDateTime = LocalDateTime.now(),
    val createdAt: LocalDateTime = LocalDateTime.now()
)
