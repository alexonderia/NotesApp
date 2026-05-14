package com.example.notesapp.core.recognition

import com.example.notesapp.core.handwriting.HandwritingBlockBuilder
import com.example.notesapp.core.model.HandwritingBlock

/**
 * Собирает итоговый текст заметки из блоков: порядок по [HandwritingBlock.orderIndex],
 * абзацы — по большим скачкам индекса (см. [HandwritingBlockBuilder.PARAGRAPH_ORDER_INDEX_GAP]).
 */
object RecognizedTextAssembler {

    fun assembleRecognizedText(blocks: List<HandwritingBlock>): String {
        val nonEmpty = blocks
            .sortedBy { it.orderIndex }
            .filter { it.recognizedText.isNotBlank() }
        if (nonEmpty.isEmpty()) return ""

        val paragraphGap = HandwritingBlockBuilder.PARAGRAPH_ORDER_INDEX_GAP / 2
        val groups = mutableListOf<MutableList<String>>()
        var prev: HandwritingBlock? = null
        for (block in nonEmpty) {
            val text = block.recognizedText
            if (prev == null) {
                groups.add(mutableListOf(text))
            } else {
                val orderDelta = block.orderIndex - prev.orderIndex
                if (orderDelta >= paragraphGap) {
                    groups.add(mutableListOf(text))
                } else {
                    groups.last().add(text)
                }
            }
            prev = block
        }
        return groups.joinToString("\n\n") { TextPostProcessor.assembleBlocks(it) }
    }
}
