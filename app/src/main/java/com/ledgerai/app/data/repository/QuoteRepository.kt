package com.ledgerai.app.data.repository

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.ledgerai.app.domain.model.Quote
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QuoteRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson
) {

    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private var cached: List<Quote>? = null

    fun loadQuotes(): List<Quote> {
        cached?.let { return it }
        val json = context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
        val type = object : TypeToken<List<QuoteDto>>() {}.type
        val list: List<QuoteDto> = gson.fromJson(json, type) ?: emptyList()
        return list.map { Quote(text = it.text, author = it.author.orEmpty()) }
            .also { cached = it }
    }

    /** Deterministic daily pick from the bundled set. */
    fun todaysQuote(date: LocalDate = LocalDate.now()): Quote {
        val quotes = loadQuotes()
        if (quotes.isEmpty()) return Quote(text = "Stay focused on what matters today.", author = "LedgerAI")
        val index = (date.toEpochDay() % quotes.size).toInt().let { if (it < 0) -it else it }
        return quotes[index]
    }

    fun persistForWidget(quote: Quote = todaysQuote()) {
        prefs.edit()
            .putString(KEY_TEXT, quote.text)
            .putString(KEY_AUTHOR, quote.author)
            .putString(KEY_DATE, LocalDate.now().toString())
            .apply()
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

    private data class QuoteDto(val text: String, val author: String? = null)

    companion object {
        private const val ASSET_NAME = "quotes.json"
        private const val PREFS_NAME = "ledgerai_quotes"
        private const val KEY_TEXT = "widget_quote_text"
        private const val KEY_AUTHOR = "widget_quote_author"
        private const val KEY_DATE = "widget_quote_date"
    }
}
