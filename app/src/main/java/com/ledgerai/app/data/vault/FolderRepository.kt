package com.ledgerai.app.data.vault

import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Vault folders. The two system folders are created on first use, with ids derived from the user
 * so two phones signed in as the same user agree on them and sync does not duplicate them.
 * Existing notes already belong to Notes (a null folderId), so nothing is moved.
 */
@Singleton
class FolderRepository @Inject constructor(
    private val folders: FolderDao,
) {

    suspend fun ensureSystemFolders(userId: String?): Pair<FolderEntity, FolderEntity> {
        val notes = ensure(userId, SystemFolder.NOTES, "Notes", sort = 0)
        val inbox = ensure(userId, SystemFolder.INBOX, "Inbox", sort = 1)
        return notes to inbox
    }

    private suspend fun ensure(userId: String?, key: String, name: String, sort: Int): FolderEntity {
        folders.bySystemKey(key)?.let { return it }
        val row = FolderEntity(
            id = systemId(userId, key),
            userId = userId,
            name = name,
            sortOrder = sort,
            systemKey = key,
            updatedAt = System.currentTimeMillis(),
        )
        folders.upsert(row)
        return row
    }

    suspend fun create(userId: String?, name: String, parentId: String? = null, locked: Boolean = false): FolderEntity {
        val clean = name.trim()
        require(clean.length in 1..MAX_NAME) { "Folder name must be 1 to $MAX_NAME characters" }
        if (parentId != null) {
            val parent = folders.get(parentId)
            // One level of nesting only.
            require(parent != null && parent.parentId == null) { "Folders nest one level deep" }
        }
        val row = FolderEntity(
            id = UUID.randomUUID().toString(),
            userId = userId,
            name = clean,
            parentId = parentId,
            locked = locked,
            deviceOnly = locked,
            sortOrder = 100,
            updatedAt = System.currentTimeMillis(),
        )
        folders.upsert(row)
        return row
    }

    suspend fun rename(id: String, name: String) {
        val row = folders.get(id) ?: return
        val clean = name.trim()
        require(clean.length in 1..MAX_NAME) { "Folder name must be 1 to $MAX_NAME characters" }
        folders.upsert(row.copy(name = clean, updatedAt = System.currentTimeMillis()))
    }

    suspend fun setPinned(id: String, pinned: Boolean) {
        val row = folders.get(id) ?: return
        folders.upsert(row.copy(pinned = pinned, updatedAt = System.currentTimeMillis()))
    }

    /** System folders ignore delete. */
    suspend fun delete(id: String) = folders.softDelete(id, System.currentTimeMillis())

    companion object {
        const val MAX_NAME = 40

        fun systemId(userId: String?, key: String): String =
            UUID.nameUUIDFromBytes("$key:${userId.orEmpty()}".toByteArray()).toString()
    }
}
