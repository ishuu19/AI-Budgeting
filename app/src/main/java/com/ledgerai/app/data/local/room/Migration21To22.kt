package com.ledgerai.app.data.local.room

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Version 21 to 22: the rebuild's records.
 * New tables: folders, birthdays, trips, achievements, wishes, passwords, app_usage_days, reminders.
 * New columns: notes.folderId, calendar_events.personId / jobId / status, media_assets.mime / sizeBytes / locked.
 * Existing rows are not touched. A note with no folder reads as the Notes folder.
 */
object Migration21To22Sql {
    val statements: List<String> = listOf(
        "ALTER TABLE `notes` ADD COLUMN `folderId` TEXT",
        "ALTER TABLE `calendar_events` ADD COLUMN `personId` TEXT",
        "ALTER TABLE `calendar_events` ADD COLUMN `jobId` INTEGER",
        "ALTER TABLE `calendar_events` ADD COLUMN `status` TEXT",
        "ALTER TABLE `media_assets` ADD COLUMN `mime` TEXT",
        "ALTER TABLE `media_assets` ADD COLUMN `sizeBytes` INTEGER",
        "ALTER TABLE `media_assets` ADD COLUMN `locked` INTEGER NOT NULL DEFAULT 0",
        """
        CREATE TABLE IF NOT EXISTS `folders` (
            `id` TEXT NOT NULL,
            `userId` TEXT,
            `name` TEXT NOT NULL,
            `parentId` TEXT,
            `pinned` INTEGER NOT NULL,
            `sortOrder` INTEGER NOT NULL,
            `locked` INTEGER NOT NULL,
            `deviceOnly` INTEGER NOT NULL,
            `systemKey` TEXT,
            `updatedAt` INTEGER NOT NULL,
            `deletedAt` INTEGER,
            PRIMARY KEY(`id`)
        )
        """,
        "CREATE INDEX IF NOT EXISTS `index_folders_userId` ON `folders` (`userId`)",
        "CREATE INDEX IF NOT EXISTS `index_folders_parentId` ON `folders` (`parentId`)",
        "CREATE INDEX IF NOT EXISTS `index_folders_updatedAt` ON `folders` (`updatedAt`)",
        """
        CREATE TABLE IF NOT EXISTS `birthdays` (
            `id` TEXT NOT NULL,
            `userId` TEXT,
            `personId` TEXT NOT NULL,
            `month` INTEGER NOT NULL,
            `day` INTEGER NOT NULL,
            `birthYear` INTEGER,
            `celebrateMinute` INTEGER NOT NULL,
            `note` TEXT NOT NULL,
            `r1mo` INTEGER NOT NULL,
            `r2w` INTEGER NOT NULL,
            `r1w` INTEGER NOT NULL,
            `r3d` INTEGER NOT NULL,
            `r1d` INTEGER NOT NULL,
            `r5h` INTEGER NOT NULL,
            `r30m` INTEGER NOT NULL,
            `r10m` INTEGER NOT NULL,
            `rAt` INTEGER NOT NULL,
            `updatedAt` INTEGER NOT NULL,
            `deletedAt` INTEGER,
            PRIMARY KEY(`id`)
        )
        """,
        "CREATE INDEX IF NOT EXISTS `index_birthdays_userId` ON `birthdays` (`userId`)",
        "CREATE INDEX IF NOT EXISTS `index_birthdays_personId` ON `birthdays` (`personId`)",
        "CREATE INDEX IF NOT EXISTS `index_birthdays_updatedAt` ON `birthdays` (`updatedAt`)",
        """
        CREATE TABLE IF NOT EXISTS `trips` (
            `id` TEXT NOT NULL,
            `userId` TEXT,
            `name` TEXT NOT NULL,
            `place` TEXT NOT NULL,
            `startDate` TEXT NOT NULL,
            `endDate` TEXT NOT NULL,
            `notes` TEXT NOT NULL,
            `r1w` INTEGER NOT NULL,
            `r1d` INTEGER NOT NULL,
            `visibility` TEXT NOT NULL,
            `updatedAt` INTEGER NOT NULL,
            `deletedAt` INTEGER,
            PRIMARY KEY(`id`)
        )
        """,
        "CREATE INDEX IF NOT EXISTS `index_trips_userId` ON `trips` (`userId`)",
        "CREATE INDEX IF NOT EXISTS `index_trips_startDate` ON `trips` (`startDate`)",
        "CREATE INDEX IF NOT EXISTS `index_trips_updatedAt` ON `trips` (`updatedAt`)",
        """
        CREATE TABLE IF NOT EXISTS `achievements` (
            `id` TEXT NOT NULL,
            `userId` TEXT,
            `title` TEXT NOT NULL,
            `happenedOn` TEXT NOT NULL,
            `note` TEXT NOT NULL,
            `sourceTaskId` INTEGER,
            `updatedAt` INTEGER NOT NULL,
            `deletedAt` INTEGER,
            PRIMARY KEY(`id`)
        )
        """,
        "CREATE INDEX IF NOT EXISTS `index_achievements_userId` ON `achievements` (`userId`)",
        "CREATE INDEX IF NOT EXISTS `index_achievements_happenedOn` ON `achievements` (`happenedOn`)",
        "CREATE INDEX IF NOT EXISTS `index_achievements_updatedAt` ON `achievements` (`updatedAt`)",
        """
        CREATE TABLE IF NOT EXISTS `wishes` (
            `id` TEXT NOT NULL,
            `userId` TEXT,
            `name` TEXT NOT NULL,
            `amount` REAL,
            `priority` INTEGER NOT NULL,
            `wantedBy` TEXT,
            `link` TEXT,
            `note` TEXT NOT NULL,
            `status` TEXT NOT NULL,
            `goalId` TEXT,
            `transactionId` TEXT,
            `visibility` TEXT NOT NULL,
            `createdAt` INTEGER NOT NULL,
            `updatedAt` INTEGER NOT NULL,
            `deletedAt` INTEGER,
            PRIMARY KEY(`id`)
        )
        """,
        "CREATE INDEX IF NOT EXISTS `index_wishes_userId` ON `wishes` (`userId`)",
        "CREATE INDEX IF NOT EXISTS `index_wishes_status` ON `wishes` (`status`)",
        "CREATE INDEX IF NOT EXISTS `index_wishes_updatedAt` ON `wishes` (`updatedAt`)",
        """
        CREATE TABLE IF NOT EXISTS `passwords` (
            `id` TEXT NOT NULL,
            `title` TEXT NOT NULL,
            `site` TEXT,
            `usernameEnc` TEXT NOT NULL,
            `secretEnc` TEXT NOT NULL,
            `noteEnc` TEXT NOT NULL,
            `lastChanged` INTEGER NOT NULL,
            `changeEveryDays` INTEGER,
            `createdAt` INTEGER NOT NULL,
            `updatedAt` INTEGER NOT NULL,
            PRIMARY KEY(`id`)
        )
        """,
        "CREATE INDEX IF NOT EXISTS `index_passwords_title` ON `passwords` (`title`)",
        """
        CREATE TABLE IF NOT EXISTS `app_usage_days` (
            `day` TEXT NOT NULL,
            `packageName` TEXT NOT NULL,
            `label` TEXT NOT NULL,
            `foregroundMs` INTEGER NOT NULL,
            `launches` INTEGER NOT NULL,
            PRIMARY KEY(`day`, `packageName`)
        )
        """,
        "CREATE INDEX IF NOT EXISTS `index_app_usage_days_day` ON `app_usage_days` (`day`)",
        """
        CREATE TABLE IF NOT EXISTS `reminders` (
            `id` TEXT NOT NULL,
            `sourceType` TEXT NOT NULL,
            `sourceId` TEXT NOT NULL,
            `rung` TEXT NOT NULL,
            `fireAt` INTEGER NOT NULL,
            `state` TEXT NOT NULL,
            `title` TEXT NOT NULL,
            `body` TEXT NOT NULL,
            PRIMARY KEY(`id`)
        )
        """,
        "CREATE INDEX IF NOT EXISTS `index_reminders_fireAt` ON `reminders` (`fireAt`)",
        "CREATE INDEX IF NOT EXISTS `index_reminders_sourceType_sourceId` ON `reminders` (`sourceType`, `sourceId`)",
    ).map { it.trimIndent() }
}

val MIGRATION_21_22 = object : Migration(21, 22) {
    override fun migrate(db: SupportSQLiteDatabase) {
        Migration21To22Sql.statements.forEach { db.execSQL(it) }
    }
}
