package com.ledgerai.app.domain.notebook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenBlocksTest {

    @Test
    fun dropsPasswordAndShortNodesAndSortsReadingOrder() {
        val nodes = listOf(
            ScreenNode(text = "secret12", password = true, top = 0, left = 0),
            ScreenNode(text = "x", top = 4, left = 0),
            ScreenNode(text = "Nearest station", top = 200, left = 8),
            ScreenNode(text = "Opens", top = 10, left = 90),
            ScreenNode(text = "Park", top = 14, left = 4),
            ScreenNode(text = null, contentDescription = "  ", top = 40, left = 0),
        )

        val blocks = ScreenBlocks.fromNodes(nodes)

        assertEquals(listOf("Park Opens", "Nearest station"), blocks.map { it.text })
        assertTrue(blocks[0].top < blocks[1].top)
        assertFalse(blocks.any { it.text.contains("secret") })
    }

    @Test
    fun usesContentDescriptionWhenTextIsMissing() {
        val blocks = ScreenBlocks.fromNodes(
            listOf(ScreenNode(text = null, contentDescription = "Hours", top = 3, left = 2))
        )
        assertEquals("Hours", blocks.single().text)
    }

    @Test
    fun passwordInputTypeIsDroppedByTheFlag() {
        assertTrue(ScreenBlocks.isPasswordInput(0x00000081))
        assertFalse(ScreenBlocks.isPasswordInput(0x00000001))
    }

    @Test
    fun capsAtTwoHundredAndPreviewShowsThreeUntilExpanded() {
        val nodes = (0 until 240).map { ScreenNode(text = "Line $it", top = it * 40, left = 0) }
        val blocks = ScreenBlocks.fromNodes(nodes)
        assertEquals(200, blocks.size)
        assertEquals(3, ScreenBlocks.visible(blocks, expanded = false).size)
        assertEquals(200, ScreenBlocks.visible(blocks, expanded = true).size)
    }

    @Test
    fun ocrLinesBecomeBlocksInOrder() {
        val blocks = ScreenBlocks.fromOcr(
            listOf(
                OcrLine("Game over", top = 80, left = 10),
                OcrLine("Score 12", top = 10, left = 10),
            )
        )
        assertEquals(listOf("Score 12", "Game over"), blocks.map { it.text })
    }

    @Test
    fun wordsSplitABlock() {
        assertEquals(listOf("Entry", "¥500."), ScreenBlocks.words("Entry ¥500."))
    }
}
