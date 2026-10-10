package com.ledgerai.app.data.ai

import com.ledgerai.app.domain.model.TransactionCategory
import java.io.InputStream

/**
 * Phrase tables for the offline rule engine, read from tab-separated files under `assets/rules/`.
 * Parsing is lazy and happens once, on the first lookup. A missing or empty file is an empty table.
 * Matching is on normalised tokens (lowercase, punctuation stripped, plural s dropped) and the longest
 * phrase wins, found through a first-token hash so 20k rows stay fast.
 */
class RuleLexicon(private val loader: () -> Map<String, List<List<String>>>) {

    constructor(tables: Map<String, List<List<String>>>) : this({ tables })

    /** A phrase found in text. [start] and [end] are character offsets; [cols] are the columns after the phrase. */
    data class Hit(val start: Int, val end: Int, val tokens: Int, val cols: List<String>)

    data class MerchantHit(val name: String, val category: TransactionCategory?, val start: Int, val end: Int)

    private val tables: Map<String, PhraseIndex> by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        loader().mapValues { (_, rows) -> PhraseIndex(rows) }
    }

    fun table(file: String): PhraseIndex = tables[file] ?: PhraseIndex.EMPTY

    val isEmpty: Boolean get() = tables.values.all { it.size == 0 }

    /** Known shop, brand or service in [text], with the category the file gives it. */
    fun merchant(text: String): MerchantHit? {
        val hit = table(MERCHANTS).findFirst(text) ?: return null
        val name = hit.cols.getOrNull(1)?.takeIf { it.isNotBlank() } ?: text.substring(hit.start, hit.end).titleCase()
        return MerchantHit(name, categoryOf(hit.cols.getOrNull(0)), hit.start, hit.end)
    }

    /** Category from a keyword phrase such as "groceries" or "taxi fare". */
    fun category(text: String): TransactionCategory? {
        for (hit in table(KEYWORDS).findAll(text)) categoryOf(hit.cols.getOrNull(0))?.let { return it }
        return null
    }

    fun has(file: String, text: String): Boolean = table(file).findFirst(text) != null

    /** Rewrites every phrase of [file] that has a normal form in column one. Longest phrase first. */
    fun rewrite(file: String, text: String): String {
        val hits = table(file).findAll(text).filter { it.cols.isNotEmpty() }
        if (hits.isEmpty()) return text
        val sb = StringBuilder()
        var pos = 0
        for (h in hits) {
            sb.append(text, pos, h.start).append(h.cols[0])
            pos = h.end
        }
        return sb.append(text, pos, text.length).toString()
    }

    /** Spoken number words from units_numbers.tsv (word, value) that the built-in list lacks. */
    fun numberWords(): Map<String, Double> = numberWordsCache
    private val numberWordsCache: Map<String, Double> by lazy {
        buildMap {
            for (row in tables[NUMBERS]?.rows.orEmpty()) {
                val v = row.getOrNull(1)?.toDoubleOrNull() ?: continue
                put(row[0].lowercase(), v)
            }
        }
    }

    class PhraseIndex(sourceRows: List<List<String>>) {
        private class Entry(val tokens: List<String>, val cols: List<String>)

        val rows: List<List<String>> = sourceRows
        private val byFirst = HashMap<String, MutableList<Entry>>()
        var size = 0
            private set

        init {
            for (row in sourceRows) {
                if (row.isEmpty()) continue
                val toks = tokenize(row[0]).map { it.norm }
                if (toks.isEmpty()) continue
                byFirst.getOrPut(toks[0]) { mutableListOf() }.add(Entry(toks, row.drop(1)))
                size++
            }
            byFirst.values.forEach { list -> list.sortByDescending { it.tokens.size } }
        }

        fun findAll(text: String): List<Hit> {
            if (size == 0) return emptyList()
            val toks = tokenize(text)
            val out = ArrayList<Hit>()
            var i = 0
            while (i < toks.size) {
                val hit = matchAt(toks, i)
                if (hit != null) {
                    out += hit
                    i += hit.tokens
                } else i++
            }
            return out
        }

        fun findFirst(text: String): Hit? {
            if (size == 0) return null
            val toks = tokenize(text)
            for (i in toks.indices) matchAt(toks, i)?.let { return it }
            return null
        }

        /** The phrase at the very start of [text], if any. */
        fun startsWith(text: String): Hit? {
            if (size == 0) return null
            val toks = tokenize(text)
            return if (toks.isEmpty()) null else matchAt(toks, 0)
        }

        private fun matchAt(toks: List<Tok>, i: Int): Hit? {
            val bucket = byFirst[toks[i].norm] ?: return null
            for (e in bucket) {
                if (i + e.tokens.size > toks.size) continue
                var ok = true
                for (k in 1 until e.tokens.size) if (toks[i + k].norm != e.tokens[k]) { ok = false; break }
                if (ok) return Hit(toks[i].start, toks[i + e.tokens.size - 1].end, e.tokens.size, e.cols)
            }
            return null
        }

        companion object {
            val EMPTY = PhraseIndex(emptyList())
        }
    }

    class Tok(val norm: String, val start: Int, val end: Int)

    companion object {
        const val MERCHANTS = "merchants.tsv"
        const val KEYWORDS = "keywords_category.tsv"
        const val TASK_VERBS = "task_verbs.tsv"
        const val TIME_PHRASES = "time_phrases.tsv"
        const val BANGLA = "bangla_terms.tsv"
        const val BILLS = "bills.tsv"
        const val PEOPLE = "people_roles.tsv"
        const val JOB_TERMS = "job_terms.tsv"
        const val NUMBERS = "units_numbers.tsv"

        val FILES = listOf(MERCHANTS, KEYWORDS, TASK_VERBS, TIME_PHRASES, BANGLA, BILLS, PEOPLE, JOB_TERMS, NUMBERS)

        val EMPTY = RuleLexicon(emptyMap<String, List<List<String>>>())

        /** Lexicon that reads each file through [open]; a null stream is an empty file. */
        fun fromStreams(open: (String) -> InputStream?): RuleLexicon = RuleLexicon({
            FILES.associateWith { name ->
                runCatching { open(name)?.use { parse(it.readBytes().toString(Charsets.UTF_8)) } }.getOrNull().orEmpty()
            }
        })

        /** Tab-separated rows. Blank lines and lines starting with # are skipped; a BOM is ignored. */
        fun parse(text: String): List<List<String>> =
            text.removePrefix("﻿").lineSequence()
                .map { it.trimEnd('\r') }
                .filter { it.isNotBlank() && !it.trimStart().startsWith("#") }
                .map { line -> line.split('\t').map { it.trim() } }
                .filter { it.isNotEmpty() && it[0].isNotEmpty() }
                .toList()

        fun categoryOf(name: String?): TransactionCategory? =
            name?.trim()?.let { n -> TransactionCategory.entries.firstOrNull { it.name.equals(n, true) } }

        private fun isWordChar(c: Char): Boolean =
            c.isLetterOrDigit() || when (Character.getType(c)) {
                Character.NON_SPACING_MARK.toInt(), Character.COMBINING_SPACING_MARK.toInt() -> true
                else -> false
            }

        /** Words with offsets. Apostrophes are dropped inside a word, a trailing plural s is dropped. */
        fun tokenize(text: String): List<Tok> {
            val out = ArrayList<Tok>()
            var i = 0
            val n = text.length
            while (i < n) {
                if (!isWordChar(text[i])) { i++; continue }
                val start = i
                val sb = StringBuilder()
                while (i < n) {
                    val c = text[i]
                    if (isWordChar(c)) sb.append(c.lowercaseChar())
                    else if ((c == '\'' || c == '’') && i + 1 < n && isWordChar(text[i + 1]) && sb.isNotEmpty()) { /* skip */ }
                    else break
                    i++
                }
                var w = sb.toString()
                if (w.length > 3 && w.endsWith("s") && !w.endsWith("ss") && w.all { it.code < 128 }) w = w.dropLast(1)
                out += Tok(w, start, i)
            }
            return out
        }

        private fun String.titleCase(): String =
            split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
    }
}
