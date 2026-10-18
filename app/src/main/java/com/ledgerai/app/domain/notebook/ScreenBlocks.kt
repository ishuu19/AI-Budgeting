package com.ledgerai.app.domain.notebook

/** One node from an assist tree or an OCR line. Password nodes are dropped. */
data class ScreenNode(
    val text: String? = null,
    val contentDescription: String? = null,
    val password: Boolean = false,
    val top: Int = 0,
    val left: Int = 0,
)

data class OcrLine(val text: String, val top: Int, val left: Int)

/** A paragraph the panel can select. Order is top to bottom, then left to right. */
data class ScreenBlock(val text: String, val top: Int, val left: Int)

object ScreenBlocks {
    const val MAX = 200
    const val PREVIEW = 3
    private const val SAME_LINE = 24

    private const val VARIATION_MASK = 0x00000ff0
    private val PASSWORD_VARIATIONS = setOf(0x00000080, 0x00000090, 0x000000e0, 0x00000010)

    fun isPasswordInput(inputType: Int): Boolean =
        (inputType and VARIATION_MASK) in PASSWORD_VARIATIONS

    fun fromNodes(nodes: List<ScreenNode>): List<ScreenBlock> {
        val kept = nodes.mapNotNull { node ->
            if (node.password) return@mapNotNull null
            val text = visible(node) ?: return@mapNotNull null
            node to text
        }.sortedWith(compareBy({ it.first.top }, { it.first.left }))
        if (kept.isEmpty()) return emptyList()
        val lines = mutableListOf<MutableList<Pair<ScreenNode, String>>>()
        kept.forEach { item ->
            val line = lines.lastOrNull()
            val anchor = line?.firstOrNull()?.first?.top
            if (line != null && anchor != null && item.first.top - anchor <= SAME_LINE) {
                line += item
            } else {
                lines += mutableListOf(item)
            }
        }
        return lines.map { line ->
            val ordered = line.sortedBy { it.first.left }
            ScreenBlock(
                text = ordered.joinToString(" ") { it.second },
                top = ordered.first().first.top,
                left = ordered.first().first.left,
            )
        }.take(MAX)
    }

    fun fromOcr(lines: List<OcrLine>): List<ScreenBlock> =
        fromNodes(lines.map { ScreenNode(text = it.text, top = it.top, left = it.left) })

    fun visible(blocks: List<ScreenBlock>, expanded: Boolean): List<ScreenBlock> =
        if (expanded) blocks else blocks.take(PREVIEW)

    fun words(block: String): List<String> =
        block.split(Regex("\\s+")).filter { it.isNotBlank() }

    private fun visible(node: ScreenNode): String? {
        val text = node.text?.trim().orEmpty()
        if (text.length >= 2) return text
        val described = node.contentDescription?.trim().orEmpty()
        return described.takeIf { it.length >= 2 }
    }
}
