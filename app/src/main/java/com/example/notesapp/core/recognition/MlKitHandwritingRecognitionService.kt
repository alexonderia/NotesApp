package com.example.notesapp.core.recognition

import com.example.notesapp.core.model.InkStroke
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModelIdentifier
import com.google.mlkit.vision.digitalink.DigitalInkRecognizer
import com.google.mlkit.vision.digitalink.DigitalInkRecognizerOptions
import com.google.mlkit.vision.digitalink.RecognitionContext
import com.google.mlkit.vision.digitalink.WritingArea
import kotlinx.coroutines.tasks.await
import java.util.concurrent.ConcurrentHashMap

class MlKitHandwritingRecognitionService : HandwritingRecognitionService {

    private val modelManager = RemoteModelManager.getInstance()

    private val modelsByTag = ConcurrentHashMap<String, DigitalInkRecognitionModel>()

    private val recognizersByTag = ConcurrentHashMap<String, DigitalInkRecognizer>()

    private fun identifierFor(tag: String): DigitalInkRecognitionModelIdentifier =
        DigitalInkRecognitionModelIdentifier.fromLanguageTag(tag)
            ?: error("ML Kit: неподдерживаемый языковой тег '$tag'.")

    private fun modelForTag(tag: String): DigitalInkRecognitionModel =
        modelsByTag.getOrPut(tag) {
            DigitalInkRecognitionModel.builder(identifierFor(tag)).build()
        }

    private fun recognizerForTag(tag: String): DigitalInkRecognizer =
        recognizersByTag.getOrPut(tag) {
            DigitalInkRecognition.getClient(
                DigitalInkRecognizerOptions.builder(modelForTag(tag)).build(),
            )
        }

    private fun languageTagsForMode(mode: RecognitionLanguageMode): List<String> =
        when (mode) {
            RecognitionLanguageMode.Russian -> listOf(LANG_TAG_RU)
            // Базовая модель «en» (латиница) стабильнее для рукописного текста, чем региональные варианты.
            RecognitionLanguageMode.English -> listOf(LANG_TAG_EN)
            RecognitionLanguageMode.RussianEnglishAuto -> listOf(LANG_TAG_RU, LANG_TAG_EN)
        }

    override suspend fun ensureModelsDownloaded(mode: RecognitionLanguageMode) {
        val conditions = DownloadConditions.Builder().build()
        for (tag in languageTagsForMode(mode)) {
            val model = modelForTag(tag)
            val isDownloaded = modelManager.isModelDownloaded(model).await()
            if (!isDownloaded) {
                modelManager.download(model, conditions).await()
            }
        }
    }

    override suspend fun recognize(
        strokes: List<InkStroke>,
        options: RecognitionOptions,
    ): HandwritingRecognitionResult {
        if (strokes.isEmpty()) {
            return HandwritingRecognitionResult(text = "")
        }
        return when (options.languageMode) {
            RecognitionLanguageMode.Russian -> recognizeWithTag(strokes, LANG_TAG_RU, options)
            RecognitionLanguageMode.English -> recognizeWithTag(strokes, LANG_TAG_EN, options)
            RecognitionLanguageMode.RussianEnglishAuto -> {
                val ru = recognizeWithTag(strokes, LANG_TAG_RU, options)
                val en = recognizeWithTag(strokes, LANG_TAG_EN, options)
                val ruText = pickRepresentativeRuCandidate(ru.candidates).ifBlank { ru.text.trim() }
                val enText = pickRepresentativeEnCandidate(en.candidates).ifBlank { en.text.trim() }
                val picked = pickBestRussianEnglishAuto(ruText, enText)
                val mergedCandidates = (ru.candidates + en.candidates)
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .distinct()
                HandwritingRecognitionResult(
                    text = picked,
                    candidates = if (mergedCandidates.isNotEmpty()) mergedCandidates else listOf(picked),
                )
            }
        }
    }

    private suspend fun recognizeWithTag(
        strokes: List<InkStroke>,
        languageTag: String,
        options: RecognitionOptions,
    ): HandwritingRecognitionResult {
        val ink = strokes.toMlKitInk()
        val recognitionContext = buildRecognitionContext(
            options.writingAreaWidth,
            options.writingAreaHeight,
            options.preContext,
        )
        val recognizer = recognizerForTag(languageTag)
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

    private companion object {
        const val LANG_TAG_RU = "ru"
        const val LANG_TAG_EN = "en"
    }
}
