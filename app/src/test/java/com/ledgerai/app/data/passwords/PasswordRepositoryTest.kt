package com.ledgerai.app.data.passwords

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PasswordRepositoryTest {

    private class FakeDao : PasswordDao {
        val rows = linkedMapOf<String, PasswordEntity>()
        override fun observeAll(): Flow<List<PasswordEntity>> = flowOf(rows.values.toList())
        override suspend fun all() = rows.values.toList()
        override suspend fun get(id: String) = rows[id]
        override suspend fun upsert(row: PasswordEntity) { rows[row.id] = row }
        override suspend fun delete(id: String) { rows.remove(id) }
    }

    /** Reversible stand-in so the test does not need the Android Keystore. */
    private class FakeCipher : ValueCipher {
        override fun seal(plain: String) = if (plain.isEmpty()) "" else "sealed(" + plain.reversed() + ")"
        override fun open(sealed: String) = if (sealed.isEmpty()) "" else sealed.removePrefix("sealed(").removeSuffix(")").reversed()
    }

    private val dao = FakeDao()
    private val repo = PasswordRepository(dao, FakeCipher())

    @Test
    fun storedRowHoldsNoPlaintextSecretUsernameOrNote() = runBlocking {
        val id = repo.save(null, "Gmail", "accounts.google.com", "rafi.k", "hunter2", "recovery phone", null)
        val row = dao.rows.getValue(id)
        assertFalse(row.secretEnc.contains("hunter2"))
        assertFalse(row.usernameEnc.contains("rafi.k"))
        assertFalse(row.noteEnc.contains("recovery"))
        assertEquals("Gmail", row.title)
    }

    @Test
    fun revealOpensWhatWasSaved() = runBlocking {
        val id = repo.save(null, "Bank", null, "u", "p@ss", "n", 90)
        val shown = repo.reveal(id)!!
        assertEquals("u", shown.username)
        assertEquals("p@ss", shown.secret)
        assertEquals("n", shown.note)
        assertEquals(90, shown.changeEveryDays)
    }

    @Test
    fun lastChangedMovesOnlyWhenTheSecretChanges() = runBlocking {
        val id = repo.save(null, "Site", null, "u", "one", "", null, now = 1_000L)
        repo.save(id, "Site renamed", null, "u", "one", "", null, now = 9_000L)
        assertEquals(1_000L, dao.rows.getValue(id).lastChanged)
        repo.save(id, "Site renamed", null, "u", "two", "", null, now = 20_000L)
        assertEquals(20_000L, dao.rows.getValue(id).lastChanged)
        assertEquals(1_000L, dao.rows.getValue(id).createdAt)
    }

    @Test
    fun deleteRemovesTheRowForGood() = runBlocking {
        val id = repo.save(null, "Temp", null, "u", "p", "", null)
        repo.delete(id)
        assertNull(repo.reveal(id))
    }

    @Test
    fun overdueUsesTheChangeEveryWindowWithoutDecrypting() {
        val day = 24L * 60 * 60 * 1000
        val old = PasswordEntity("a", "Old", null, lastChanged = 0, changeEveryDays = 30, createdAt = 0, updatedAt = 0)
        val fresh = PasswordEntity("b", "Fresh", null, lastChanged = 40 * day, changeEveryDays = 30, createdAt = 0, updatedAt = 0)
        val never = PasswordEntity("c", "Never", null, lastChanged = 0, changeEveryDays = null, createdAt = 0, updatedAt = 0)
        assertEquals(listOf("a"), repo.overdue(listOf(old, fresh, never), now = 50 * day))
    }

    @Test
    fun noRegisteredActionTypeMentionsPasswordsOrAppUsage() {
        val roots = listOf(File("src/main/java"), File("app/src/main/java")).filter { it.exists() }
        assertTrue("source folder not found from ${File(".").absolutePath}", roots.isNotEmpty())
        val typeLiteral = Regex("""type\s*=\s*"([a-z_.]+)"""")
        val offenders = roots.flatMap { root ->
            root.walkTopDown().filter { it.extension == "kt" && it.path.contains("assistant", true) || it.path.contains("capture") || it.path.contains("clips") }
                .filter { it.isFile }
                .flatMap { f -> typeLiteral.findAll(f.readText()).map { it.groupValues[1] } }
                .filter { it.contains("password") || it.contains("app_usage") || it.contains("passcode") }
                .toList()
        }
        assertEquals(emptyList<String>(), offenders)
    }
}
