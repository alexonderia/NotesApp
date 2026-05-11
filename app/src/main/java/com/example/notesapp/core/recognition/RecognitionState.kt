package com.example.notesapp.core.recognition

sealed interface RecognitionState {
    data object Idle : RecognitionState
    data object DownloadingModel : RecognitionState
    data object Recognizing : RecognitionState
    data class Success(val text: String) : RecognitionState
    data class Error(val message: String) : RecognitionState
}
