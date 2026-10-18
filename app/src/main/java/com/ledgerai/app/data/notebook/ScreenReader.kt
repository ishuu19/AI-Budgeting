package com.ledgerai.app.data.notebook

import android.graphics.Bitmap
import android.view.inputmethod.EditorInfo
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.ledgerai.app.domain.notebook.OcrLine
import com.ledgerai.app.domain.notebook.ScreenBlock
import com.ledgerai.app.domain.notebook.ScreenBlocks
import com.ledgerai.app.domain.notebook.ScreenNode
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/** Assist text first. OCR stays on the phone and nothing is uploaded. */
object ScreenReader {
    fun nodes(structure: android.app.assist.AssistStructure?): List<ScreenNode> {
        if (structure == null) return emptyList()
        val out = mutableListOf<ScreenNode>()
        for (i in 0 until structure.windowNodeCount) {
            val root = structure.getWindowNodeAt(i)?.rootViewNode ?: continue
            walk(root, 0, 0, out)
        }
        return out
    }

    fun blocks(structure: android.app.assist.AssistStructure?): List<ScreenBlock> =
        ScreenBlocks.fromNodes(nodes(structure))

    fun label(structure: android.app.assist.AssistStructure?): String? {
        if (structure == null) return null
        val title = (0 until structure.windowNodeCount).firstNotNullOfOrNull { index ->
            structure.getWindowNodeAt(index)?.title?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        }
        val pkg = structure.activityComponent?.packageName?.substringAfterLast('.')?.takeIf { it.isNotBlank() }
        return title ?: pkg
    }

    suspend fun ocr(bitmap: Bitmap): List<ScreenBlock> = suspendCancellableCoroutine { cont ->
        val image = InputImage.fromBitmap(bitmap, 0)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        recognizer.process(image)
            .addOnSuccessListener { result ->
                recognizer.close()
                val lines = result.textBlocks.flatMap { block ->
                    block.lines.map { line ->
                        val box = line.boundingBox
                        OcrLine(line.text, box?.top ?: 0, box?.left ?: 0)
                    }
                }
                if (cont.isActive) cont.resume(ScreenBlocks.fromOcr(lines))
            }
            .addOnFailureListener {
                recognizer.close()
                if (cont.isActive) cont.resume(emptyList())
            }
    }

    private fun walk(
        node: android.app.assist.AssistStructure.ViewNode,
        parentLeft: Int,
        parentTop: Int,
        out: MutableList<ScreenNode>,
    ) {
        val left = parentLeft + node.left
        val top = parentTop + node.top
        val input = runCatching { node.inputType }.getOrDefault(EditorInfo.TYPE_NULL)
        out += ScreenNode(
            text = node.text?.toString(),
            contentDescription = node.contentDescription?.toString(),
            password = ScreenBlocks.isPasswordInput(input),
            top = top,
            left = left,
        )
        for (i in 0 until node.childCount) {
            val child = node.getChildAt(i) ?: continue
            walk(child, left, top, out)
        }
    }
}
