package com.example.notesapp.core.recognition

import com.example.notesapp.core.model.InkStroke
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModelIdentifier
import com.google.mlkit.vision.digitalink.DigitalInkRecognizerOptions
import com.google.mlkit.vision.digitalink.RecognitionContext
import com.google.mlkit.vision.digitalink.WritingArea
import kotlinx.coroutines.tasks.await

class MlKitHandwritingRecognitionService : HandwritingRecognitionService {

    private val modelIdentifier: DigitalInkRecognitionModelIdentifier by lazy {
        DigitalInkRecognitionModelIdentifier.fromLanguageTag("ru")
            ?: error("ML Kit не поддерживает языковой тег 'ru'. Проверьте подключение библиотеки digital-ink-recognition.")
    }

    private val model: DigitalInkRecognitionModel by lazy {
        DigitalInkRecognitionModel.builder(modelIdentifier).build()
    }

    private val recognizer by lazy {
        DigitalInkRecognition.getClient(
            DigitalInkRecognizerOptions.builder(model).build(),
        )
    }

    private val modelManager = RemoteModelManager.getInstance()

    override suspend fun ensureRussianModelDownloaded() {
        val isDownloaded = modelManager.isModelDownloaded(model).await()
        if (!isDownloaded) {
            val conditions = DownloadConditions.Builder().build()
            modelManager.download(model, conditions).await()
        }
    }

    override suspend fun recognizeRussian(
        strokes: List<InkStroke>,
        writingAreaWidth: Float?,
        writingAreaHeight: Float?,
        preContext: String,
    ): HandwritingRecognitionResult {
        if (strokes.isEmpty()) {
            return HandwritingRecognitionResult(text = "")
        }

        val ink = strokes.toMlKitInk()

        val recognitionContext = buildRecognitionContext(writingAreaWidth, writingAreaHeight, preContext)

        val mlKitResult = if (recognitionContext != null) {
            recognizer.recognize(ink, recognitionContext).await()
        } else {
            recognizer.recognize(ink).await()
        }

        val candidates = mlKitResult.candidates.map { it.text }
        val bestText = candidates.firstOrNull() ?: ""
        return HandwritingRecognitionResult(text = bestText, candidates = candidates)
    }

    private fun buildRecognitionContext(
        width: Float?,
        height: Float?,
        preContext: String,
    ): RecognitionContext? {
        val hasArea = width != null && height != null
        val hasContext = preContext.isNotEmpty()
        if (!hasArea && !hasContext) return null

        val builder = RecognitionContext.builder()
        if (hasContext) builder.setPreContext(preContext)
        if (hasArea) builder.setWritingArea(WritingArea(width!!, height!!))
        return builder.build()
    }
}
