package com.ledgerai.app.data.local.room

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Adds remoteId and userId so offline rows can be pushed to Supabase.
 * Tables that had no updatedAt get one, default 0, so the first sync still sends them
 * (listForSync includes remoteId IS NULL).
 */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val withUpdated = listOf(
            "study_plans",
            "plan_blocks",
            "habits",
            "habit_logs",
            "job_applications",
            "spend_speculations"
        )
        withUpdated.forEach { table ->
            db.execSQL("ALTER TABLE $table ADD COLUMN remoteId TEXT")
            db.execSQL("ALTER TABLE $table ADD COLUMN userId TEXT")
        }
        listOf(
            "activity_entries",
            "checkin_windows",
            "voice_history",
            "spend_guide_days",
            "leave_rules",
            "focus_sessions",
            "nudge_proposals"
        ).forEach { table ->
            db.execSQL("ALTER TABLE $table ADD COLUMN remoteId TEXT")
            db.execSQL("ALTER TABLE $table ADD COLUMN userId TEXT")
            db.execSQL("ALTER TABLE $table ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
        }
        listOf("activity_entries", "checkin_windows", "leave_rules", "nudge_proposals").forEach { table ->
            db.execSQL("ALTER TABLE $table ADD COLUMN deletedAt INTEGER")
        }
    }
}
