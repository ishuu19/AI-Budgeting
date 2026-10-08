package com.ledgerai.app.data.sync

import com.ledgerai.app.BuildConfig
import com.ledgerai.app.data.local.room.AlarmDao
import com.ledgerai.app.data.local.room.BillDao
import com.ledgerai.app.data.local.room.BudgetDao
import com.ledgerai.app.data.local.room.DebtDao
import com.ledgerai.app.data.local.room.GoalDao
import com.ledgerai.app.data.local.room.NoteDao
import com.ledgerai.app.data.local.room.RoutineDao
import com.ledgerai.app.data.local.room.TaskDao
import com.ledgerai.app.data.local.room.TaskReminderDao
import com.ledgerai.app.data.local.room.TransactionDao
import com.ledgerai.app.data.preferences.UserSession
import kotlinx.coroutines.flow.first
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room ↔ Supabase PostgREST sync with last-write-wins on [updatedAt].
 * Soft deletes via [deletedAt]. Requires a remote user + Bearer JWT in [UserSession].
 */
@Singleton
class SyncRepository @Inject constructor(
    private val api: PostgrestApi,
    private val userSession: UserSession,
    private val cursorStore: SyncCursorStore,
    private val transactionDao: TransactionDao,
    private val budgetDao: BudgetDao,
    private val debtDao: DebtDao,
    private val goalDao: GoalDao,
    private val billDao: BillDao,
    private val taskDao: TaskDao,
    private val reminderDao: TaskReminderDao,
    private val routineDao: RoutineDao,
    private val alarmDao: AlarmDao,
    private val noteDao: NoteDao,
) {

    private val anonKey: String get() = BuildConfig.SUPABASE_ANON_KEY

    /**
     * @return false when skipped (missing config / no remote session); true after a sync attempt.
     */
    suspend fun syncAll(): Boolean {
        if (BuildConfig.SUPABASE_URL.isBlank() || anonKey.isBlank()) return false
        val info = userSession.userInfo.first()
        if (!info.hasRemoteUser) return false

        val bearer = "Bearer ${info.accessToken}"
        val userId = info.userId
        val sinceMs = cursorStore.lastSyncMs()
        val startedAt = System.currentTimeMillis()
        val filter = SyncTime.postgrestGtFilter(sinceMs)

        pullTransactions(bearer, filter)
        pullBudgets(bearer, filter)
        pullDebts(bearer, filter)
        pullGoals(bearer, filter)
        pullBills(bearer, filter)
        pullTasks(bearer, filter)
        pullReminders(bearer, filter)
        pullRoutines(bearer, filter)
        pullAlarms(bearer, filter)
        pullNotes(bearer, filter)

        pushTransactions(userId, sinceMs, bearer)
        pushBudgets(userId, sinceMs, bearer)
        pushDebts(userId, sinceMs, bearer)
        pushGoals(userId, sinceMs, bearer)
        pushBills(userId, sinceMs, bearer)
        pushTasks(userId, sinceMs, bearer)
        pushReminders(userId, sinceMs, bearer)
        pushRoutines(userId, sinceMs, bearer)
        pushAlarms(userId, sinceMs, bearer)
        pushNotes(userId, sinceMs, bearer)

        cursorStore.saveLastSyncMs(startedAt)
        return true
    }

    private fun newId(): String = UUID.randomUUID().toString()

    // ─── pull (remote wins if remote.updatedAt >= local.updatedAt) ───────────

    private suspend fun pullTransactions(bearer: String, filter: String) {
        api.pullTransactions(bearer, anonKey, filter).forEach { remote ->
            val local = transactionDao.getByRemoteId(remote.id)
            if (local == null) transactionDao.insert(remote.toEntity())
            else if (SyncTime.remoteWins(remote.updatedAt, local.updatedAt)) {
                transactionDao.update(remote.toEntity(localId = local.id).copy(location = local.location))
            }
        }
    }

    private suspend fun pullBudgets(bearer: String, filter: String) {
        api.pullBudgets(bearer, anonKey, filter).forEach { remote ->
            val remoteUpdated = SyncTime.isoToMillis(remote.updatedAt)
            val local = budgetDao.getByRemoteId(remote.id)
            if (local == null) budgetDao.insert(remote.toEntity())
            else if (remoteUpdated >= local.updatedAt) {
                budgetDao.update(remote.toEntity(localId = local.id))
            }
        }
    }

    private suspend fun pullDebts(bearer: String, filter: String) {
        api.pullDebts(bearer, anonKey, filter).forEach { remote ->
            val remoteUpdated = SyncTime.isoToMillis(remote.updatedAt)
            val local = debtDao.getByRemoteId(remote.id)
            if (local == null) debtDao.insert(remote.toEntity())
            else if (remoteUpdated >= local.updatedAt) {
                debtDao.update(remote.toEntity(localId = local.id).copy(location = local.location))
            }
        }
    }

    private suspend fun pullGoals(bearer: String, filter: String) {
        api.pullGoals(bearer, anonKey, filter).forEach { remote ->
            val remoteUpdated = SyncTime.isoToMillis(remote.updatedAt)
            val local = goalDao.getByRemoteId(remote.id)
            if (local == null) goalDao.insert(remote.toEntity())
            else if (remoteUpdated >= local.updatedAt) {
                goalDao.update(remote.toEntity(localId = local.id).copy(location = local.location))
            }
        }
    }

    private suspend fun pullBills(bearer: String, filter: String) {
        api.pullBills(bearer, anonKey, filter).forEach { remote ->
            val remoteUpdated = SyncTime.isoToMillis(remote.updatedAt)
            val local = billDao.getByRemoteId(remote.id)
            if (local == null) billDao.insert(remote.toEntity())
            else if (remoteUpdated >= local.updatedAt) {
                billDao.update(remote.toEntity(localId = local.id).copy(location = local.location))
            }
        }
    }

    private suspend fun pullTasks(bearer: String, filter: String) {
        api.pullTasks(bearer, anonKey, filter).forEach { remote ->
            val remoteUpdated = SyncTime.isoToMillis(remote.updatedAt)
            val local = taskDao.getByRemoteId(remote.id)
            if (local == null) taskDao.insert(remote.toEntity())
            else if (remoteUpdated >= local.updatedAt) {
                taskDao.update(remote.toEntity(localId = local.id).copy(location = local.location, links = local.links))
            }
        }
    }

    private suspend fun pullReminders(bearer: String, filter: String) {
        api.pullReminders(bearer, anonKey, filter).forEach { remote ->
            val task = taskDao.getByRemoteId(remote.taskId) ?: return@forEach
            val remoteUpdated = SyncTime.isoToMillis(remote.updatedAt)
            val local = reminderDao.getByRemoteId(remote.id)
            if (local == null) {
                reminderDao.insert(remote.toEntity(localTaskId = task.id))
            } else if (remoteUpdated >= local.updatedAt) {
                reminderDao.update(remote.toEntity(localId = local.id, localTaskId = task.id))
            }
        }
    }

    private suspend fun pullRoutines(bearer: String, filter: String) {
        api.pullRoutines(bearer, anonKey, filter).forEach { remote ->
            val remoteUpdated = SyncTime.isoToMillis(remote.updatedAt)
            val local = routineDao.getByRemoteId(remote.id)
            if (local == null) routineDao.insert(remote.toEntity())
            else if (remoteUpdated >= local.updatedAt) {
                routineDao.update(remote.toEntity(localId = local.id).copy(location = local.location))
            }
        }
    }

    private suspend fun pullAlarms(bearer: String, filter: String) {
        api.pullAlarms(bearer, anonKey, filter).forEach { remote ->
            val remoteUpdated = SyncTime.isoToMillis(remote.updatedAt)
            val local = alarmDao.getByRemoteId(remote.id)
            if (local == null) alarmDao.insert(remote.toEntity())
            else if (remoteUpdated >= local.updatedAt) {
                alarmDao.update(remote.toEntity(localId = local.id).copy(location = local.location))
            }
        }
    }

    private suspend fun pullNotes(bearer: String, filter: String) {
        api.pullNotes(bearer, anonKey, filter).forEach { remote ->
            val remoteUpdated = SyncTime.isoToMillis(remote.updatedAt)
            val local = noteDao.getByRemoteId(remote.id)
            if (local == null) noteDao.insert(remote.toEntity())
            else if (remoteUpdated >= local.updatedAt) {
                noteDao.update(remote.toEntity(localId = local.id).copy(location = local.location))
            }
        }
    }

    // ─── push ────────────────────────────────────────────────────────────────

    private suspend fun pushTransactions(userId: String, sinceMs: Long, bearer: String) {
        val locals = transactionDao.listForSync(sinceMs)
        if (locals.isEmpty()) return
        val rows = ArrayList<RemoteTransactionDto>(locals.size)
        for (local in locals) {
            val rid = local.remoteId ?: run {
                val id = newId()
                transactionDao.update(local.copy(remoteId = id, userId = userId))
                id
            }
            rows += local.toRemoteDto(rid, userId)
        }
        api.upsertTransactions(bearer, anonKey, body = rows)
    }

    private suspend fun pushBudgets(userId: String, sinceMs: Long, bearer: String) {
        val locals = budgetDao.listForSync(sinceMs)
        if (locals.isEmpty()) return
        val rows = ArrayList<RemoteBudgetDto>(locals.size)
        for (local in locals) {
            val rid = local.remoteId ?: run {
                val id = newId()
                budgetDao.update(local.copy(remoteId = id, userId = userId))
                id
            }
            rows += local.toRemoteDto(rid, userId)
        }
        api.upsertBudgets(bearer, anonKey, body = rows)
    }

    private suspend fun pushDebts(userId: String, sinceMs: Long, bearer: String) {
        val locals = debtDao.listForSync(sinceMs)
        if (locals.isEmpty()) return
        val rows = ArrayList<RemoteDebtDto>(locals.size)
        for (local in locals) {
            val rid = local.remoteId ?: run {
                val id = newId()
                debtDao.update(local.copy(remoteId = id, userId = userId))
                id
            }
            rows += local.toRemoteDto(rid, userId)
        }
        api.upsertDebts(bearer, anonKey, body = rows)
    }

    private suspend fun pushGoals(userId: String, sinceMs: Long, bearer: String) {
        val locals = goalDao.listForSync(sinceMs)
        if (locals.isEmpty()) return
        val rows = ArrayList<RemoteGoalDto>(locals.size)
        for (local in locals) {
            val rid = local.remoteId ?: run {
                val id = newId()
                goalDao.update(local.copy(remoteId = id, userId = userId))
                id
            }
            rows += local.toRemoteDto(rid, userId)
        }
        api.upsertGoals(bearer, anonKey, body = rows)
    }

    private suspend fun pushBills(userId: String, sinceMs: Long, bearer: String) {
        val locals = billDao.listForSync(sinceMs)
        if (locals.isEmpty()) return
        val rows = ArrayList<RemoteBillDto>(locals.size)
        for (local in locals) {
            val rid = local.remoteId ?: run {
                val id = newId()
                billDao.update(local.copy(remoteId = id, userId = userId))
                id
            }
            rows += local.toRemoteDto(rid, userId)
        }
        api.upsertBills(bearer, anonKey, body = rows)
    }

    private suspend fun pushTasks(userId: String, sinceMs: Long, bearer: String) {
        val locals = taskDao.listForSync(sinceMs)
        if (locals.isEmpty()) return
        val rows = ArrayList<RemoteTaskDto>(locals.size)
        for (local in locals) {
            val rid = local.remoteId ?: run {
                val id = newId()
                taskDao.update(local.copy(remoteId = id, userId = userId))
                id
            }
            rows += local.toRemoteDto(rid, userId)
        }
        api.upsertTasks(bearer, anonKey, body = rows)
    }

    private suspend fun pushReminders(userId: String, sinceMs: Long, bearer: String) {
        val locals = reminderDao.listForSync(sinceMs)
        if (locals.isEmpty()) return
        val rows = ArrayList<RemoteReminderDto>()
        for (local in locals) {
            val task = taskDao.getByIdAny(local.taskId) ?: continue
            val taskRemote = task.remoteId ?: continue
            val rid = local.remoteId ?: run {
                val id = newId()
                reminderDao.update(local.copy(remoteId = id, userId = userId))
                id
            }
            rows += local.toRemoteDto(rid, userId, taskRemote)
        }
        if (rows.isEmpty()) return
        api.upsertReminders(bearer, anonKey, body = rows)
    }

    private suspend fun pushRoutines(userId: String, sinceMs: Long, bearer: String) {
        val locals = routineDao.listForSync(sinceMs)
        if (locals.isEmpty()) return
        val rows = ArrayList<RemoteRoutineDto>(locals.size)
        for (local in locals) {
            val rid = local.remoteId ?: run {
                val id = newId()
                routineDao.update(local.copy(remoteId = id, userId = userId))
                id
            }
            rows += local.toRemoteDto(rid, userId)
        }
        api.upsertRoutines(bearer, anonKey, body = rows)
    }

    private suspend fun pushAlarms(userId: String, sinceMs: Long, bearer: String) {
        val locals = alarmDao.listForSync(sinceMs)
        if (locals.isEmpty()) return
        val rows = ArrayList<RemoteAlarmDto>(locals.size)
        for (local in locals) {
            val rid = local.remoteId ?: run {
                val id = newId()
                alarmDao.update(local.copy(remoteId = id, userId = userId))
                id
            }
            rows += local.toRemoteDto(rid, userId)
        }
        api.upsertAlarms(bearer, anonKey, body = rows)
    }

    private suspend fun pushNotes(userId: String, sinceMs: Long, bearer: String) {
        val locals = noteDao.listForSync(sinceMs)
        if (locals.isEmpty()) return
        val rows = ArrayList<RemoteNoteDto>(locals.size)
        for (local in locals) {
            val rid = local.remoteId ?: run {
                val id = newId()
                noteDao.update(local.copy(remoteId = id, userId = userId))
                id
            }
            rows += local.toRemoteDto(rid, userId)
        }
        api.upsertNotes(bearer, anonKey, body = rows)
    }
}
