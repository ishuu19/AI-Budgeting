package com.ledgerai.app.data.ai

import android.content.Context
import com.ledgerai.app.domain.model.TransactionCategory
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** A category (and canonical merchant name) suggested while typing a manual transaction. */
data class MerchantSuggestion(
    val category: TransactionCategory,
    /** Canonical spelling, or null when the typed text is already fine. */
    val merchant: String? = null,
    /** "Remembered" for a user correction, "Rules" for the lexicon. */
    val reason: String = SOURCE_RULES,
)

/** Looks up a merchant or note text and says which category it belongs to. Kept small and separate from RuleLexicon. */
interface MerchantCategoryLookup {
    fun lookup(merchant: String, note: String = ""): MerchantSuggestion?
}

private data class MerchantEntry(val alias: String, val canonical: String?, val category: TransactionCategory)

/** Reads assets/rules text files. Missing files are fine. */
object RuleAssets {
    fun read(context: Context, path: String): String? =
        runCatching { context.assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() } }.getOrNull()
}

/**
 * Pure matcher over `alias<TAB>canonical<TAB>CATEGORY` rows (merchants.tsv). Columns are found by content: the
 * column that names a [TransactionCategory] is the category, the other two are alias and canonical name.
 * Two-column rows (`alias<TAB>CATEGORY`) are accepted too.
 */
class TsvMerchantLookup(
    tsv: String,
    private val corrections: CorrectionStore? = null,
) : MerchantCategoryLookup {

    private val entries: List<MerchantEntry> =
        (rows(tsv).map { MerchantEntry(it.first, it.second, it.third) } + EMBEDDED).sortedByDescending { it.alias.length }

    override fun lookup(merchant: String, note: String): MerchantSuggestion? {
        val m = norm(merchant)
        val text = norm("$merchant $note")
        if (text.isEmpty()) return null
        corrections?.get(m.ifEmpty { text })?.let { return MerchantSuggestion(it, null, "Remembered") }
        // Whole-word or phrase match, longest alias first. The merchant field wins over the note.
        val hit = entries.firstOrNull { matches(m, it.alias) } ?: entries.firstOrNull { matches(text, it.alias) } ?: return null
        val typed = merchant.trim()
        val canonical = hit.canonical?.takeIf { typed.isNotEmpty() && it != typed && matches(m, hit.alias) }
        return MerchantSuggestion(hit.category, canonical)
    }

    private fun matches(text: String, alias: String): Boolean =
        text.isNotEmpty() && alias.isNotEmpty() && " $text ".contains(" $alias ")

    companion object {
        fun norm(s: String): String =
            s.lowercase(Locale.ENGLISH).replace("'", "").replace("’", "").replace(Regex("[^a-z0-9ঀ-৿ ]"), " ").replace(Regex("[ ]+"), " ").trim()

        private fun categoryOf(raw: String): TransactionCategory? {
            val t = raw.trim()
            return TransactionCategory.entries.firstOrNull { it.name.equals(t, true) || it.displayName.equals(t, true) }
        }

        private val EMBEDDED: List<MerchantEntry> = listOf(
            "starbucks" to ("Starbucks" to TransactionCategory.FOOD), "mcdonalds" to ("McDonald's" to TransactionCategory.FOOD),
            "kfc" to ("KFC" to TransactionCategory.FOOD), "pizza hut" to ("Pizza Hut" to TransactionCategory.FOOD),
            "dominos" to ("Domino's" to TransactionCategory.FOOD), "subway" to ("Subway" to TransactionCategory.FOOD),
            "foodpanda" to ("Foodpanda" to TransactionCategory.FOOD), "pathao food" to ("Pathao Food" to TransactionCategory.FOOD),
            "shwapno" to ("Shwapno" to TransactionCategory.FOOD), "agora" to ("Agora" to TransactionCategory.FOOD),
            "walmart" to ("Walmart" to TransactionCategory.FOOD), "costco" to ("Costco" to TransactionCategory.FOOD),
            "uber" to ("Uber" to TransactionCategory.TRANSPORT), "lyft" to ("Lyft" to TransactionCategory.TRANSPORT),
            "pathao" to ("Pathao" to TransactionCategory.TRANSPORT), "obhai" to ("Obhai" to TransactionCategory.TRANSPORT),
            "shell" to ("Shell" to TransactionCategory.TRANSPORT), "metro" to ("Metro" to TransactionCategory.TRANSPORT),
            "netflix" to ("Netflix" to TransactionCategory.SUBSCRIPTIONS), "spotify" to ("Spotify" to TransactionCategory.SUBSCRIPTIONS),
            "youtube premium" to ("YouTube Premium" to TransactionCategory.SUBSCRIPTIONS), "icloud" to ("iCloud" to TransactionCategory.SUBSCRIPTIONS),
            "chatgpt" to ("ChatGPT" to TransactionCategory.SUBSCRIPTIONS), "disney" to ("Disney+" to TransactionCategory.SUBSCRIPTIONS),
            "amazon" to ("Amazon" to TransactionCategory.SHOPPING), "daraz" to ("Daraz" to TransactionCategory.SHOPPING),
            "ikea" to ("IKEA" to TransactionCategory.SHOPPING), "zara" to ("Zara" to TransactionCategory.SHOPPING),
            "aarong" to ("Aarong" to TransactionCategory.SHOPPING), "ebay" to ("eBay" to TransactionCategory.SHOPPING),
            "cinema" to ("Cinema" to TransactionCategory.ENTERTAINMENT), "steam" to ("Steam" to TransactionCategory.ENTERTAINMENT),
            "playstation" to ("PlayStation" to TransactionCategory.ENTERTAINMENT), "star cineplex" to ("Star Cineplex" to TransactionCategory.ENTERTAINMENT),
            "pharmacy" to ("Pharmacy" to TransactionCategory.HEALTH), "square pharma" to ("Square Pharmacy" to TransactionCategory.HEALTH),
            "lazz pharma" to ("Lazz Pharma" to TransactionCategory.HEALTH), "hospital" to ("Hospital" to TransactionCategory.HEALTH),
            "clinic" to ("Clinic" to TransactionCategory.HEALTH), "gym" to ("Gym" to TransactionCategory.HEALTH),
            "grameenphone" to ("Grameenphone" to TransactionCategory.UTILITIES), "robi" to ("Robi" to TransactionCategory.UTILITIES),
            "banglalink" to ("Banglalink" to TransactionCategory.UTILITIES), "dpdc" to ("DPDC" to TransactionCategory.UTILITIES),
            "desco" to ("DESCO" to TransactionCategory.UTILITIES), "titas gas" to ("Titas Gas" to TransactionCategory.UTILITIES),
            "wasa" to ("WASA" to TransactionCategory.UTILITIES), "electricity" to ("Electricity" to TransactionCategory.UTILITIES),
            "landlord" to ("Landlord" to TransactionCategory.RENT), "house rent" to ("House rent" to TransactionCategory.RENT),
            "udemy" to ("Udemy" to TransactionCategory.EDUCATION), "coursera" to ("Coursera" to TransactionCategory.EDUCATION),
            "tuition" to ("Tuition" to TransactionCategory.EDUCATION), "bookshop" to ("Bookshop" to TransactionCategory.EDUCATION),
            "upwork" to ("Upwork" to TransactionCategory.FREELANCE), "fiverr" to ("Fiverr" to TransactionCategory.FREELANCE),
            "payroll" to ("Payroll" to TransactionCategory.SALARY), "salary" to ("Salary" to TransactionCategory.SALARY),
        ).map { (alias, v) -> MerchantEntry(alias, v.first, v.second) }

        internal fun rows(tsv: String): List<Triple<String, String?, TransactionCategory>> {
            val out = mutableListOf<Triple<String, String?, TransactionCategory>>()
            for (raw in tsv.lines()) {
                val line = raw.trim()
                if (line.isEmpty() || line.startsWith("#")) continue
                val cols = line.split('\t').map { it.trim() }
                if (cols.size < 2) continue
                val catIdx = cols.indexOfFirst { categoryOf(it) != null }
                if (catIdx < 0) continue
                val cat = categoryOf(cols[catIdx])!!
                val rest = cols.filterIndexed { i, _ -> i != catIdx }.filter { it.isNotEmpty() }
                val alias = norm(rest.firstOrNull() ?: continue)
                if (alias.isEmpty()) continue
                out += Triple(alias, rest.getOrNull(1)?.takeIf { it.isNotEmpty() }, cat)
            }
            return out
        }
    }
}

/** Per-user category corrections, keyed by normalised merchant text. Backed by SharedPreferences. */
interface CorrectionStore {
    fun get(key: String): TransactionCategory?
    fun put(key: String, category: TransactionCategory)
}

/** In-memory store for tests. */
class MemoryCorrectionStore : CorrectionStore {
    private val map = HashMap<String, TransactionCategory>()
    override fun get(key: String) = map[key]
    override fun put(key: String, category: TransactionCategory) { map[key] = category }
}

@Singleton
class PrefsCorrectionStore @Inject constructor(@ApplicationContext context: Context) : CorrectionStore {
    private val prefs = context.getSharedPreferences("ledgerai_merchant_corrections", Context.MODE_PRIVATE)
    override fun get(key: String): TransactionCategory? =
        prefs.getString(key, null)?.let { name -> TransactionCategory.entries.firstOrNull { it.name == name } }
    override fun put(key: String, category: TransactionCategory) {
        prefs.edit().putString(key, category.name).apply()
    }
}

/** Default lookup: assets/rules/merchants.tsv over the embedded list, with per-user corrections first. */
@Singleton
class DefaultMerchantLookup @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: PrefsCorrectionStore,
) : MerchantCategoryLookup {
    private val delegate by lazy { TsvMerchantLookup(RuleAssets.read(context, "rules/merchants.tsv").orEmpty(), store) }

    override fun lookup(merchant: String, note: String): MerchantSuggestion? = delegate.lookup(merchant, note)

    /** Call when the user saves a transaction whose category differs from the suggestion. */
    fun remember(merchant: String, category: TransactionCategory) {
        val key = TsvMerchantLookup.norm(merchant)
        if (key.length >= 2) store.put(key, category)
    }
}
