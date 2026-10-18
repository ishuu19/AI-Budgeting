package com.ledgerai.app.data.local.room

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.sql.Connection
import java.sql.DriverManager

/** Version 20 clips gain notebook columns. Chat tables are created in the same step. */
class Migration20To21Test {

    private lateinit var db: Connection

    @Before
    fun setUp() {
        db = DriverManager.getConnection("jdbc:sqlite::memory:")
        exec("CREATE TABLE notes (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, title TEXT NOT NULL)")
        exec("INSERT INTO notes (title) VALUES ('Japan trip')")
        Migration19To20Sql.statements.forEach { exec(it) }
        exec(
            """
            INSERT INTO clips (id, text, textHash, sourceKind, status, sensitive, aiState, capturedAt, updatedAt)
            VALUES ('clip-1', 'Shinjuku', 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa', 'selection', 'inbox', 0, 'none', 10, 10)
            """.trimIndent()
        )
        Migration20To21Sql.statements.forEach { exec(it) }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun existingClipKeepsItsTextAndGainsNotebookColumns() {
        db.createStatement().use { st ->
            st.executeQuery("SELECT text, editedText, pinned, pinOrder, prompt, windowTitle, threadId, messageId FROM clips WHERE id = 'clip-1'").use { rs ->
                assertTrue(rs.next())
                assertEquals("Shinjuku", rs.getString("text"))
                assertNull(rs.getString("editedText"))
                assertEquals(0, rs.getInt("pinned"))
                assertNull(rs.getString("pinOrder"))
            }
        }
        exec("UPDATE clips SET editedText = 'edited line' WHERE id = 'clip-1'")
        db.createStatement().use { st ->
            st.executeQuery("SELECT text, editedText FROM clips WHERE id = 'clip-1'").use { rs ->
                assertTrue(rs.next())
                assertEquals("Shinjuku", rs.getString("text"))
                assertEquals("edited line", rs.getString("editedText"))
            }
        }
    }

    @Test
    fun chatTablesRoundTrip() {
        exec(
            """
            INSERT INTO chat_threads (id, title, origin, createdAt, updatedAt)
            VALUES ('thread-1', 'Park hours', 'notebook', 10, 10)
            """.trimIndent()
        )
        exec(
            """
            INSERT INTO chat_messages (id, threadId, role, text, state, createdAt, updatedAt)
            VALUES ('msg-1', 'thread-1', 'user', 'Summarize', 'queued', 11, 11)
            """.trimIndent()
        )
        db.createStatement().use { st ->
            st.executeQuery("SELECT title FROM chat_threads WHERE id = 'thread-1'").use { rs ->
                assertTrue(rs.next())
                assertEquals("Park hours", rs.getString("title"))
            }
        }
        db.createStatement().use { st ->
            st.executeQuery("SELECT text, state FROM chat_messages WHERE threadId = 'thread-1'").use { rs ->
                assertTrue(rs.next())
                assertEquals("Summarize", rs.getString("text"))
                assertEquals("queued", rs.getString("state"))
            }
        }
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

    private fun exec(sql: String) = db.createStatement().use { it.execute(sql) }
}
