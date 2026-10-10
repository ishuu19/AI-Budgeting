package com.ledgerai.app.data.receipts

import com.ledgerai.app.domain.receipts.Receipt
import com.ledgerai.app.domain.receipts.ReceiptDrafts
import com.ledgerai.app.domain.receipts.ReceiptLine
import com.ledgerai.app.domain.receipts.ValueConfidence
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ReceiptCaptureTest {

    @Test
    fun missingTotalIsNotFilledIn() {
        val receipt = Receipt(
            merchant = "Lidl",
            merchantConfidence = ValueConfidence.CONFIRMED,
            purchasedOn = LocalDate.of(2026, 10, 1),
            purchasedOnConfidence = ValueConfidence.CONFIRMED,
            total = null,
            currency = "EUR",
            lines = listOf(
                ReceiptLine(
                    rawText = "Milk",
                    qty = 2.0,
                    unitPrice = 1.5,
                    lineTotal = 3.0,
                    confidence = ValueConfidence.CONFIRMED,
                ),
                ReceiptLine(
                    rawText = "Bread",
                    qty = 1.0,
                    unitPrice = 2.0,
                    lineTotal = 2.0,
                    confidence = ValueConfidence.CONFIRMED,
                ),
            ),
        )

        val expense = ReceiptDrafts.propose(receipt).expense

        assertNull(expense.amount)
        assertNull(expense.amountConfidence)
    }

    @Test
    fun inferredFlagIsPreserved() {
        val receipt = Receipt(
            id = 4L,
            merchant = "Lidl",
            merchantConfidence = ValueConfidence.INFERRED,
            purchasedOn = LocalDate.of(2026, 10, 1),
            purchasedOnConfidence = ValueConfidence.INFERRED,
            total = 5.0,
            totalConfidence = ValueConfidence.INFERRED,
            currency = "EUR",
            lines = listOf(
                ReceiptLine(
                    id = 9L,
                    receiptId = 4L,
                    rawText = "Milk",
                    qty = 1.0,
                    unitPrice = 5.0,
                    lineTotal = 5.0,
                    confidence = ValueConfidence.INFERRED,
                ),
            ),
        )

        val proposals = ReceiptDrafts.propose(receipt)

        assertEquals(ValueConfidence.INFERRED, proposals.expense.merchantConfidence)
        assertEquals(ValueConfidence.INFERRED, proposals.expense.dateConfidence)
        assertEquals(ValueConfidence.INFERRED, proposals.expense.amountConfidence)
        assertEquals("Lidl", proposals.expense.merchant)
        assertEquals(LocalDate.of(2026, 10, 1), proposals.expense.date)
        assertEquals(5.0, proposals.expense.amount)
        assertEquals(ValueConfidence.INFERRED, proposals.stockChanges.single().confidence)
        assertEquals("receipt", proposals.expense.sourceType)
        assertEquals(4L, proposals.expense.sourceId)
        assertEquals(4L, proposals.stockChanges.single().sourceId)
        assertEquals(9L, proposals.stockChanges.single().sourceLineId)
    }

    @Test
    fun confirmDoesNotInventLinePrices() = runBlocking {
        val repo = ReceiptRepository(MemoryReceiptStore())
        val result = repo.confirm(
            Receipt(
                merchant = null,
                purchasedOn = null,
                total = null,
                currency = null,
                lines = listOf(
                    ReceiptLine(
                        rawText = "Eggs",
                        qty = null,
                        unitPrice = null,
                        lineTotal = null,
                        confidence = ValueConfidence.INFERRED,
                    ),
                ),
            ),
        )

        val line = result.receipt.lines.single()
        val stock = result.proposals.stockChanges.single()
        assertTrue(result.receipt.locallyConfirmed)
        assertNull(result.receipt.merchant)
        assertNull(result.receipt.purchasedOn)
        assertNull(result.receipt.total)
        assertNull(result.receipt.transactionId)
        assertNull(line.qty)
        assertNull(line.unitPrice)
        assertNull(line.lineTotal)
        assertEquals(ValueConfidence.INFERRED, line.confidence)
        assertNull(result.proposals.expense.amount)
        assertNull(result.proposals.expense.merchant)
        assertNull(result.proposals.expense.date)
        assertEquals("Eggs", result.proposals.expense.note)
        assertNull(stock.quantityDelta)
        assertNull(stock.unitPrice)
        assertNull(stock.lineTotal)
        assertEquals(ValueConfidence.INFERRED, stock.confidence)
        assertEquals("Eggs", stock.rawText)
        assertEquals("receipt", stock.sourceType)
        assertEquals(result.receipt.id, stock.sourceId)
        assertEquals(line.id, stock.sourceLineId)
        assertTrue(result.receipt.id != 0L)

        val stored = repo.find(result.receipt.id)
        assertNull(stored!!.lines.single().unitPrice)
        assertNull(stored.lines.single().lineTotal)
        assertEquals(ValueConfidence.INFERRED, stored.lines.single().confidence)
    }

    @Test
    fun roomRecordsKeepUnknownPricesAndInferredFlag() {
        val receipt = Receipt(
            merchant = null,
            purchasedOn = null,
            total = null,
            lines = listOf(
                ReceiptLine(
                    rawText = "Eggs",
                    qty = null,
                    unitPrice = null,
                    lineTotal = null,
                    confidence = ValueConfidence.INFERRED,
                ),
            ),
        )

        val (entity, lines) = receipt.toRecords()
        val back = entity.toDomain(lines)

        assertNull(entity.total)
        assertNull(entity.merchant)
        assertNull(entity.purchasedOn)
        assertNull(lines.single().unitPrice)
        assertNull(lines.single().lineTotal)
        assertNull(lines.single().qty)
        assertEquals("inferred", lines.single().confidence)
        assertNull(back.total)
        assertNull(back.merchant)
        assertNull(back.purchasedOn)
        assertNull(back.lines.single().unitPrice)
        assertEquals(ValueConfidence.INFERRED, back.lines.single().confidence)
    }

    @Test
    fun confirmMarksPresentMerchantDateTotalAndLinePricesConfirmed() = runBlocking {
        val repo = ReceiptRepository(MemoryReceiptStore())
        val result = repo.confirm(
            Receipt(
                merchant = "Lidl",
                merchantConfidence = ValueConfidence.INFERRED,
                purchasedOn = LocalDate.of(2026, 10, 1),
                purchasedOnConfidence = ValueConfidence.INFERRED,
                total = 5.0,
                totalConfidence = ValueConfidence.INFERRED,
                currency = "EUR",
                lines = listOf(
                    ReceiptLine(
                        rawText = "Milk",
                        qty = 1.0,
                        unitPrice = 5.0,
                        lineTotal = 5.0,
                        confidence = ValueConfidence.INFERRED,
                    ),
                ),
            ),
        )

        assertTrue(result.receipt.locallyConfirmed)
        assertEquals("Lidl", result.receipt.merchant)
        assertEquals(ValueConfidence.CONFIRMED, result.receipt.merchantConfidence)
        assertEquals(LocalDate.of(2026, 10, 1), result.receipt.purchasedOn)
        assertEquals(ValueConfidence.CONFIRMED, result.receipt.purchasedOnConfidence)
        assertEquals(5.0, result.receipt.total)
        assertEquals(ValueConfidence.CONFIRMED, result.receipt.totalConfidence)
        val line = result.receipt.lines.single()
        assertEquals(5.0, line.unitPrice)
        assertEquals(5.0, line.lineTotal)
        assertEquals(ValueConfidence.CONFIRMED, line.confidence)
        assertEquals(ValueConfidence.CONFIRMED, result.proposals.expense.merchantConfidence)
        assertEquals(ValueConfidence.CONFIRMED, result.proposals.expense.dateConfidence)
        assertEquals(ValueConfidence.CONFIRMED, result.proposals.expense.amountConfidence)
        assertEquals(5.0, result.proposals.expense.amount)
        assertEquals(5.0, result.proposals.stockChanges.single().unitPrice)
        assertEquals(5.0, result.proposals.stockChanges.single().lineTotal)
        assertEquals(ValueConfidence.CONFIRMED, result.proposals.stockChanges.single().confidence)

        val stored = repo.find(result.receipt.id)!!
        assertEquals(ValueConfidence.CONFIRMED, stored.merchantConfidence)
        assertEquals(ValueConfidence.CONFIRMED, stored.purchasedOnConfidence)
        assertEquals(ValueConfidence.CONFIRMED, stored.totalConfidence)
        assertEquals(ValueConfidence.CONFIRMED, stored.lines.single().confidence)
        assertEquals(5.0, stored.lines.single().unitPrice)
        assertEquals(5.0, stored.lines.single().lineTotal)
    }

    @Test
    fun nullLinePricesStayNullAfterConfirmWhenTotalIsKnown() = runBlocking {
        val repo = ReceiptRepository(MemoryReceiptStore())
        val result = repo.confirm(
            Receipt(
                merchant = "Lidl",
                merchantConfidence = ValueConfidence.CONFIRMED,
                purchasedOn = LocalDate.of(2026, 10, 1),
                purchasedOnConfidence = ValueConfidence.CONFIRMED,
                total = 12.5,
                totalConfidence = ValueConfidence.CONFIRMED,
                lines = listOf(
                    ReceiptLine(
                        rawText = "Eggs",
                        qty = 1.0,
                        unitPrice = null,
                        lineTotal = null,
                        confidence = ValueConfidence.INFERRED,
                    ),
                ),
            ),
        )

        val line = result.receipt.lines.single()
        val stock = result.proposals.stockChanges.single()
        assertEquals(12.5, result.receipt.total)
        assertEquals(ValueConfidence.CONFIRMED, result.receipt.totalConfidence)
        assertNull(line.unitPrice)
        assertNull(line.lineTotal)
        assertEquals(ValueConfidence.INFERRED, line.confidence)
        assertEquals(12.5, result.proposals.expense.amount)
        assertNull(stock.unitPrice)
        assertNull(stock.lineTotal)
        assertEquals(ValueConfidence.INFERRED, stock.confidence)

        val stored = repo.find(result.receipt.id)!!
        assertEquals(12.5, stored.total)
        assertNull(stored.lines.single().unitPrice)
        assertNull(stored.lines.single().lineTotal)
    }

    @Test
    fun confirmKeepsUserEnteredLinePriceAndLeavesOtherPricesNull() = runBlocking {
        val repo = ReceiptRepository(MemoryReceiptStore())
        val result = repo.confirm(
            Receipt(
                merchant = "Lidl",
                merchantConfidence = ValueConfidence.INFERRED,
                total = null,
                lines = listOf(
                    ReceiptLine(
                        rawText = "Eggs",
                        qty = 2.0,
                        unitPrice = 1.25,
                        lineTotal = 2.5,
                        confidence = ValueConfidence.CONFIRMED,
                    ),
                    ReceiptLine(
                        rawText = "Bread",
                        qty = null,
                        unitPrice = null,
                        lineTotal = null,
                        confidence = ValueConfidence.INFERRED,
                    ),
                ),
            ),
        )

        val eggs = result.receipt.lines.single { it.rawText == "Eggs" }
        val bread = result.receipt.lines.single { it.rawText == "Bread" }
        assertEquals(1.25, eggs.unitPrice)
        assertEquals(2.5, eggs.lineTotal)
        assertEquals(2.0, eggs.qty)
        assertEquals(ValueConfidence.CONFIRMED, eggs.confidence)
        assertNull(bread.qty)
        assertNull(bread.unitPrice)
        assertNull(bread.lineTotal)
        assertEquals(ValueConfidence.INFERRED, bread.confidence)
        assertNull(result.receipt.total)
        assertNull(result.proposals.expense.amount)
        assertNull(result.proposals.expense.amountConfidence)

        val eggStock = result.proposals.stockChanges.single { it.rawText == "Eggs" }
        val breadStock = result.proposals.stockChanges.single { it.rawText == "Bread" }
        assertEquals(1.25, eggStock.unitPrice)
        assertEquals(2.5, eggStock.lineTotal)
        assertEquals(ValueConfidence.CONFIRMED, eggStock.confidence)
        assertNull(breadStock.quantityDelta)
        assertNull(breadStock.unitPrice)
        assertNull(breadStock.lineTotal)
        assertEquals(ValueConfidence.INFERRED, breadStock.confidence)

        val stored = repo.find(result.receipt.id)!!
        assertEquals(1.25, stored.lines.single { it.rawText == "Eggs" }.unitPrice)
        assertEquals(2.5, stored.lines.single { it.rawText == "Eggs" }.lineTotal)
        assertEquals(ValueConfidence.CONFIRMED, stored.lines.single { it.rawText == "Eggs" }.confidence)
        assertNull(stored.lines.single { it.rawText == "Bread" }.unitPrice)
        assertNull(stored.lines.single { it.rawText == "Bread" }.lineTotal)
        assertNull(stored.total)
    }
}

private class MemoryReceiptStore : ReceiptStore {
    private val rows = mutableMapOf<Long, Receipt>()
    private var nextReceiptId = 1L
    private var nextLineId = 1L

    override suspend fun upsert(receipt: Receipt): Receipt {
        val id = if (receipt.id == 0L) nextReceiptId++ else receipt.id
        val lines = receipt.lines.map { line ->
            val lineId = if (line.id == 0L) nextLineId++ else line.id
            line.copy(id = lineId, receiptId = id)
        }
        val saved = receipt.copy(id = id, lines = lines)
        rows[id] = saved
        return saved
    }

    override suspend fun find(id: Long): Receipt? = rows[id]
}
