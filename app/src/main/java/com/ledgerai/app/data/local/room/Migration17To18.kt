package com.ledgerai.app.data.local.room

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Version 17 to 18: media_assets only. Earlier tables stay as they are. */
object Migration17To18Sql {
    val statements: List<String> = listOf(
        """
        CREATE TABLE IF NOT EXISTS `media_assets` (
            `id` TEXT NOT NULL,
            `userId` TEXT NOT NULL,
            `localPath` TEXT NOT NULL,
            `remotePath` TEXT,
            `kind` TEXT NOT NULL,
            `title` TEXT NOT NULL,
            `description` TEXT NOT NULL,
            `note` TEXT NOT NULL,
            `uploadState` TEXT NOT NULL,
            `analysisState` TEXT NOT NULL,
            `linkedType` TEXT,
            `linkedId` TEXT,
            `payload` TEXT,
            `createdAt` INTEGER NOT NULL,
            `updatedAt` INTEGER NOT NULL,
            `deletedAt` INTEGER,
            PRIMARY KEY(`id`)
        )
        """,
        "CREATE INDEX IF NOT EXISTS `index_media_assets_userId` ON `media_assets` (`userId`)",
        "CREATE INDEX IF NOT EXISTS `index_media_assets_uploadState` ON `media_assets` (`uploadState`)",
        "CREATE INDEX IF NOT EXISTS `index_media_assets_analysisState` ON `media_assets` (`analysisState`)",
    ).map { it.trimIndent() }
}

val MIGRATION_17_18 = object : Migration(17, 18) {
    override fun migrate(db: SupportSQLiteDatabase) {
        Migration17To18Sql.statements.forEach { db.execSQL(it) }
    }
}
