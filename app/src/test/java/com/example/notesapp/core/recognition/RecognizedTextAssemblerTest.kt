package com.example.notesapp.core.recognition

import com.example.notesapp.core.handwriting.HandwritingBlockBuilder
import com.example.notesapp.core.model.HandwritingBlock
import com.example.notesapp.core.model.StrokeBounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecognizedTextAssemblerTest {

    @Test
    fun assemblesMultipleBlocksInOrder() {
        val blocks = listOf(
            block("1", listOf("a"), "First", 0),
            block("2", listOf("b"), "Second", 1),
        )
        val text = RecognizedTextAssembler.assembleRecognizedText(blocks)
        assertEquals("First\nSecond", text)
    }

    @Test
    fun singleBlockHasNoExtraNoise() {
        val blocks = listOf(
            block("1", listOf("x"), "One", 0),
        )
        assertEquals("One", RecognizedTextAssembler.assembleRecognizedText(blocks))
    }

    @Test
    fun paragraphGapInsertsBlankLine() {
        val blocks = listOf(
            block("1", listOf("a"), "Line1", 0),
            block("2", listOf("b"), "Line2", HandwritingBlockBuilder.PARAGRAPH_ORDER_INDEX_GAP + 1),
        )
        val text = RecognizedTextAssembler.assembleRecognizedText(blocks)
        assertTrue(text.contains("\n\n"))
    }

    private fun block(
        id: String,
        strokeIds: List<String>,
        text: String,
        order: Int,
    ): HandwritingBlock =
        HandwritingBlock(
            id = id,
            strokeIds = strokeIds,
            bounds = StrokeBounds(0f, 0f, 10f, 10f),
            recognizedText = text,
            orderIndex = order,
            updatedAt = 0L,
        )
}
