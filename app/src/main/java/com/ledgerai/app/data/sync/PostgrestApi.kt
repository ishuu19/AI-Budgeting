package com.ledgerai.app.data.sync

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * Minimal PostgREST client for Supabase (`/rest/v1`).
 * Auth: `apikey` = anon key, `Authorization` = Bearer user JWT.
 */
interface PostgrestApi {

    @GET("transactions")
    suspend fun pullTransactions(
        @Header("Authorization") authorization: String,
        @Header("apikey") apiKey: String,
        @Query("updated_at") updatedAt: String,
        @Query("select") select: String = "*"
    ): List<RemoteTransactionDto>

    @POST("transactions")
    suspend fun upsertTransactions(
        @Header("Authorization") authorization: String,
        @Header("apikey") apiKey: String,
        @Header("Prefer") prefer: String = UPSERT_PREFER,
        @Query("on_conflict") onConflict: String = "id",
        @Body body: List<RemoteTransactionDto>
    ): List<RemoteTransactionDto>

    @GET("budgets")
    suspend fun pullBudgets(
        @Header("Authorization") authorization: String,
        @Header("apikey") apiKey: String,
        @Query("updated_at") updatedAt: String,
        @Query("select") select: String = "*"
    ): List<RemoteBudgetDto>

    @POST("budgets")
    suspend fun upsertBudgets(
        @Header("Authorization") authorization: String,
        @Header("apikey") apiKey: String,
        @Header("Prefer") prefer: String = UPSERT_PREFER,
        @Query("on_conflict") onConflict: String = "id",
        @Body body: List<RemoteBudgetDto>
    ): List<RemoteBudgetDto>

    @GET("debts")
    suspend fun pullDebts(
        @Header("Authorization") authorization: String,
        @Header("apikey") apiKey: String,
        @Query("updated_at") updatedAt: String,
        @Query("select") select: String = "*"
    ): List<RemoteDebtDto>

    @POST("debts")
    suspend fun upsertDebts(
        @Header("Authorization") authorization: String,
        @Header("apikey") apiKey: String,
        @Header("Prefer") prefer: String = UPSERT_PREFER,
        @Query("on_conflict") onConflict: String = "id",
        @Body body: List<RemoteDebtDto>
    ): List<RemoteDebtDto>

    @GET("goals")
    suspend fun pullGoals(
        @Header("Authorization") authorization: String,
        @Header("apikey") apiKey: String,
        @Query("updated_at") updatedAt: String,
        @Query("select") select: String = "*"
    ): List<RemoteGoalDto>

    @POST("goals")
    suspend fun upsertGoals(
        @Header("Authorization") authorization: String,
        @Header("apikey") apiKey: String,
        @Header("Prefer") prefer: String = UPSERT_PREFER,
        @Query("on_conflict") onConflict: String = "id",
        @Body body: List<RemoteGoalDto>
    ): List<RemoteGoalDto>

    @GET("bills")
    suspend fun pullBills(
        @Header("Authorization") authorization: String,
        @Header("apikey") apiKey: String,
        @Query("updated_at") updatedAt: String,
        @Query("select") select: String = "*"
    ): List<RemoteBillDto>

    @POST("bills")
    suspend fun upsertBills(
        @Header("Authorization") authorization: String,
        @Header("apikey") apiKey: String,
        @Header("Prefer") prefer: String = UPSERT_PREFER,
        @Query("on_conflict") onConflict: String = "id",
        @Body body: List<RemoteBillDto>
    ): List<RemoteBillDto>

    @GET("tasks")
    suspend fun pullTasks(
        @Header("Authorization") authorization: String,
        @Header("apikey") apiKey: String,
        @Query("updated_at") updatedAt: String,
        @Query("select") select: String = "*"
    ): List<RemoteTaskDto>

    @POST("tasks")
    suspend fun upsertTasks(
        @Header("Authorization") authorization: String,
        @Header("apikey") apiKey: String,
        @Header("Prefer") prefer: String = UPSERT_PREFER,
        @Query("on_conflict") onConflict: String = "id",
        @Body body: List<RemoteTaskDto>
    ): List<RemoteTaskDto>

    @GET("reminders")
    suspend fun pullReminders(
        @Header("Authorization") authorization: String,
        @Header("apikey") apiKey: String,
        @Query("updated_at") updatedAt: String,
        @Query("select") select: String = "*"
    ): List<RemoteReminderDto>

    @POST("reminders")
    suspend fun upsertReminders(
        @Header("Authorization") authorization: String,
        @Header("apikey") apiKey: String,
        @Header("Prefer") prefer: String = UPSERT_PREFER,
        @Query("on_conflict") onConflict: String = "id",
        @Body body: List<RemoteReminderDto>
    ): List<RemoteReminderDto>

    @GET("alarms")
    suspend fun pullAlarms(
        @Header("Authorization") authorization: String,
        @Header("apikey") apiKey: String,
        @Query("updated_at") updatedAt: String,
        @Query("select") select: String = "*"
    ): List<RemoteAlarmDto>

    @POST("alarms")
    suspend fun upsertAlarms(
        @Header("Authorization") authorization: String,
        @Header("apikey") apiKey: String,
        @Header("Prefer") prefer: String = UPSERT_PREFER,
        @Query("on_conflict") onConflict: String = "id",
        @Body body: List<RemoteAlarmDto>
    ): List<RemoteAlarmDto>

    @GET("notes")
    suspend fun pullNotes(
        @Header("Authorization") authorization: String,
        @Header("apikey") apiKey: String,
        @Query("updated_at") updatedAt: String,
        @Query("select") select: String = "*"
    ): List<RemoteNoteDto>

    @POST("notes")
    suspend fun upsertNotes(
        @Header("Authorization") authorization: String,
        @Header("apikey") apiKey: String,
        @Header("Prefer") prefer: String = UPSERT_PREFER,
        @Query("on_conflict") onConflict: String = "id",
        @Body body: List<RemoteNoteDto>
    ): List<RemoteNoteDto>

    @GET("routines")
    suspend fun pullRoutines(
        @Header("Authorization") authorization: String,
        @Header("apikey") apiKey: String,
        @Query("updated_at") updatedAt: String,
        @Query("select") select: String = "*"
    ): List<RemoteRoutineDto>

    @POST("routines")
    suspend fun upsertRoutines(
        @Header("Authorization") authorization: String,
        @Header("apikey") apiKey: String,
        @Header("Prefer") prefer: String = UPSERT_PREFER,
        @Query("on_conflict") onConflict: String = "id",
        @Body body: List<RemoteRoutineDto>
    ): List<RemoteRoutineDto>

    @GET("quotes_seen")
    suspend fun pullQuotesSeen(
        @Header("Authorization") authorization: String,
        @Header("apikey") apiKey: String,
        @Query("deleted_at") deletedAt: String = "is.null",
        @Query("select") select: String = "id,quote_hash,quote_text,author,seen_on,updated_at,deleted_at"
    ): List<RemoteQuotesSeenDto>

    @POST("quotes_seen")
    suspend fun upsertQuotesSeen(
        @Header("Authorization") authorization: String,
        @Header("apikey") apiKey: String,
        @Header("Prefer") prefer: String = UPSERT_PREFER,
        @Query("on_conflict") onConflict: String = "user_id,quote_hash,seen_on",
        @Body body: List<RemoteQuotesSeenDto>
    ): List<RemoteQuotesSeenDto>

    companion object {
        /** representation keeps Retrofit List<> deserialization happy (minimal = empty body). */
        const val UPSERT_PREFER = "resolution=merge-duplicates,return=representation"
    }
}
