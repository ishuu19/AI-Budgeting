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
 * Runs [Migration10To11Sql] on a stand-in for the version 10 database and checks the new tables
 * against the schema Room exported for version 11.
 */
class Migration10To11Test {

    private lateinit var db: Connection

    @Before
    fun setUp() {
        db = DriverManager.getConnection("jdbc:sqlite::memory:")
        exec("CREATE TABLE notes (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, title TEXT NOT NULL)")
        exec("INSERT INTO notes (title) VALUES ('Keep me')")
        Migration10To11Sql.statements.forEach { exec(it) }
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
    fun actionSlotIsUniqueAndEventLinksToIt() {
        exec(
            """
            INSERT INTO assistant_actions (inputRef, actionIndex, planJson, risk, status, error, updatedAt)
            VALUES ('voice:1', 0, '{"type":"note.add"}', 'low', 'applied', '', 10)
            """.trimIndent()
        )
        exec(
            """
            INSERT INTO event_log (type, payload, sourceActionId, createdAt, updatedAt)
            VALUES ('note.add', '{"message":"Added note"}', 1, 10, 10)
            """.trimIndent()
        )
        var rejected = false
        try {
            exec(
                """
                INSERT INTO assistant_actions (inputRef, actionIndex, planJson, risk, status, error, updatedAt)
                VALUES ('voice:1', 0, '{}', 'low', 'applied', '', 11)
                """.trimIndent()
            )
        } catch (_: Exception) {
            rejected = true
        }
        assertTrue(rejected)
    }

    @Test
    fun newTablesMatchExportedRoomSchema() {
        val entities = JsonParser.parseString(schema(11).readText()).asJsonObject
            .getAsJsonObject("database").getAsJsonArray("entities")
        val fresh = DriverManager.getConnection("jdbc:sqlite::memory:")
        try {
            for (table in listOf("assistant_actions", "event_log")) {
                val entity = entities.map { it.asJsonObject }.single { it.get("tableName").asString == table }
                fresh.createStatement().use {
                    it.execute(entity.get("createSql").asString.replace("\${TABLE_NAME}", table))
                }
                for (idx in entity.getAsJsonArray("indices")) {
                    fresh.createStatement().use {
                        it.execute(idx.asJsonObject.get("createSql").asString.replace("\${TABLE_NAME}", table))
                    }
                }
                assertEquals(describe(fresh, table), describe(db, table))
                assertEquals(indices(fresh, table), indices(db, table))
            }
        } finally {
            fresh.close()
        }
    }

    @Test
    fun versionElevenOnlyAddsAuditTables() {
        fun tables(version: Int): Map<String, String> =
            JsonParser.parseString(schema(version).readText()).asJsonObject
                .getAsJsonObject("database").getAsJsonArray("entities")
                .map { it.asJsonObject }
                .associate { it.get("tableName").asString to it.get("createSql").asString }

        val v10 = tables(10)
        val v11 = tables(11)
        assertEquals(v10.keys + setOf("assistant_actions", "event_log"), v11.keys)
        for ((name, sql) in v10) assertEquals("table $name changed", sql, v11[name])
    }
}
