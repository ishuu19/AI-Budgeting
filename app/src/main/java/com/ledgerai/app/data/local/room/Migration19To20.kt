package com.ledgerai.app.data.local.room

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Version 19 to 20: clips only. Earlier tables stay as they are. */
object Migration19To20Sql {
    val statements: List<String> = listOf(
        """
        CREATE TABLE IF NOT EXISTS `clips` (
            `id` TEXT NOT NULL,
            `remoteId` TEXT,
            `userId` TEXT,
            `text` TEXT NOT NULL,
            `textHash` TEXT NOT NULL,
            `sourceKind` TEXT NOT NULL,
            `sourceApp` TEXT,
            `url` TEXT,
            `noteId` INTEGER,
            `status` TEXT NOT NULL,
            `sensitive` INTEGER NOT NULL,
            `aiState` TEXT NOT NULL,
            `aiKind` TEXT,
            `aiJson` TEXT,
            `filedType` TEXT,
            `filedId` TEXT,
            `capturedAt` INTEGER NOT NULL,
            `updatedAt` INTEGER NOT NULL,
            `deletedAt` INTEGER,
            PRIMARY KEY(`id`)
        )
        """,
        "CREATE INDEX IF NOT EXISTS `index_clips_noteId` ON `clips` (`noteId`)",
        "CREATE INDEX IF NOT EXISTS `index_clips_status` ON `clips` (`status`)",
        "CREATE INDEX IF NOT EXISTS `index_clips_remoteId` ON `clips` (`remoteId`)",
        "CREATE INDEX IF NOT EXISTS `index_clips_updatedAt` ON `clips` (`updatedAt`)",
        "CREATE INDEX IF NOT EXISTS `index_clips_textHash` ON `clips` (`textHash`)",
    ).map { it.trimIndent() }
}

val MIGRATION_19_20 = object : Migration(19, 20) {
    override fun migrate(db: SupportSQLiteDatabase) {
        Migration19To20Sql.statements.forEach { db.execSQL(it) }
    }
}
