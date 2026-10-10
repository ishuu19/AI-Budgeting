package com.ledgerai.app.domain.documents

/** Kind guessed from text. Unknown means the text does not support a label. */
enum class DocumentLabel {
    UNKNOWN,
    RECEIPT,
    OTHER,
}

/** A rule guess is inferred. Confirmed is reserved for a value the user accepted. */
enum class FieldConfidence {
    CONFIRMED,
    INFERRED,
}

data class DocumentDraft(
    val label: DocumentLabel,
    val sourceText: String,
    val confidence: FieldConfidence,
    val reportsAmount: Boolean,
    val reportsDate: Boolean,
)

object DocumentClassifier {
    private val receiptWord = Regex("\\breceipt\\b", RegexOption.IGNORE_CASE)
    private val invoiceWord = Regex("\\binvoice\\b", RegexOption.IGNORE_CASE)
    private val totalWord = Regex("\\b(?:subtotal|grand\\s+total|total)\\b", RegexOption.IGNORE_CASE)
    private val money = Regex(
        """(?<!\d)(?:[$€£]\s*)?\d{1,3}(?:,\d{3})+(?:\.\d{2})?(?!\d)|(?<!\d)(?:[$€£]\s*)?\d+\.\d{2}(?!\d)""",
    )
    private val sentenceEnd = Regex("""[A-Za-z][.!?](?:\s|$)""")

    fun classify(text: String): DocumentDraft {
        val label = if (text.isBlank()) DocumentLabel.UNKNOWN else labelOf(text)
        return DocumentDraft(
            label = label,
            sourceText = text,
            confidence = FieldConfidence.INFERRED,
            reportsAmount = false,
            reportsDate = false,
        )
    }

    private fun labelOf(text: String): DocumentLabel {
        val receipt = receiptWord.containsMatchIn(text) ||
            (totalWord.containsMatchIn(text) && money.containsMatchIn(text))
        val invoice = invoiceWord.containsMatchIn(text)
        return when {
            receipt && invoice -> DocumentLabel.UNKNOWN
            receipt -> DocumentLabel.RECEIPT
            invoice -> DocumentLabel.OTHER
            sentenceEnd.containsMatchIn(text) -> DocumentLabel.OTHER
            else -> DocumentLabel.UNKNOWN
        }
    }
}
