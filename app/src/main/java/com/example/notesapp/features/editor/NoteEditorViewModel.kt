package com.example.notesapp.features.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.notesapp.core.model.Folder
import com.example.notesapp.core.model.InkStroke
import com.example.notesapp.core.model.Note
import com.example.notesapp.core.recognition.HandwritingRecognitionService
import com.example.notesapp.core.recognition.RecognitionState
import com.example.notesapp.core.repository.NotesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class EditorMode {
    Handwriting,
    Text,
}

class NoteEditorViewModel(
    private val repository: NotesRepository,
    private val noteId: String,
    private val recognitionService: HandwritingRecognitionService,
) : ViewModel() {

    val note: StateFlow<Note?> = repository.notes
        .map { list -> list.find { it.id == noteId } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = repository.getNote(noteId),
        )

    val folders: StateFlow<List<Folder>> = repository.folders
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    private val _editorMode = MutableStateFlow(EditorMode.Text)
    val editorMode: StateFlow<EditorMode> = _editorMode.asStateFlow()

    private val _recognitionState = MutableStateFlow<RecognitionState>(RecognitionState.Idle)
    val recognitionState: StateFlow<RecognitionState> = _recognitionState.asStateFlow()

    fun setEditorMode(mode: EditorMode) {
        val currentMode = _editorMode.value
        if (currentMode == EditorMode.Handwriting && mode == EditorMode.Text) {
            val strokes = note.value?.strokes ?: emptyList()
            val alreadyRunning = _recognitionState.value == RecognitionState.Recognizing ||
                _recognitionState.value == RecognitionState.DownloadingModel
            if (strokes.isNotEmpty() && !alreadyRunning) {
                viewModelScope.launch {
                    runRecognition(strokes)
                    _editorMode.value = EditorMode.Text
                }
                return
            }
        }
        _editorMode.value = mode
    }

    fun recognizeCurrentHandwriting() {
        val alreadyRunning = _recognitionState.value == RecognitionState.Recognizing ||
            _recognitionState.value == RecognitionState.DownloadingModel
        if (alreadyRunning) return

        val strokes = note.value?.strokes ?: emptyList()
        if (strokes.isEmpty()) {
            _recognitionState.value = RecognitionState.Error("no_strokes")
            return
        }

        viewModelScope.launch {
            runRecognition(strokes)
        }
    }

    private suspend fun runRecognition(strokes: List<InkStroke>) {
        try {
            _recognitionState.value = RecognitionState.DownloadingModel
            recognitionService.ensureRussianModelDownloaded()

            _recognitionState.value = RecognitionState.Recognizing
            val currentNote = note.value ?: return
            val preContext = currentNote.text.takeLast(20)

            val result = recognitionService.recognizeRussian(
                strokes = strokes,
                preContext = preContext,
            )

            val merged = mergeRecognizedText(currentNote.text, result.text)
            repository.updateNoteText(noteId, merged)
            _recognitionState.value = RecognitionState.Success(result.text)
        } catch (e: Exception) {
            _recognitionState.value = RecognitionState.Error(e.message ?: "Ошибка распознавания")
        }
    }

    fun dismissRecognitionState() {
        _recognitionState.value = RecognitionState.Idle
    }

    fun onTitleChange(title: String) {
        repository.updateNoteTitle(noteId, title)
    }

    fun onTextChange(text: String) {
        repository.updateNoteText(noteId, text)
    }

    fun onFolderSelected(folderId: String?) {
        repository.updateNoteFolder(noteId, folderId)
    }

    fun onStrokeAdded(stroke: InkStroke) {
        val currentStrokes = repository.getNote(noteId)?.strokes ?: emptyList()
        repository.updateNoteStrokes(noteId, currentStrokes + stroke)
    }

    fun onUndoStroke() {
        val currentStrokes = repository.getNote(noteId)?.strokes ?: emptyList()
        if (currentStrokes.isNotEmpty()) {
            repository.updateNoteStrokes(noteId, currentStrokes.dropLast(1))
        }
    }

    fun onClearStrokes() {
        repository.updateNoteStrokes(noteId, emptyList())
    }

    companion object {
        fun mergeRecognizedText(existing: String, recognized: String): String {
            if (recognized.isBlank()) return existing
            return if (existing.isBlank()) recognized else "$existing\n\n$recognized"
        }

        fun factory(
            repository: NotesRepository,
            noteId: String,
            recognitionService: HandwritingRecognitionService,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass.isAssignableFrom(NoteEditorViewModel::class.java))
                    return NoteEditorViewModel(repository, noteId, recognitionService) as T
                }
            }
    }
}
