package com.ledgerai.app.data.local.room

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Version 16 to 17: wardrobe_items and outfits only. Earlier tables stay as they are. */
object Migration16To17Sql {
    val statements: List<String> = listOf(
        """
        CREATE TABLE IF NOT EXISTS `wardrobe_items` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `userId` TEXT NOT NULL,
            `name` TEXT NOT NULL,
            `type` TEXT NOT NULL,
            `colors` TEXT NOT NULL,
            `seasons` TEXT NOT NULL,
            `photoPath` TEXT,
            `laundryStatus` TEXT NOT NULL,
            `updatedAt` INTEGER NOT NULL,
            `deletedAt` INTEGER
        )
        """,
        """
        CREATE INDEX IF NOT EXISTS `index_wardrobe_items_userId` ON `wardrobe_items` (`userId`)
        """,
        """
        CREATE TABLE IF NOT EXISTS `outfits` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `userId` TEXT NOT NULL,
            `occasion` TEXT NOT NULL,
            `itemIds` TEXT NOT NULL,
            `wornOn` TEXT,
            `updatedAt` INTEGER NOT NULL,
            `deletedAt` INTEGER
        )
        """,
        """
        CREATE INDEX IF NOT EXISTS `index_outfits_userId` ON `outfits` (`userId`)
        """,
    ).map { it.trimIndent() }
}

val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(db: SupportSQLiteDatabase) {
        Migration16To17Sql.statements.forEach { db.execSQL(it) }
    }
}
