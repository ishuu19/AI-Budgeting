package com.ledgerai.app.data.local.room

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Version 12 to 13: inventory and shopping tables only. Assistant and household tables stay as they are. */
object Migration12To13Sql {
    val statements: List<String> = listOf(
        """
        CREATE TABLE IF NOT EXISTS `items` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `ownerUserId` TEXT,
            `householdId` TEXT,
            `name` TEXT NOT NULL,
            `category` TEXT NOT NULL,
            `quantity` REAL,
            `unit` TEXT NOT NULL,
            `location` TEXT NOT NULL,
            `expiresOn` TEXT,
            `kind` TEXT NOT NULL,
            `sourceType` TEXT NOT NULL,
            `sourceId` TEXT,
            `confidence` TEXT NOT NULL,
            `updatedAt` INTEGER NOT NULL,
            `deletedAt` INTEGER
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS `shopping_lists` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `householdId` TEXT,
            `userId` TEXT,
            `name` TEXT NOT NULL,
            `updatedAt` INTEGER NOT NULL,
            `deletedAt` INTEGER
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS `shopping_items` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `listId` INTEGER NOT NULL,
            `itemName` TEXT NOT NULL,
            `qty` REAL,
            `addedBy` TEXT,
            `boughtBy` TEXT,
            `boughtAt` TEXT,
            `status` TEXT NOT NULL,
            `updatedAt` INTEGER NOT NULL,
            `deletedAt` INTEGER
        )
        """,
        """
        CREATE INDEX IF NOT EXISTS `index_shopping_items_listId` ON `shopping_items` (`listId`)
        """,
    ).map { it.trimIndent() }
}

val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        Migration12To13Sql.statements.forEach { db.execSQL(it) }
    }
}
