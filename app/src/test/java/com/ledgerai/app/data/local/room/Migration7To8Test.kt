package com.ledgerai.app.data.local.room

import com.google.gson.JsonParser
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.sql.Connection
import java.sql.DriverManager

/**
 * Runs the real migration SQL ([Migration7To8Sql]) on a SQLite database that already holds version 7 data
 * (sqlite-jdbc, not a Room MigrationTestHelper run), and compares the new table with the schema Room
 * exported for version 8.
 */
class Migration7To8Test {

    private lateinit var db: Connection

    @Before
    fun setUp() {
        db = DriverManager.getConnection("jdbc:sqlite::memory:")
        // One existing table with a row stands in for the rest of the version 7 database.
        exec("CREATE TABLE notes (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, title TEXT NOT NULL)")
        exec("INSERT INTO notes (title) VALUES ('Keep me')")
        Migration7To8Sql.statements.forEach { exec(it) }
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun exec(sql: String) = db.createStatement().use { it.execute(sql) }

    private fun describe(conn: Connection, table: String): List<String> =
        conn.createStatement().use { st ->
            st.executeQuery("PRAGMA table_info($table)").use { rs ->
                buildList {
                    while (rs.next()) {
                        add("${rs.getString("name")}|${rs.getString("type")}|${rs.getInt("notnull")}|${rs.getInt("pk")}")
                    }
                }
            }
        }

    private fun indices(conn: Connection, table: String): List<String> =
        conn.createStatement().use { st ->
            st.executeQuery("PRAGMA index_list($table)").use { rs ->
                buildList {
                    while (rs.next()) add("${rs.getString("name")}|${rs.getInt("unique")}")
                }.sorted()
            }
        }

    private fun schema(version: Int) =
        File("schemas/com.ledgerai.app.data.local.room.LedgerDatabase/$version.json").also {
            assertTrue("exported schema missing: ${it.absolutePath}", it.exists())
        }

    @Test
    fun existingDataSurvives() {
        db.createStatement().use { st ->
            st.executeQuery("SELECT COUNT(*) AS n FROM notes").use { rs ->
                rs.next()
                assertEquals(1, rs.getInt("n"))
            }
        }
    }

    @Test
    fun voiceHistoryAcceptsRowsWithAndWithoutLink() {
        exec("INSERT INTO voice_history (transcript, resultKind, linkedItemId, resultSummary, createdAt) VALUES ('spent 5 on tea', 'Spend', 7, 'Tea', 1000)")
        exec("INSERT INTO voice_history (transcript, resultKind, linkedItemId, resultSummary, createdAt) VALUES ('mumble', 'Unsorted', NULL, '', 2000)")
        db.createStatement().use { st ->
            st.executeQuery("SELECT COUNT(*) AS n FROM voice_history WHERE deletedAt IS NULL").use { rs ->
                rs.next()
                assertEquals(2, rs.getInt("n"))
            }
        }
    }

    @Test
    fun voiceHistoryMatchesExportedRoomSchema() {
        val entities = JsonParser.parseString(schema(8).readText()).asJsonObject
            .getAsJsonObject("database").getAsJsonArray("entities")
        val fresh = DriverManager.getConnection("jdbc:sqlite::memory:")
        try {
            val entity = entities.map { it.asJsonObject }.single { it.get("tableName").asString == "voice_history" }
            fresh.createStatement().use {
                it.execute(entity.get("createSql").asString.replace("\${TABLE_NAME}", "voice_history"))
            }
            for (idx in entity.getAsJsonArray("indices")) {
                fresh.createStatement().use {
                    it.execute(idx.asJsonObject.get("createSql").asString.replace("\${TABLE_NAME}", "voice_history"))
                }
            }
            assertEquals(describe(fresh, "voice_history"), describe(db, "voice_history"))
            assertEquals(indices(fresh, "voice_history"), indices(db, "voice_history"))
        } finally {
            fresh.close()
        }
    }

    @Test
    fun versionEightOnlyAddsVoiceHistory() {
        fun tables(version: Int): Map<String, String> =
            JsonParser.parseString(schema(version).readText()).asJsonObject
                .getAsJsonObject("database").getAsJsonArray("entities")
                .map { it.asJsonObject }
                .associate { it.get("tableName").asString to it.get("createSql").asString }

        val v7 = tables(7)
        val v8 = tables(8)
        assertEquals(v7.keys + "voice_history", v8.keys)
        for ((name, sql) in v7) assertEquals("table $name changed", sql, v8[name])
    }
}
