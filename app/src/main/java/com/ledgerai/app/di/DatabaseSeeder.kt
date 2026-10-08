package com.ledgerai.app.di

import com.ledgerai.app.data.ai.InsightStore
import com.ledgerai.app.data.local.room.LedgerDatabase
import com.ledgerai.app.data.preferences.UserPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * No demo/mock rows — user data only.
 * [onApplicationStart] runs a one-time wipe so installs that previously received seed data start empty.
 */
@Singleton
class DatabaseSeeder @Inject constructor(
    private val database: LedgerDatabase,
    private val userPreferences: UserPreferences,
    private val insightStore: InsightStore,
) {

    fun onApplicationStart() {
        CoroutineScope(Dispatchers.IO).launch {
            userPreferences.runFreshLocalResetIfNeeded {
                database.clearAllTables()
                insightStore.clear()
            }
        }
    }
}
