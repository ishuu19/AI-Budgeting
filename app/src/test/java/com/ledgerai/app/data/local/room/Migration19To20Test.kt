package com.ledgerai.app.data.local.room

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.sql.Connection
import java.sql.DriverManager

/** Opens a stand-in for the version 19 database, runs [Migration19To20Sql], and inserts a clip. */
class Migration19To20Test {

    private lateinit var db: Connection

    @Before
    fun setUp() {
        db = DriverManager.getConnection("jdbc:sqlite::memory:")
        exec("CREATE TABLE notes (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, title TEXT NOT NULL)")
        exec("INSERT INTO notes (title) VALUES ('Japan trip')")
        Migration19To20Sql.statements.forEach { exec(it) }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun existingNotesStay() {
        db.createStatement().use { st ->
            st.executeQuery("SELECT title FROM notes").use { rs ->
                assertTrue(rs.next())
                assertEquals("Japan trip", rs.getString("title"))
            }
        }
    }

    @Test
    fun insertAClip() {
        exec(
            """
            INSERT INTO clips (id, text, textHash, sourceKind, status, sensitive, aiState, capturedAt, updatedAt)
            VALUES ('clip-1', 'Shinjuku', 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa', 'selection', 'inbox', 0, 'none', 10, 10)
            """.trimIndent()
        )
        db.createStatement().use { st ->
            st.executeQuery("SELECT text, status FROM clips WHERE id = 'clip-1'").use { rs ->
                assertTrue(rs.next())
                assertEquals("Shinjuku", rs.getString("text"))
                assertEquals("inbox", rs.getString("status"))
            }
        }
    }

    private fun exec(sql: String) = db.createStatement().use { it.execute(sql) }
}
