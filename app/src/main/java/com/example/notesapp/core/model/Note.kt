package com.example.notesapp.core.model

data class Note(
    val id: String,
    val title: String,
    val text: String,
    val strokes: List<InkStroke> = emptyList(),
    /** null означает «без папки». */
    val folderId: String?,
    /** Epoch milliseconds; обновляется при каждом изменении. */
    val lastModifiedEpochMs: Long,
    /** Epoch milliseconds; время создания заметки (не меняется). */
    val createdAt: Long = lastModifiedEpochMs,
)
