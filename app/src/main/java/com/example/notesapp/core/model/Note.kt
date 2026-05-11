package com.example.notesapp.core.model

data class Note(
    val id: String,
    val title: String,
    val text: String,
    val strokes: List<InkStroke> = emptyList(),
    val folderId: String,
    /** Epoch milliseconds; обновляется при создании и изменении текста. */
    val lastModifiedEpochMs: Long,
)
