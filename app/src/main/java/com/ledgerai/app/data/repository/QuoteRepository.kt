package com.ledgerai.app.data.repository

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.ledgerai.app.BuildConfig
import com.ledgerai.app.data.preferences.UserSession
import com.ledgerai.app.data.sync.PostgrestApi
import com.ledgerai.app.data.sync.RemoteQuotesSeenDto
import com.ledgerai.app.data.sync.SyncTime
import com.ledgerai.app.domain.model.Quote
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QuoteRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson,
    private val api: PostgrestApi,
    private val userSession: UserSession
) {

    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val anonKey: String get() = BuildConfig.SUPABASE_ANON_KEY

    private var cached: List<Quote>? = null

    fun loadQuotes(): List<Quote> {
        cached?.let { return it }
        val json = context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
        val type = object : TypeToken<List<QuoteDto>>() {}.type
        val list: List<QuoteDto> = gson.fromJson(json, type) ?: emptyList()
        return list.map { Quote(text = it.text, author = it.author.orEmpty()) }
            .also { cached = it }
    }

    /**
     * Deterministic daily pick from the bundled set (offline / sync callers).
     * Prefer [pickDailyQuote] when a remote session may exist so seen quotes are skipped.
     */
    fun todaysQuote(date: LocalDate = LocalDate.now()): Quote {
        val quotes = loadQuotes()
        if (quotes.isEmpty()) return FALLBACK_QUOTE
        return pickFromPool(quotes, date)
    }

    /**
     * Daily pick that merges local + remote [quotes_seen], prefers unseen quotes when practical,
     * then marks the choice seen (SharedPreferences always; PostgREST when signed in remotely).
     */
    suspend fun pickDailyQuote(date: LocalDate = LocalDate.now()): Quote {
        val quotes = loadQuotes()
        if (quotes.isEmpty()) return FALLBACK_QUOTE

        val localSeen = loadLocalSeenHashes()
        val remoteSeen = fetchRemoteSeenHashes()
        val seen = localSeen + remoteSeen
        if (remoteSeen.isNotEmpty()) {
            saveLocalSeenHashes(seen)
        }

        val unseen = quotes.filter { quoteHash(it) !in seen }
        val pool = if (unseen.isNotEmpty()) unseen else quotes
        val quote = pickFromPool(pool, date)
        markQuoteSeen(quote, date)
        return quote
    }

    fun persistForWidget(quote: Quote = todaysQuote()) {
        prefs.edit()
            .putString(KEY_TEXT, quote.text)
            .putString(KEY_AUTHOR, quote.author)
            .putString(KEY_DATE, LocalDate.now().toString())
            .apply()
    }

    /** Suspend variant: remote-aware pick + widget prefs. */
    suspend fun persistForWidgetRemote(date: LocalDate = LocalDate.now()) {
        val quote = pickDailyQuote(date)
        persistForWidget(quote)
    }

    fun readWidgetQuote(): Quote {
        val date = prefs.getString(KEY_DATE, null)
        if (date == LocalDate.now().toString()) {
            val text = prefs.getString(KEY_TEXT, null)
            if (!text.isNullOrBlank()) {
                return Quote(text = text, author = prefs.getString(KEY_AUTHOR, "").orEmpty())
            }
        }
        val quote = todaysQuote()
        persistForWidget(quote)
        return quote
    }

    /** Stable id for a quote (schema column [quote_hash]). */
    fun quoteHash(quote: Quote): String = hashUtf8("${quote.text}\n${quote.author}")

    suspend fun markQuoteSeen(quote: Quote, date: LocalDate = LocalDate.now()) {
        val hash = quoteHash(quote)
        val local = loadLocalSeenHashes().toMutableSet()
        local += hash
        saveLocalSeenHashes(local)

        val info = userSession.userInfo.first()
        if (!info.hasRemoteUser) return
        if (BuildConfig.SUPABASE_URL.isBlank() || anonKey.isBlank()) return

        val bearer = "Bearer ${info.accessToken}"
        val nowIso = SyncTime.millisToIso(System.currentTimeMillis())
        val seenOn = SyncTime.dateToString(date)
        // Stable id so re-marks for the same day merge cleanly on PostgREST upsert.
        val rowId = UUID.nameUUIDFromBytes("${info.userId}|$hash|$seenOn".toByteArray()).toString()
        val row = RemoteQuotesSeenDto(
            id = rowId,
            userId = info.userId,
            quoteHash = hash,
            quoteText = quote.text,
            author = quote.author,
            seenOn = seenOn,
            updatedAt = nowIso,
            deletedAt = null
        )
        runCatching {
            withContext(Dispatchers.IO) {
                api.upsertQuotesSeen(bearer, anonKey, body = listOf(row))
            }
        }
    }

    private suspend fun fetchRemoteSeenHashes(): Set<String> {
        val info = userSession.userInfo.first()
        if (!info.hasRemoteUser) return emptySet()
        if (BuildConfig.SUPABASE_URL.isBlank() || anonKey.isBlank()) return emptySet()
        val bearer = "Bearer ${info.accessToken}"
        return runCatching {
            withContext(Dispatchers.IO) {
                api.pullQuotesSeen(bearer, anonKey)
                    .mapNotNull { it.quoteHash.takeIf(String::isNotBlank) }
                    .toSet()
            }
        }.getOrDefault(emptySet())
    }

    private fun loadLocalSeenHashes(): Set<String> =
        prefs.getStringSet(KEY_SEEN_HASHES, emptySet())?.toSet() ?: emptySet()

    private fun saveLocalSeenHashes(hashes: Set<String>) {
        prefs.edit().putStringSet(KEY_SEEN_HASHES, hashes).apply()
    }

    private fun pickFromPool(pool: List<Quote>, date: LocalDate): Quote {
        val index = (date.toEpochDay() % pool.size).toInt().let { if (it < 0) -it else it }
        return pool[index]
    }

    private fun hashUtf8(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { b -> "%02x".format(b) }
    }

    private data class QuoteDto(val text: String, val author: String? = null)

    companion object {
        private const val ASSET_NAME = "quotes.json"
        private const val PREFS_NAME = "ledgerai_quotes"
        private const val KEY_TEXT = "widget_quote_text"
        private const val KEY_AUTHOR = "widget_quote_author"
        private const val KEY_DATE = "widget_quote_date"
        private const val KEY_SEEN_HASHES = "quotes_seen_hashes"
        private val FALLBACK_QUOTE =
            Quote(text = "Stay focused on what matters today.", author = "LedgerAI")
    }
}
