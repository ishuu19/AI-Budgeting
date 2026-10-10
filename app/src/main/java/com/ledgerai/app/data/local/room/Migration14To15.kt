package com.ledgerai.app.data.local.room

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Version 14 to 15: people, interactions, commitments, and memories only. */
object Migration14To15Sql {
    val statements: List<String> = listOf(
        """
        CREATE TABLE IF NOT EXISTS `people` (
            `id` TEXT NOT NULL,
            `userId` TEXT NOT NULL,
            `name` TEXT NOT NULL,
            `org` TEXT,
            `role` TEXT,
            `notes` TEXT NOT NULL,
            `visibility` TEXT NOT NULL,
            `updatedAt` INTEGER NOT NULL,
            `deletedAt` INTEGER,
            PRIMARY KEY(`id`)
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS `interactions` (
            `id` TEXT NOT NULL,
            `userId` TEXT NOT NULL,
            `personId` TEXT NOT NULL,
            `occurredOn` TEXT NOT NULL,
            `where` TEXT,
            `summary` TEXT NOT NULL,
            `sourceId` TEXT,
            `updatedAt` INTEGER NOT NULL,
            `deletedAt` INTEGER,
            PRIMARY KEY(`id`),
            FOREIGN KEY(`personId`) REFERENCES `people`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION
        )
        """,
        """
        CREATE INDEX IF NOT EXISTS `index_interactions_personId` ON `interactions` (`personId`)
        """,
        """
        CREATE TABLE IF NOT EXISTS `commitments` (
            `id` TEXT NOT NULL,
            `userId` TEXT NOT NULL,
            `personId` TEXT,
            `eventId` TEXT,
            `text` TEXT NOT NULL,
            `dueOn` TEXT,
            `status` TEXT NOT NULL,
            `updatedAt` INTEGER NOT NULL,
            `deletedAt` INTEGER,
            PRIMARY KEY(`id`),
            FOREIGN KEY(`personId`) REFERENCES `people`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION
        )
        """,
        """
        CREATE INDEX IF NOT EXISTS `index_commitments_personId` ON `commitments` (`personId`)
        """,
        """
        CREATE TABLE IF NOT EXISTS `memories` (
            `id` TEXT NOT NULL,
            `userId` TEXT NOT NULL,
            `personId` TEXT NOT NULL,
            `text` TEXT NOT NULL,
            `kind` TEXT NOT NULL,
            `status` TEXT NOT NULL,
            `sourceType` TEXT NOT NULL,
            `sourceId` TEXT,
            `visibility` TEXT NOT NULL,
            `updatedAt` INTEGER NOT NULL,
            `deletedAt` INTEGER,
            PRIMARY KEY(`id`),
            FOREIGN KEY(`personId`) REFERENCES `people`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION
        )
        """,
        """
        CREATE INDEX IF NOT EXISTS `index_memories_personId` ON `memories` (`personId`)
        """,
    ).map { it.trimIndent() }
}

val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        Migration14To15Sql.statements.forEach { db.execSQL(it) }
    }
}
