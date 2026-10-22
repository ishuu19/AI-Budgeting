package com.ledgerai.app.domain.assistant

import org.junit.Assert.assertEquals
import org.junit.Test

class ContextTextTest {
    @Test
    fun anEmptySectionSaysNoneSoTheModelDoesNotGuess() {
        assertEquals("Jobs: none", contextSection("Jobs", emptyList(), 5))
    }

    @Test
    fun aLongSectionSaysHowManyWereLeftOut() {
        val text = contextSection("Pantry", listOf("a", "b", "c"), 2)
        assertEquals("Pantry (3 total, first 2 shown):\na\nb", text)
    }

    @Test
    fun aShortSectionListsEverything() {
        assertEquals("Wishes (1):\n[x] bike", contextSection("Wishes", listOf("[x] bike"), 5))
    }

    @Test
    fun keywordsKeepOnlyMatchingRecordsIgnoringCase() {
        val lines = listOf("[1] Milk | 2 l", "[2] Eggs | 6", "[3] Bread\n    memory: likes EGGS")
        assertEquals(listOf("[2] Eggs | 6", "[3] Bread\n    memory: likes EGGS"), matching(lines, listOf("eggs")))
        assertEquals(lines, matching(lines, emptyList()))
    }

    @Test
    fun oneTypoStillFindsTheRecordButShortWordsStayExact() {
        assertEquals(true, fuzzyContains("[4] Sam | 14 Mar", "siam"))
        assertEquals(true, fuzzyContains("Ana Pérez", "anna"))
        assertEquals(false, fuzzyContains("[4] Sam | 14 Mar", "sim"))
        assertEquals(false, fuzzyContains("[4] Samuel | 14 Mar", "siam"))
    }

    @Test
    fun freeTextIsFlattenedAndCapped() {
        assertEquals("a b c", oneLine("a\n  b\tc", 20))
        assertEquals("abcdefg...", oneLine("abcdefghijklmnop", 10))
        assertEquals("", oneLine(null, 10))
    }
}
