package com.ledgerai.app.data.passwords

import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** A password with its secret fields opened. Only ever built in memory, never stored or logged. */
data class PasswordSecret(
    val id: String,
    val title: String,
    val site: String?,
    val username: String,
    val secret: String,
    val note: String,
    val lastChanged: Long,
    val changeEveryDays: Int?,
)

/**
 * Device-only store. There is no sync handler, no search index and no assistant action for this
 * table; the build has a test that fails if an action type mentioning "password" is registered.
 */
@Singleton
class PasswordRepository @Inject constructor(
    private val dao: PasswordDao,
    private val cipher: ValueCipher,
) {

    suspend fun save(
        id: String?,
        title: String,
        site: String?,
        username: String,
        secret: String,
        note: String,
        changeEveryDays: Int?,
        now: Long = System.currentTimeMillis(),
    ): String {
        require(title.isNotBlank()) { "Title is required" }
        val existing = id?.let { dao.get(it) }
        val changed = existing?.takeIf { cipher.open(it.secretEnc) == secret }?.lastChanged ?: now
        val row = PasswordEntity(
            id = existing?.id ?: UUID.randomUUID().toString(),
            title = title.trim(),
            site = site?.trim()?.ifBlank { null },
            usernameEnc = cipher.seal(username),
            secretEnc = cipher.seal(secret),
            noteEnc = cipher.seal(note),
            lastChanged = changed,
            changeEveryDays = changeEveryDays,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
        dao.upsert(row)
        return row.id
    }

    suspend fun reveal(id: String): PasswordSecret? = dao.get(id)?.let {
        PasswordSecret(
            id = it.id,
            title = it.title,
            site = it.site,
            username = cipher.open(it.usernameEnc),
            secret = cipher.open(it.secretEnc),
            note = cipher.open(it.noteEnc),
            lastChanged = it.lastChanged,
            changeEveryDays = it.changeEveryDays,
        )
    }

    suspend fun delete(id: String) = dao.delete(id)

    /**
     * One file the user keeps, sealed with their passphrase (PBKDF2 key, AES-GCM). It is the only way
     * passwords leave the phone, and it is how they reach a new one. Layout: "LPW1", salt, iv, ciphertext.
     */
    suspend fun export(passphrase: CharArray): ByteArray {
        val items = dao.all().map { reveal(it.id) }.filterNotNull()
        val json = com.google.gson.Gson().toJson(items).toByteArray(Charsets.UTF_8)
        val rnd = java.security.SecureRandom()
        val salt = ByteArray(16).also(rnd::nextBytes)
        val iv = ByteArray(12).also(rnd::nextBytes)
        val c = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        c.init(javax.crypto.Cipher.ENCRYPT_MODE, backupKey(passphrase, salt), javax.crypto.spec.GCMParameterSpec(128, iv))
        return MAGIC + salt + iv + c.doFinal(json)
    }

    /** Merges by title and site; an existing entry is updated, not duplicated. Returns how many were applied. */
    suspend fun import(bytes: ByteArray, passphrase: CharArray): Int {
        require(bytes.size > MAGIC.size + 28 && bytes.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) { "That is not a password backup" }
        val salt = bytes.copyOfRange(MAGIC.size, MAGIC.size + 16)
        val iv = bytes.copyOfRange(MAGIC.size + 16, MAGIC.size + 28)
        val c = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        c.init(javax.crypto.Cipher.DECRYPT_MODE, backupKey(passphrase, salt), javax.crypto.spec.GCMParameterSpec(128, iv))
        val plain = try {
            c.doFinal(bytes.copyOfRange(MAGIC.size + 28, bytes.size))
        } catch (_: javax.crypto.AEADBadTagException) {
            throw IllegalArgumentException("Wrong passphrase")
        }
        val items = com.google.gson.Gson().fromJson(String(plain, Charsets.UTF_8), Array<PasswordSecret>::class.java).toList()
        val existing = dao.all()
        items.forEach { p ->
            val match = existing.firstOrNull { it.title.equals(p.title, true) && (it.site ?: "").equals(p.site ?: "", true) }
            val id = save(match?.id, p.title, p.site, p.username, p.secret, p.note, p.changeEveryDays)
            dao.get(id)?.let { dao.upsert(it.copy(lastChanged = p.lastChanged)) }
        }
        return items.size
    }

    private fun backupKey(passphrase: CharArray, salt: ByteArray): javax.crypto.SecretKey {
        val spec = javax.crypto.spec.PBEKeySpec(passphrase, salt, 210_000, 256)
        val raw = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return javax.crypto.spec.SecretKeySpec(raw, "AES")
    }

    /** Ids whose change-every window has passed. Needs no decryption. */
    fun overdue(rows: List<PasswordEntity>, now: Long = System.currentTimeMillis()): List<String> =
        rows.filter { r ->
            val days = r.changeEveryDays ?: return@filter false
            now - r.lastChanged > days * DAY_MS
        }.map { it.id }

    private companion object {
        val MAGIC = "LPW1".toByteArray(Charsets.US_ASCII)
        const val DAY_MS = 24L * 60 * 60 * 1000
    }
}
