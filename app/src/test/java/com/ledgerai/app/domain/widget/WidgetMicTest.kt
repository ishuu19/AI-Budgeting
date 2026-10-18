package com.ledgerai.app.domain.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetMicTest {

    @Test
    fun blankSpeechIsDropped() {
        assertNull(widgetMicTranscript(""))
        assertNull(widgetMicTranscript("   "))
    }

    @Test
    fun speechIsTrimmedForCategorization() {
        assertEquals("coffee 4 dollars", widgetMicTranscript("  coffee 4 dollars  "))
    }
}
