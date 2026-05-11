package com.example.notesapp.core.model

import java.util.UUID

data class InkStroke(
    val id: String = UUID.randomUUID().toString(),
    val points: List<StrokePoint> = emptyList(),
    val color: Long = 0xFF000000L,
    val width: Float = 4f,
    val timestamp: Long = System.currentTimeMillis(),
)
