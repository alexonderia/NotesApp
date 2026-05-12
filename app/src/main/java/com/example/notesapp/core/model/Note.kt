package com.example.notesapp.core.model

fun buildDisplayText(recognizedText: String, manualText: String): String =
    when {
        manualText.isBlank() -> recognizedText
        recognizedText.isBlank() -> manualText
        else -> "$recognizedText\n\n$manualText"
    }

data class Note(
    val id: String,
    val title: String,
    val recognizedText: String = "",
    /** Штрихи, уже учтённые в [recognizedText] при пошаговом распознавании. */
    val recognizedStrokeIds: Set<String> = emptySet(),
    val manualText: String = "",
    val strokes: List<InkStroke> = emptyList(),
    /** Логические строки/фрагменты рукописи для зеркала текста по расположению на странице. */
    val handwritingBlocks: List<HandwritingBlock> = emptyList(),
    /** null означает «без папки». */
    val folderId: String?,
    /** Epoch milliseconds; обновляется при каждом изменении. */
    val lastModifiedEpochMs: Long,
    /** Epoch milliseconds; время создания заметки (не меняется). */
    val createdAt: Long = lastModifiedEpochMs,
) {
    /** Объединённый текст для превью и обратной совместимости. */
    val text: String get() = buildDisplayText(recognizedText, manualText)
}
