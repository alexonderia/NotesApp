package com.example.notesapp.core.handwriting

import com.example.notesapp.core.model.InkStroke
import com.example.notesapp.core.model.StrokePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HandwritingLineChunkerTest {

    private fun stroke(id: String, minX: Float, maxX: Float, y: Float = 10f): InkStroke {
        val pts = listOf(
            StrokePoint(minX, y, 1f, 0L),
            StrokePoint(maxX, y + 20f, 1f, 0L),
        )
        return InkStroke(id = id, points = pts)
    }

    @Test
    fun sortsStrokesLeftToRightWithinLine() {
        val s1 = stroke("a", 200f, 220f)
        val s2 = stroke("b", 0f, 40f)
        val chunks = HandwritingLineChunker.splitIntoHorizontalChunks(
            strokes = listOf(s1, s2),
            pageContentWidth = 400f,
            medianStrokeHeight = 24f,
            force = true,
        )
        assertTrue(chunks.isNotEmpty())
        val flat = chunks.flatten()
        assertEquals(listOf("b", "a"), flat.map { it.id })
    }

    @Test
    fun splitEvenChunksCoversAllStrokes() {
        val strokes = (0 until 9).map { i -> stroke("s$i", i * 30f, i * 30f + 20f) }
        val parts = HandwritingLineChunker.splitEvenChunks(strokes, targetGroups = 3)
        assertEquals(3, parts.size)
        assertEquals(9, parts.sumOf { it.size })
    }

    @Test
    fun mergeChunkTextsJoinsOverlappingContinuation() {
        val merged = HandwritingLineChunker.mergeChunkTexts(listOf("hello wor", "world"))
        assertEquals("hello world", merged)
    }
}
