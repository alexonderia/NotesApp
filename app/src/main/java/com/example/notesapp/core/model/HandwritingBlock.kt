package com.example.notesapp.core.model

data class HandwritingBlock(
    val id: String,
    val strokeIds: List<String>,
    val bounds: StrokeBounds,
    val recognizedText: String,
    val orderIndex: Int,
    val updatedAt: Long,
)
