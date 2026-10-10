package com.ledgerai.app.data.local.room

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Version 15 to 16: subscriptions only. Earlier tables stay as they are. */
object Migration15To16Sql {
    val statements: List<String> = listOf(
        """
        CREATE TABLE IF NOT EXISTS `subscriptions` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `userId` TEXT,
            `merchant` TEXT NOT NULL,
            `amount` REAL,
            `period` TEXT NOT NULL,
            `nextRenewalOn` TEXT NOT NULL,
            `status` TEXT NOT NULL,
            `sourceId` TEXT,
            `updatedAt` INTEGER NOT NULL,
            `deletedAt` INTEGER,
            `remoteId` TEXT
        )
        """,
        """
        CREATE INDEX IF NOT EXISTS `index_subscriptions_userId` ON `subscriptions` (`userId`)
        """,
        """
        CREATE INDEX IF NOT EXISTS `index_subscriptions_updatedAt` ON `subscriptions` (`updatedAt`)
        """,
    ).map { it.trimIndent() }
}

val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) {
        Migration15To16Sql.statements.forEach { db.execSQL(it) }
    }
}
