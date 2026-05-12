package com.example.notesapp.core.recognition

data class RecognitionOptions(
    val languageMode: RecognitionLanguageMode,
    val writingAreaWidth: Float? = null,
    val writingAreaHeight: Float? = null,
    val preContext: String = "",
)
