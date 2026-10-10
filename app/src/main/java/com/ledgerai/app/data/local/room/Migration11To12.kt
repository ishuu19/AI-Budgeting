package com.ledgerai.app.data.local.room

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Version 11 to 12: household tables only. Assistant and other feature tables stay as they are. */
object Migration11To12Sql {
    val statements: List<String> = listOf(
        """
        CREATE TABLE IF NOT EXISTS `households` (
            `id` TEXT NOT NULL,
            `name` TEXT NOT NULL,
            `createdBy` TEXT NOT NULL,
            `updatedAt` INTEGER NOT NULL,
            `deletedAt` INTEGER,
            PRIMARY KEY(`id`)
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS `household_members` (
            `householdId` TEXT NOT NULL,
            `userId` TEXT NOT NULL,
            `role` TEXT NOT NULL,
            `joinedAt` TEXT NOT NULL,
            `updatedAt` INTEGER NOT NULL,
            `deletedAt` INTEGER,
            PRIMARY KEY(`householdId`, `userId`)
        )
        """,
    ).map { it.trimIndent() }
}

val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        Migration11To12Sql.statements.forEach { db.execSQL(it) }
    }
}
