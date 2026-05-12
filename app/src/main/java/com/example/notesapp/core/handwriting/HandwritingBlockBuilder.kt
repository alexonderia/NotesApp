package com.example.notesapp.core.handwriting

import com.example.notesapp.core.model.HandwritingBlock
import com.example.notesapp.core.model.InkStroke
import com.example.notesapp.core.model.StrokeBounds
import com.example.notesapp.core.model.ToolType
import java.util.zip.CRC32
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Строит **reading order** (порядок чтения) для зеркала распознанного текста: логические строки
 * сверху вниз, внутри строки слева направо. Используется при группировке штрихов в [HandwritingBlock].
 *
 * **Абзацы:** при зазоре между строками сильно больше типичного между соседними строками одного абзаца,
 * к [orderIndex] добавляется [PARAGRAPH_ORDER_INDEX_GAP]. При сборке `recognizedText` в редакторе между такими
 * блоками вставляется `\n\n` вместо одного `\n`.
 */
object HandwritingBlockBuilder {

    /**
     * При визуальном разрыве абзаца к следующему [orderIndex] добавляется этот шаг (между строками одного абзаца шаг 1).
     */
    const val PARAGRAPH_ORDER_INDEX_GAP = 1000

    private const val DEFAULT_LINE_THRESHOLD_PX = 48f

    /** Минимальная доля вертикального пересечения относительно меньшей высоты, чтобы считать штрих частью строки. */
    private const val OVERLAP_RATIO_STRONG = 0.32f

    private const val OVERLAP_RATIO_WEAK = 0.16f

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

    /**
     * Порог «одна строка» по вертикали: сочетание средней/медианной высоты штрихов и нижней границы в px.
     */
    fun calculateLineThreshold(pairs: List<Pair<InkStroke, StrokeBounds>>): Float {
        if (pairs.isEmpty()) return DEFAULT_LINE_THRESHOLD_PX
        val heights = pairs.map { it.second.height }
        val avgH = heights.average().toFloat().coerceAtLeast(1f)
        val sortedH = heights.sorted()
        val medianH = sortedH[sortedH.size / 2].coerceAtLeast(1f)
        return max(
            DEFAULT_LINE_THRESHOLD_PX,
            max(avgH * 0.92f, medianH * 0.98f),
        )
    }

    /**
     * Детерминированный id блока: один и тот же набор [strokeIds] (в любом порядке) даёт один и тот же id.
     */
    fun buildStableBlockId(strokeIds: List<String>): String {
        val payload = strokeIds.sorted().joinToString("_").encodeToByteArray()
        val crc = CRC32()
        crc.update(payload, 0, payload.size)
        val unsigned = crc.value and 0xffff_ffffL
        return "block_${unsigned.toString(16)}"
    }

    /**
     * Группирует штрихи в строки: внутри строки порядок слева направо ([minX]), строки сверху вниз ([minY] объединённых bounds).
     *
     * Учитываются вертикальное пересечение bounds, [minY]/[maxY]/[centerY], высота штрихов и вертикальное расстояние
     * между кандидатом и уже собранной строкой. При близких по Y строках пересечение по X помогает отсечь соседние строки.
     */
    fun groupStrokesIntoLines(
        pairs: List<Pair<InkStroke, StrokeBounds>>,
    ): List<List<Pair<InkStroke, StrokeBounds>>> {
        if (pairs.isEmpty()) return emptyList()

        val threshold = calculateLineThreshold(pairs)
        val sorted = pairs.sortedWith(
            compareBy<Pair<InkStroke, StrokeBounds>> { it.second.minY }
                .thenBy { it.second.minX },
        )
        val lines = mutableListOf<StrokeLine>()

        for (pair in sorted) {
            val bounds = pair.second
            val compatible = lines.filter { belongsToLine(bounds, it, threshold) }
            val bestLine = compatible.minByOrNull { line ->
                val u = line.unionBounds()
                abs(u.centerY - bounds.centerY) + abs(u.centerX - bounds.centerX) * 0.02f
            }
            if (bestLine != null) {
                bestLine.items.add(pair)
            } else {
                lines.add(StrokeLine(mutableListOf(pair)))
            }
        }

        val sortedLines = lines.sortedBy { line ->
            line.items.minOfOrNull { it.second.minY } ?: line.centerY()
        }

        return sortedLines.map { line ->
            line.items.sortedBy { it.second.minX }
        }
    }

    fun build(strokes: List<InkStroke>): List<HandwritingBlock> {
        if (strokes.isEmpty()) return emptyList()

        val penStrokes = strokes.filter { it.toolType == ToolType.Pen && it.points.isNotEmpty() }
        val pairs = penStrokes.map { stroke -> stroke to StrokeBounds.fromStroke(stroke) }
        if (pairs.isEmpty()) return emptyList()

        val lineGroups = groupStrokesIntoLines(pairs)
        val now = System.currentTimeMillis()
        val avgStrokeHeight = pairs.map { it.second.height }.average().toFloat().coerceAtLeast(1f)
        val orderIndices = assignOrderIndicesWithParagraphGaps(lineGroups, avgStrokeHeight)

        return lineGroups.mapIndexed { i, linePairs ->
            val strokeIds = linePairs.map { it.first.id }
            val union = linePairs.map { it.second }.reduce { acc, b -> acc.merge(b) }
            HandwritingBlock(
                id = buildStableBlockId(strokeIds),
                strokeIds = strokeIds,
                bounds = union,
                recognizedText = "",
                orderIndex = orderIndices[i],
                updatedAt = now,
            )
        }
    }

    /**
     * Назначает [orderIndex] по вертикали: подряд идущие строки +1; перед строкой после **крупного** вертикального
     * зазора к индексу добавляется [PARAGRAPH_ORDER_INDEX_GAP] (без отдельного поля «абзац» в модели).
     */
    private fun assignOrderIndicesWithParagraphGaps(
        lineGroups: List<List<Pair<InkStroke, StrokeBounds>>>,
        avgStrokeHeight: Float,
    ): List<Int> {
        if (lineGroups.isEmpty()) return emptyList()
        val unions = lineGroups.map { linePairs ->
            linePairs.map { it.second }.reduce { acc, b -> acc.merge(b) }
        }
        val gaps = unions.zipWithNext { prev, next -> next.minY - prev.maxY }
        val medianLineGap = if (gaps.isEmpty()) {
            avgStrokeHeight
        } else {
            gaps.sorted().let { it[it.size / 2] }.coerceAtLeast(1f)
        }
        val paragraphThreshold = max(
            medianLineGap * 2.2f + avgStrokeHeight * 0.55f,
            avgStrokeHeight * 1.35f,
        )

        var orderIdx = 0
        return lineGroups.indices.map { i ->
            if (i > 0) {
                val gap = unions[i].minY - unions[i - 1].maxY
                if (gap > paragraphThreshold) {
                    orderIdx += PARAGRAPH_ORDER_INDEX_GAP
                }
            }
            orderIdx++
            orderIdx - 1
        }
    }

    private fun verticalOverlapDepth(a: StrokeBounds, b: StrokeBounds): Float {
        val top = max(a.minY, b.minY)
        val bottom = min(a.maxY, b.maxY)
        return (bottom - top).coerceAtLeast(0f)
    }

    private fun horizontalOverlapDepth(a: StrokeBounds, b: StrokeBounds): Float {
        val left = max(a.minX, b.minX)
        val right = min(a.maxX, b.maxX)
        return (right - left).coerceAtLeast(0f)
    }

    /**
     * true, если штрих логично отнести к уже сформированной строке (пересечение по Y / близость центров / полоса по X).
     */
    private fun belongsToLine(stroke: StrokeBounds, line: StrokeLine, threshold: Float): Boolean {
        if (line.items.isEmpty()) return false
        val union = line.unionBounds()

        val vOverlap = verticalOverlapDepth(stroke, union)
        val minH = min(stroke.height, union.height).coerceAtLeast(1f)
        val maxH = max(stroke.height, union.height).coerceAtLeast(1f)
        val ratioMin = vOverlap / minH
        val ratioMax = vOverlap / maxH

        if (ratioMin >= OVERLAP_RATIO_STRONG) return true

        val cyDelta = abs(stroke.centerY - union.centerY)
        if (cyDelta > threshold * 1.05f) return false

        if (ratioMin >= OVERLAP_RATIO_WEAK && cyDelta <= threshold * 0.75f) return true
        if (ratioMax >= 0.22f && cyDelta <= threshold * 0.85f) return true

        val hOverlap = horizontalOverlapDepth(stroke, union)
        val minW = min(stroke.width, union.width).coerceAtLeast(1f)
        if (hOverlap >= minW * 0.08f && ratioMin >= 0.1f && cyDelta <= threshold * 0.55f) return true

        // Очень близкие по центру Y и есть заметное пересечение по Y (например, точка над «i»)
        if (cyDelta <= threshold * 0.35f && vOverlap >= minH * 0.08f) return true

        return false
    }
}
