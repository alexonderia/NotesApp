package com.example.notesapp.core.recognition

import com.example.notesapp.core.model.InkStroke

interface HandwritingRecognitionService {
    suspend fun ensureRussianModelDownloaded()
    suspend fun recognizeRussian(
        strokes: List<InkStroke>,
        writingAreaWidth: Float? = null,
        writingAreaHeight: Float? = null,
        preContext: String = "",
    ): HandwritingRecognitionResult
}
