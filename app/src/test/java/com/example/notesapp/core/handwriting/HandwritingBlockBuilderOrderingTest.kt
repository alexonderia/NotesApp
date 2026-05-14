package com.example.notesapp.core.handwriting

import com.example.notesapp.core.model.InkStroke
import com.example.notesapp.core.model.StrokePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HandwritingBlockBuilderOrderingTest {

    private fun hStroke(id: String, x: Float, y: Float): InkStroke {
        val pts = listOf(
            StrokePoint(x, y, 1f, 0L),
            StrokePoint(x + 30f, y + 18f, 1f, 0L),
        )
        return InkStroke(id = id, points = pts)
    }

    @Test
    fun linesOrderedTopToBottomAndStrokesLeftToRight() {
        val topLeft = hStroke("tl", 10f, 50f)
        val topRight = hStroke("tr", 120f, 55f)
        val bottom = hStroke("b", 20f, 200f)
        val blocks = HandwritingBlockBuilder.build(listOf(bottom, topRight, topLeft))
        assertEquals(2, blocks.size)
        val topLine = blocks.minByOrNull { it.bounds.minY }!!
        val bottomLine = blocks.maxByOrNull { it.bounds.minY }!!
        assertTrue(topLine.bounds.minY < bottomLine.bounds.minY)
        assertEquals(listOf("tl", "tr"), topLine.strokeIds)
        assertEquals(listOf("b"), bottomLine.strokeIds)
    }
}
