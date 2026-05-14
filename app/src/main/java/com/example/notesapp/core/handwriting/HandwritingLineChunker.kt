package com.example.notesapp.core.handwriting

import com.example.notesapp.core.model.InkStroke
import com.example.notesapp.core.model.StrokeBounds
import kotlin.math.max
import kotlin.math.min

/**
 * Делит штрихи **одной строки** на горизонтальные фрагменты для устойчивого Digital Ink
 * (слишком широкая строка или слишком много штрихов).
 */
object HandwritingLineChunker {

    /** Доля ширины страницы (контента), после которой строка считается «широкой». */
    const val WIDE_LINE_TO_PAGE_RATIO = 0.82f

    /** Максимум штрихов в одном запросе к ML Kit без попытки разбиения (меньше резать строку посередине слова). */
    const val MAX_STROKES_PER_BLOCK = 88

    private const val MIN_GAP_PX = 20f

    fun unionBounds(strokes: List<InkStroke>): StrokeBounds {
        if (strokes.isEmpty()) {
            return StrokeBounds(0f, 0f, 0f, 0f)
        }
        return strokes.map { StrokeBounds.fromStroke(it) }.reduce { a, b -> a.merge(b) }
    }

    fun needsChunking(
        strokes: List<InkStroke>,
        lineBounds: StrokeBounds,
        pageContentWidth: Float,
        medianStrokeHeight: Float,
    ): Boolean {
        if (strokes.isEmpty()) return false
        val pw = pageContentWidth.coerceAtLeast(1f)
        val mh = medianStrokeHeight.coerceAtLeast(1f)
        val wide = lineBounds.width >= pw * WIDE_LINE_TO_PAGE_RATIO
        val manyStrokes = strokes.size > MAX_STROKES_PER_BLOCK
        val hugeBox = lineBounds.width * lineBounds.height > pw * mh * 26f
        return wide || manyStrokes || hugeBox
    }

    /**
     * Разбивает штрихи строки на последовательные группы слева направо.
     * @param force если true — разбиение даже при небольшой ширине (fallback после пустого ответа).
     */
    fun splitIntoHorizontalChunks(
        strokes: List<InkStroke>,
        pageContentWidth: Float,
        medianStrokeHeight: Float,
        force: Boolean = false,
    ): List<List<InkStroke>> {
        if (strokes.isEmpty()) return emptyList()
        if (strokes.size == 1) return listOf(strokes)

        val sorted = strokes.sortedBy { StrokeBounds.fromStroke(it).minX }
        val lineBounds = unionBounds(sorted)
        val pw = pageContentWidth.coerceAtLeast(1f)
        val mh = medianStrokeHeight.coerceAtLeast(1f)
        // Больше порог по X-gap — разрезаем чаще только там, где похоже на пробел между словами.
        val gapThreshold = max(MIN_GAP_PX, mh * 0.58f)

        if (!force && !needsChunking(sorted, lineBounds, pw, mh)) {
            return listOf(sorted)
        }

        val chunks = mutableListOf<MutableList<InkStroke>>()
        var bucket = mutableListOf<InkStroke>()
        var bucketBounds: StrokeBounds? = null

        fun flush() {
            if (bucket.isNotEmpty()) {
                chunks.add(bucket)
                bucket = mutableListOf()
                bucketBounds = null
            }
        }

        for (stroke in sorted) {
            val b = StrokeBounds.fromStroke(stroke)
            if (bucket.isEmpty()) {
                bucket.add(stroke)
                bucketBounds = b
                continue
            }
            val ub = bucketBounds!!
            val gap = b.minX - ub.maxX
            val merged = ub.merge(b)
            val tooWide = merged.width > pw * 0.88f
            val tooMany = bucket.size >= MAX_STROKES_PER_BLOCK
            val gapBreak = bucket.size >= 2 && gap >= gapThreshold && (tooWide || force)

            if ((tooWide && bucket.size >= 2) || tooMany || gapBreak) {
                flush()
            }
            bucket.add(stroke)
            bucketBounds = if (bucketBounds == null) b else bucketBounds!!.merge(b)
        }
        flush()

        if (chunks.isEmpty()) return listOf(sorted)

        if (chunks.size == 1 && sorted.size > 1) {
            val stillHeavy = force ||
                needsChunking(sorted, lineBounds, pw, mh)
            if (stillHeavy) {
                return splitEvenChunks(sorted, targetGroups = min(3, sorted.size))
            }
        }
        return chunks
    }

    /** Равные части по числу штрихов — последний fallback. */
    fun splitEvenChunks(strokes: List<InkStroke>, targetGroups: Int): List<List<InkStroke>> {
        if (strokes.isEmpty()) return emptyList()
        val n = strokes.size
        val g = targetGroups.coerceIn(2, n)
        val base = n / g
        val extra = n % g
        val out = ArrayList<List<InkStroke>>(g)
        var idx = 0
        repeat(g) { gi ->
            val take = base + if (gi < extra) 1 else 0
            if (take > 0) {
                out.add(strokes.subList(idx, idx + take))
                idx += take
            }
        }
        return out.filter { it.isNotEmpty() }
    }

    /**
     * Склеивает тексты горизонтальных кусков одной строки; убирает простые пересечения суффикс/префикс.
     */
    fun mergeChunkTexts(parts: List<String>): String {
        val cleaned = parts.map { it.trim() }.filter { it.isNotEmpty() }
        if (cleaned.isEmpty()) return ""
        if (cleaned.size == 1) return cleaned[0]
        var acc = cleaned[0]
        for (i in 1 until cleaned.size) {
            acc = mergeOverlappingAdjacent(acc, cleaned[i])
        }
        return acc
    }

    /**
     * Склейка двух фрагментов одной строки. Длинные совпадения суффикс/префикс часто ошибочны для кириллицы
     * и съедают конец слова — поэтому overlap ограничен и не меньше 2 символов.
     */
    private fun mergeOverlappingAdjacent(left: String, right: String): String {
        val l = left.trimEnd()
        val r = right.trimStart()
        if (l.isEmpty()) return r
        if (r.isEmpty()) return l
        val maxOverlap = minOf(l.length, r.length, 10)
        for (len in maxOverlap downTo 3) {
            if (!l.endsWith(r.take(len))) continue
            val rest = r.drop(len)
            if (rest.isEmpty()) return l
            return l + rest
        }
        return "$l $r"
    }
}
