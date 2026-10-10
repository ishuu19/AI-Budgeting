package com.ledgerai.app.data.local.room

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Job applications gain a location and labeled dates besides the application date. */
val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE job_applications ADD COLUMN location TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE job_applications ADD COLUMN extraDates TEXT NOT NULL DEFAULT ''")
    }
}
