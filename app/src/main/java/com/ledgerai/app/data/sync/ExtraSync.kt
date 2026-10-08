package com.ledgerai.app.data.sync

/**
 * One extra Room table (or a small group) that [SyncRepository.syncAll] uploads
 * after the core money and calendar tables. Implementations are bound with @IntoSet.
 */
interface ExtraSync {
    suspend fun sync(bearer: String, apiKey: String, userId: String, sinceMs: Long, filter: String)
}
