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
        @Query("select") select: String = "*",
        @Query("order") order: String = PULL_ORDER,
        @Query("limit") limit: Int = PULL_PAGE_SIZE,
        @Query("offset") offset: Int = 0
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
        @Query("select") select: String = "*",
        @Query("order") order: String = PULL_ORDER,
        @Query("limit") limit: Int = PULL_PAGE_SIZE,
        @Query("offset") offset: Int = 0
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
        @Query("select") select: String = "*",
        @Query("order") order: String = PULL_ORDER,
        @Query("limit") limit: Int = PULL_PAGE_SIZE,
        @Query("offset") offset: Int = 0
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
        @Query("select") select: String = "*",
        @Query("order") order: String = PULL_ORDER,
        @Query("limit") limit: Int = PULL_PAGE_SIZE,
        @Query("offset") offset: Int = 0
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
        @Query("select") select: String = "*",
        @Query("order") order: String = PULL_ORDER,
        @Query("limit") limit: Int = PULL_PAGE_SIZE,
        @Query("offset") offset: Int = 0
    ): List<RemoteBillDto>

    @POST("bills")
    suspend fun upsertBills(
        @Header("Authorization") authorization: String,
        @Header("apikey") apiKey: String,
        @Header("Prefer") prefer: String = UPSERT_PREFER,
        @Query("on_conflict") onConflict: String = "id",
        @Body body: List<RemoteBillDto>
    ): List<RemoteBillDto>

    @GET("notes")
    suspend fun pullNotes(
        @Header("Authorization") authorization: String,
        @Header("apikey") apiKey: String,
        @Query("updated_at") updatedAt: String,
        @Query("select") select: String = "*",
        @Query("order") order: String = PULL_ORDER,
        @Query("limit") limit: Int = PULL_PAGE_SIZE,
        @Query("offset") offset: Int = 0
    ): List<RemoteNoteDto>

    @POST("notes")
    suspend fun upsertNotes(
        @Header("Authorization") authorization: String,
        @Header("apikey") apiKey: String,
        @Header("Prefer") prefer: String = UPSERT_PREFER,
        @Query("on_conflict") onConflict: String = "id",
        @Body body: List<RemoteNoteDto>
    ): List<RemoteNoteDto>

    @GET("calendar_events")
    suspend fun pullEvents(
        @Header("Authorization") authorization: String,
        @Header("apikey") apiKey: String,
        @Query("updated_at") updatedAt: String,
        @Query("select") select: String = "*",
        @Query("order") order: String = PULL_ORDER,
        @Query("limit") limit: Int = PULL_PAGE_SIZE,
        @Query("offset") offset: Int = 0
    ): List<RemoteEventDto>

    @POST("calendar_events")
    suspend fun upsertEvents(
        @Header("Authorization") authorization: String,
        @Header("apikey") apiKey: String,
        @Header("Prefer") prefer: String = UPSERT_PREFER,
        @Query("on_conflict") onConflict: String = "id",
        @Body body: List<RemoteEventDto>
    ): List<RemoteEventDto>

    @GET("event_reminders")
    suspend fun pullEventReminders(
        @Header("Authorization") authorization: String,
        @Header("apikey") apiKey: String,
        @Query("updated_at") updatedAt: String,
        @Query("select") select: String = "*",
        @Query("order") order: String = PULL_ORDER,
        @Query("limit") limit: Int = PULL_PAGE_SIZE,
        @Query("offset") offset: Int = 0
    ): List<RemoteEventReminderDto>

    @POST("event_reminders")
    suspend fun upsertEventReminders(
        @Header("Authorization") authorization: String,
        @Header("apikey") apiKey: String,
        @Header("Prefer") prefer: String = UPSERT_PREFER,
        @Query("on_conflict") onConflict: String = "id",
        @Body body: List<RemoteEventReminderDto>
    ): List<RemoteEventReminderDto>

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
        const val PULL_ORDER = "updated_at.asc,id.asc"
        const val PULL_PAGE_SIZE = 500

        /** representation keeps Retrofit List<> deserialization happy (minimal = empty body). */
        const val UPSERT_PREFER = "resolution=merge-duplicates,return=representation"
    }
}
