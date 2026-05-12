package com.example.notesapp.core.handwriting

import com.example.notesapp.core.model.HandwritingBlock
import com.example.notesapp.core.model.InkStroke
import com.example.notesapp.core.model.ToolType
import com.example.notesapp.core.model.StrokeBounds
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max

/**
 * Группирует штрихи в блоки (логические строки) по координатам: сначала по Y, внутри строки по X.
 */
object HandwritingBlockBuilder {

    private const val DEFAULT_LINE_THRESHOLD_PX = 48f

    private data class StrokeLine(
        val items: MutableList<Pair<InkStroke, StrokeBounds>> = mutableListOf(),
    ) {
        fun centerY(): Float {
            if (items.isEmpty()) return 0f
            var s = 0f
            for ((_, b) in items) s += b.centerY
            return s / items.size
        }

        fun unionBounds(): StrokeBounds =
            items.map { it.second }.reduce { acc, b -> acc.merge(b) }
    }

    fun build(strokes: List<InkStroke>): List<HandwritingBlock> {
        if (strokes.isEmpty()) return emptyList()

        val penStrokes = strokes.filter { it.toolType == ToolType.Pen && it.points.isNotEmpty() }
        val pairs = penStrokes
            .map { stroke -> stroke to StrokeBounds.fromStroke(stroke) }

        if (pairs.isEmpty()) return emptyList()

        val avgHeight = pairs.map { it.second.height }.average().toFloat().coerceAtLeast(1f)
        val lineThreshold = max(DEFAULT_LINE_THRESHOLD_PX, avgHeight * 0.85f)

        val sortedByY = pairs.sortedBy { it.second.centerY }
        val lines = mutableListOf<StrokeLine>()

        for (pair in sortedByY) {
            val cy = pair.second.centerY
            val bestLine = lines.minByOrNull { abs(it.centerY() - cy) }
            if (bestLine != null && abs(bestLine.centerY() - cy) <= lineThreshold) {
                bestLine.items.add(pair)
            } else {
                lines.add(StrokeLine(mutableListOf(pair)))
            }
        }

        val sortedLines = lines.sortedBy { line ->
            line.items.minOfOrNull { it.second.minY } ?: line.centerY()
        }

        val now = System.currentTimeMillis()
        return sortedLines.mapIndexed { orderIndex, line ->
            val sortedInLine = line.items.sortedBy { it.second.minX }
            val strokeIds = sortedInLine.map { it.first.id }
            val union = line.unionBounds()
            HandwritingBlock(
                id = "block_${UUID.randomUUID()}",
                strokeIds = strokeIds,
                bounds = union,
                recognizedText = "",
                orderIndex = orderIndex,
                updatedAt = now,
            )
        }
    }
}
