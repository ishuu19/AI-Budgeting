package com.ledgerai.app.data.local.room

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Version 20 to 21: notebook columns on clips, plus chat threads and messages. */
object Migration20To21Sql {
    val statements: List<String> = listOf(
        "ALTER TABLE `clips` ADD COLUMN `editedText` TEXT",
        "ALTER TABLE `clips` ADD COLUMN `pinned` INTEGER NOT NULL DEFAULT 0",
        "ALTER TABLE `clips` ADD COLUMN `pinOrder` INTEGER",
        "ALTER TABLE `clips` ADD COLUMN `prompt` TEXT",
        "ALTER TABLE `clips` ADD COLUMN `windowTitle` TEXT",
        "ALTER TABLE `clips` ADD COLUMN `threadId` TEXT",
        "ALTER TABLE `clips` ADD COLUMN `messageId` TEXT",
        """
        CREATE TABLE IF NOT EXISTS `chat_threads` (
            `id` TEXT NOT NULL,
            `remoteId` TEXT,
            `userId` TEXT,
            `title` TEXT NOT NULL,
            `origin` TEXT NOT NULL,
            `sourceApp` TEXT,
            `createdAt` INTEGER NOT NULL,
            `updatedAt` INTEGER NOT NULL,
            `deletedAt` INTEGER,
            PRIMARY KEY(`id`)
        )
        """,
        "CREATE INDEX IF NOT EXISTS `index_chat_threads_remoteId` ON `chat_threads` (`remoteId`)",
        "CREATE INDEX IF NOT EXISTS `index_chat_threads_updatedAt` ON `chat_threads` (`updatedAt`)",
        """
        CREATE TABLE IF NOT EXISTS `chat_messages` (
            `id` TEXT NOT NULL,
            `remoteId` TEXT,
            `userId` TEXT,
            `threadId` TEXT NOT NULL,
            `role` TEXT NOT NULL,
            `text` TEXT NOT NULL,
            `prompt` TEXT,
            `clipIds` TEXT,
            `state` TEXT NOT NULL,
            `actionsJson` TEXT,
            `createdAt` INTEGER NOT NULL,
            `updatedAt` INTEGER NOT NULL,
            `deletedAt` INTEGER,
            PRIMARY KEY(`id`)
        )
        """,
        "CREATE INDEX IF NOT EXISTS `index_chat_messages_threadId` ON `chat_messages` (`threadId`)",
        "CREATE INDEX IF NOT EXISTS `index_chat_messages_remoteId` ON `chat_messages` (`remoteId`)",
        "CREATE INDEX IF NOT EXISTS `index_chat_messages_updatedAt` ON `chat_messages` (`updatedAt`)",
    ).map { it.trimIndent() }
}

val MIGRATION_20_21 = object : Migration(20, 21) {
    override fun migrate(db: SupportSQLiteDatabase) {
        Migration20To21Sql.statements.forEach { db.execSQL(it) }
    }
}
