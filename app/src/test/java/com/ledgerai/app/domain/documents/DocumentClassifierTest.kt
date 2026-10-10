package com.ledgerai.app.domain.documents

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Breaks if the classifier guesses a kind the text does not support,
 * or reports a rule guess as something the user confirmed.
 */
class DocumentClassifierTest {

    @Test
    fun classify_blank_isUnknown() {
        assertEquals(DocumentLabel.UNKNOWN, DocumentClassifier.classify("").label)
        assertEquals(DocumentLabel.UNKNOWN, DocumentClassifier.classify("   \n\t").label)
    }

    @Test
    fun classify_receiptHeading_isReceipt() {
        val text = "Corner Shop\nReceipt\nMilk"
        assertEquals(DocumentLabel.RECEIPT, DocumentClassifier.classify(text).label)
    }

    @Test
    fun classify_totalWithCents_isReceipt() {
        val text = "SUBTOTAL 10.00\nTAX 0.80\nTOTAL 10.80"
        assertEquals(DocumentLabel.RECEIPT, DocumentClassifier.classify(text).label)
    }

    @Test
    fun classify_currencyTotal_isReceipt() {
        assertEquals(DocumentLabel.RECEIPT, DocumentClassifier.classify("Grand total €4.20").label)
    }

    @Test
    fun classify_bareAmount_isUnknown() {
        assertEquals(DocumentLabel.UNKNOWN, DocumentClassifier.classify("18.40").label)
    }

    @Test
    fun classify_totalWithoutMoney_isUnknown() {
        assertEquals(DocumentLabel.UNKNOWN, DocumentClassifier.classify("Total").label)
        assertEquals(DocumentLabel.UNKNOWN, DocumentClassifier.classify("Total 12 items").label)
    }

    @Test
    fun classify_invoice_isOther() {
        val text = "Invoice 1042\nAmount due \$40.00\nPlease pay within 14 days."
        assertEquals(DocumentLabel.OTHER, DocumentClassifier.classify(text).label)
    }

    @Test
    fun classify_receiptAndInvoiceTogether_isUnknown() {
        val text = "This invoice is not a receipt. Total \$10.00"
        assertEquals(DocumentLabel.UNKNOWN, DocumentClassifier.classify(text).label)
    }

    @Test
    fun classify_prose_isOther() {
        val text = "Notes from the dentist visit. Remember to book a follow-up next week."
        assertEquals(DocumentLabel.OTHER, DocumentClassifier.classify(text).label)
    }

    @Test
    fun classify_jobListingWithSalary_isOther() {
        val text = "Software Engineer. We offer a salary of \$120,000 per year. Apply by November."
        assertEquals(DocumentLabel.OTHER, DocumentClassifier.classify(text).label)
    }

    @Test
    fun classify_shortFragment_isUnknown() {
        assertEquals(DocumentLabel.UNKNOWN, DocumentClassifier.classify("hello").label)
    }

    @Test
    fun classify_keepsSourceTextAndMarksGuessInferred() {
        val text = "Corner Shop\nReceipt"
        val draft = DocumentClassifier.classify(text)
        assertEquals(text, draft.sourceText)
        assertEquals(FieldConfidence.INFERRED, draft.confidence)
        assertFalse(draft.reportsAmount)
        assertFalse(draft.reportsDate)
    }
}
