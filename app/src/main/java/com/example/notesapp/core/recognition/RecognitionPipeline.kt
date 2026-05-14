package com.example.notesapp.core.recognition

import android.util.Log
import com.example.notesapp.BuildConfig
import com.example.notesapp.core.handwriting.HandwritingBlockBuilder
import com.example.notesapp.core.handwriting.HandwritingLineChunker
import com.example.notesapp.core.model.BlockRecognitionStatus
import com.example.notesapp.core.model.HandwritingBlock
import com.example.notesapp.core.model.InkStroke
import com.example.notesapp.core.model.StrokeBounds
import com.example.notesapp.core.model.penStrokesOnly

/**
 * Единый сценарий: штрихи страницы → строки/блоки → Digital Ink по блокам → сборка текста без append-дублей.
 */
class RecognitionPipeline(
    private val recognitionService: HandwritingRecognitionService,
) {

    data class Result(
        val assembledText: String,
        val blocks: List<HandwritingBlock>,
        /** Нет осмысленного текста — не затираем предыдущую заметку без предупреждения. */
        val isEffectivelyEmpty: Boolean,
        val hadBlockFailures: Boolean,
        val usedHorizontalChunking: Boolean,
        val strokeCount: Int,
    )

    suspend fun run(
        penStrokes: List<InkStroke>,
        previousBlocks: List<HandwritingBlock>,
        languageMode: RecognitionLanguageMode,
    ): Result {
        val dbg = BuildConfig.DEBUG

        fun d(msg: () -> String) {
            if (dbg) Log.d(DEBUG_TAG, msg())
        }

        val filtered = penStrokes.penStrokesOnly()
        if (filtered.isEmpty()) {
            d { "no pen strokes" }
            return Result(
                assembledText = "",
                blocks = emptyList(),
                isEffectivelyEmpty = true,
                hadBlockFailures = false,
                usedHorizontalChunking = false,
                strokeCount = 0,
            )
        }

        val pageBounds = filtered
            .map { StrokeBounds.fromStroke(it) }
            .reduce { a, b -> a.merge(b) }
        val pageWidth = pageBounds.width.coerceAtLeast(1f)
        val heights = filtered.map { StrokeBounds.fromStroke(it).height }.sorted()
        val medianStrokeHeight = heights[heights.size / 2].coerceAtLeast(1f)

        d {
            "start: strokes=${filtered.size} pageBounds=" +
                "[${pageBounds.minX.toInt()},${pageBounds.minY.toInt()}.." +
                "${pageBounds.maxX.toInt()},${pageBounds.maxY.toInt()}] " +
                "pageW=${pageWidth.toInt()} medianH=${"%.1f".format(medianStrokeHeight)}"
        }

        val templateBlocks = HandwritingBlockBuilder.build(filtered)
        if (templateBlocks.isEmpty()) {
            return Result("", emptyList(), true, false, false, filtered.size)
        }

        d { "blocks built: count=${templateBlocks.size}" }

        val strokeById = filtered.associateBy { it.id }
        val previousByStrokeSet = previousBlocks.associateBy { it.strokeIds.toSet() }
        val now = System.currentTimeMillis()

        var usedHorizontalChunking = false
        var hadFailures = false
        val contextTail = StringBuilder()

        val finalized = ArrayList<HandwritingBlock>(templateBlocks.size)

        for (template in templateBlocks.sortedBy { it.orderIndex }) {
            val blockStrokes = template.strokeIds.mapNotNull { strokeById[it] }.penStrokesOnly()
            d {
                "block id=${template.id} order=${template.orderIndex} strokes=${blockStrokes.size} " +
                    "bounds=[${template.bounds.minX.toInt()},${template.bounds.minY.toInt()}.." +
                    "${template.bounds.maxX.toInt()},${template.bounds.maxY.toInt()}]"
            }

            if (blockStrokes.isEmpty()) {
                finalized.add(
                    template.copy(
                        recognizedText = "",
                        recognitionCandidates = emptyList(),
                        recognitionStatus = BlockRecognitionStatus.Failed,
                        updatedAt = now,
                    ),
                )
                hadFailures = true
                continue
            }

            val strokeSet = template.strokeIds.toSet()
            val reusable = previousByStrokeSet[strokeSet]
                ?.takeIf {
                    it.recognizedText.isNotBlank() &&
                        it.recognitionStatus != BlockRecognitionStatus.Failed
                }

            if (reusable != null) {
                val copy = template.copy(
                    recognizedText = reusable.recognizedText,
                    recognitionCandidates = reusable.recognitionCandidates,
                    recognitionStatus = BlockRecognitionStatus.Reused,
                    updatedAt = now,
                )
                finalized.add(copy)
                appendContextTail(contextTail, copy.recognizedText)
                d { "  reused text len=${copy.recognizedText.length}" }
                continue
            }

            val preContext = contextTail.toString().takeLast(PRE_CONTEXT_MAX_CHARS)
            var chunkingUsedForBlock = false
            var chunks = HandwritingLineChunker.splitIntoHorizontalChunks(
                blockStrokes,
                pageContentWidth = pageWidth,
                medianStrokeHeight = medianStrokeHeight,
                force = false,
            )
            if (chunks.size > 1) {
                usedHorizontalChunking = true
                chunkingUsedForBlock = true
            }

            val firstPass = recognizeChunkList(
                chunks = chunks,
                preContextStart = preContext,
                languageMode = languageMode,
            )
            var text = firstPass.first
            var candidates = firstPass.second
            var ok = firstPass.third

            if (!ok || text.isBlank()) {
                val forced = HandwritingLineChunker.splitIntoHorizontalChunks(
                    blockStrokes,
                    pageContentWidth = pageWidth,
                    medianStrokeHeight = medianStrokeHeight,
                    force = true,
                )
                if (forced.size > chunks.size || text.isBlank()) {
                    if (forced.size > 1) {
                        usedHorizontalChunking = true
                        chunkingUsedForBlock = true
                    }
                    chunks = forced
                    val second = recognizeChunkList(
                        chunks = forced,
                        preContextStart = preContext,
                        languageMode = languageMode,
                    )
                    // Не затирать непустой первый проход пустым повтором (частый регресс при лишнем chunking).
                    if (second.first.isNotBlank() || text.isBlank()) {
                        text = second.first
                        candidates = second.second
                        ok = second.third
                    }
                }
            }

            if (!ok || text.isBlank()) {
                hadFailures = true
                finalized.add(
                    template.copy(
                        recognizedText = "",
                        recognitionCandidates = candidates,
                        recognitionStatus = BlockRecognitionStatus.Failed,
                        updatedAt = now,
                    ),
                )
                d { "  FAILED or empty; candidates=${candidates.size}" }
                continue
            }

            val cleaned = TextPostProcessor.cleanBlockText(text)
            finalized.add(
                template.copy(
                    recognizedText = cleaned,
                    recognitionCandidates = candidates,
                    recognitionStatus = BlockRecognitionStatus.Recognized,
                    updatedAt = now,
                ),
            )
            appendContextTail(contextTail, cleaned)
            d { "  recognized len=${cleaned.length} chunking=$chunkingUsedForBlock" }
        }

        val assembled = RecognizedTextAssembler.assembleRecognizedText(finalized)
        val effectivelyEmpty = assembled.isBlank()

        d {
            "done: assembledLen=${assembled.length} failures=$hadFailures " +
                "chunking=$usedHorizontalChunking effectiveEmpty=$effectivelyEmpty"
        }

        return Result(
            assembledText = assembled,
            blocks = finalized,
            isEffectivelyEmpty = effectivelyEmpty,
            hadBlockFailures = hadFailures,
            usedHorizontalChunking = usedHorizontalChunking,
            strokeCount = filtered.size,
        )
    }

    private suspend fun recognizeChunkList(
        chunks: List<List<InkStroke>>,
        preContextStart: String,
        languageMode: RecognitionLanguageMode,
    ): Triple<String, List<String>, Boolean> {
        var localContext = preContextStart
        val texts = ArrayList<String>(chunks.size)
        val mergedCandidateOrder = ArrayList<String>()
        var lastCandidates: List<String> = emptyList()
        var allOk = true
        for (chunk in chunks) {
            if (chunk.isEmpty()) continue
            // Не передаём WritingArea: подсказка по bounds в координатах холста даёт у ML Kit пустые/неверные
            // результаты на части устройств. preContext оставляем — он совместим с прежним поведением.
            val opts = RecognitionOptions(
                languageMode = languageMode,
                writingAreaWidth = null,
                writingAreaHeight = null,
                preContext = localContext.takeLast(PRE_CONTEXT_MAX_CHARS),
            )
            val result = try {
                recognitionService.recognize(strokes = chunk, options = opts)
            } catch (_: Exception) {
                allOk = false
                HandwritingRecognitionResult(text = "", candidates = emptyList())
            }
            val piece = TextPostProcessor.cleanBlockText(result.text)
            texts.add(piece)
            lastCandidates = result.candidates
            for (c in result.candidates) {
                if (c.isNotBlank() && c !in mergedCandidateOrder) {
                    mergedCandidateOrder.add(c)
                }
            }
            if (piece.isNotEmpty()) {
                if (localContext.isNotEmpty() && !localContext.endsWith(' ') && !localContext.endsWith('\n')) {
                    localContext += " "
                }
                localContext += piece
                if (localContext.length > 256) {
                    localContext = localContext.takeLast(256)
                }
            }
        }
        val merged = HandwritingLineChunker.mergeChunkTexts(texts)
        val primary = TextPostProcessor.cleanBlockText(merged)
        if (primary.isNotEmpty()) {
            return Triple(primary, mergedCandidateOrder.ifEmpty { lastCandidates }, allOk)
        }
        val fromCandidate = mergedCandidateOrder
            .firstOrNull { it.isNotBlank() }
            ?.let { TextPostProcessor.cleanBlockText(it) }
            .orEmpty()
        return Triple(fromCandidate, mergedCandidateOrder.ifEmpty { lastCandidates }, allOk)
    }

    private companion object {
        const val PRE_CONTEXT_MAX_CHARS = 48
        const val DEBUG_TAG = "HwRecognition"

        fun appendContextTail(sb: StringBuilder, line: String) {
            val t = TextPostProcessor.cleanBlockText(line)
            if (t.isEmpty()) return
            if (sb.isNotEmpty()) sb.append('\n')
            sb.append(t)
            if (sb.length > 512) {
                val drop = sb.length - 512
                sb.delete(0, drop)
            }
        }
    }
}
