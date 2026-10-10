package com.ledgerai.app.data.local.room

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Version 13 to 14: receipt tables only. Assistant, household, and inventory tables stay as they are. */
object Migration13To14Sql {
    val statements: List<String> = listOf(
        """
        CREATE TABLE IF NOT EXISTS `receipts` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `userId` TEXT,
            `householdId` TEXT,
            `merchant` TEXT,
            `merchantConfidence` TEXT,
            `purchasedOn` TEXT,
            `purchasedOnConfidence` TEXT,
            `total` REAL,
            `totalConfidence` TEXT,
            `currency` TEXT,
            `paidBy` TEXT,
            `documentId` TEXT,
            `transactionId` TEXT,
            `locallyConfirmed` INTEGER NOT NULL,
            `updatedAt` INTEGER NOT NULL,
            `deletedAt` INTEGER
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS `receipt_lines` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `receiptId` INTEGER NOT NULL,
            `rawText` TEXT NOT NULL,
            `itemId` TEXT,
            `qty` REAL,
            `unitPrice` REAL,
            `lineTotal` REAL,
            `confidence` TEXT NOT NULL,
            `updatedAt` INTEGER NOT NULL,
            `deletedAt` INTEGER,
            FOREIGN KEY(`receiptId`) REFERENCES `receipts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
        )
        """,
        """
        CREATE INDEX IF NOT EXISTS `index_receipt_lines_receiptId` ON `receipt_lines` (`receiptId`)
        """,
    ).map { it.trimIndent() }
}

val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        Migration13To14Sql.statements.forEach { db.execSQL(it) }
    }
}
