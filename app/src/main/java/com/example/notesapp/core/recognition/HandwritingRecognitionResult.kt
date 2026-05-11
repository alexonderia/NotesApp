package com.example.notesapp.core.recognition

data class HandwritingRecognitionResult(
    val text: String,
    val candidates: List<String> = emptyList(),
)
