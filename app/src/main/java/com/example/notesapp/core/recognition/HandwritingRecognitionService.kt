package com.example.notesapp.core.recognition

import com.example.notesapp.core.model.InkStroke

interface HandwritingRecognitionService {
    suspend fun ensureModelsDownloaded(mode: RecognitionLanguageMode)

    suspend fun recognize(
        strokes: List<InkStroke>,
        options: RecognitionOptions,
    ): HandwritingRecognitionResult
}
