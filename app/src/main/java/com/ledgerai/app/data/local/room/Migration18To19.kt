package com.ledgerai.app.data.local.room

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Version 18 to 19: a display name on each household member, so people are shown by name, not by id. */
val MIGRATION_18_19 = object : Migration(18, 19) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `household_members` ADD COLUMN `displayName` TEXT NOT NULL DEFAULT ''")
    }
}
