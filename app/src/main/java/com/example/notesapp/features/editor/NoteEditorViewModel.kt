package com.example.notesapp.features.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.notesapp.core.model.Folder
import com.example.notesapp.core.model.InkStroke
import com.example.notesapp.core.model.Note
import com.example.notesapp.core.model.ToolType
import com.example.notesapp.core.model.penStrokesOnly
import com.example.notesapp.core.recognition.HandwritingRecognitionService
import com.example.notesapp.core.recognition.RecognitionLanguageMode
import com.example.notesapp.core.recognition.RecognitionPipeline
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

    private val defaultRecognitionLanguageMode = RecognitionLanguageMode.RussianEnglishAuto

    private val recognitionPipeline = RecognitionPipeline(recognitionService)

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

    private val _selectedTool = MutableStateFlow(ToolType.Pen)
    val selectedTool: StateFlow<ToolType> = _selectedTool.asStateFlow()

    private val _selectedColor = MutableStateFlow(0xFF000000L)
    val selectedColor: StateFlow<Long> = _selectedColor.asStateFlow()

    private val _selectedWidth = MutableStateFlow(4f)
    val selectedWidth: StateFlow<Float> = _selectedWidth.asStateFlow()

    private val _redoStack = MutableStateFlow<List<InkStroke>>(emptyList())
    val redoStack: StateFlow<List<InkStroke>> = _redoStack.asStateFlow()

    fun setSelectedTool(tool: ToolType) {
        _selectedTool.value = tool
    }

    fun setSelectedColor(color: Long) {
        _selectedColor.value = color
    }

    fun setSelectedWidth(width: Float) {
        _selectedWidth.value = width
    }

    fun setEditorMode(mode: EditorMode) {
        val currentMode = _editorMode.value
        if (currentMode == EditorMode.Handwriting && mode == EditorMode.Text) {
            val alreadyRunning = _recognitionState.value == RecognitionState.Recognizing ||
                _recognitionState.value == RecognitionState.DownloadingModel
            if (alreadyRunning) return

            val n = note.value
            val newStrokes = n?.strokes?.penStrokesOnly()?.filter { it.id !in n.recognizedStrokeIds }.orEmpty()
            if (newStrokes.isNotEmpty()) {
                viewModelScope.launch {
                    runRecognitionPipeline(requireNewInk = true)
                    _editorMode.value = EditorMode.Text
                }
                return
            }
        }
        _editorMode.value = mode
    }

    fun recognizeHandwriting() {
        if (isRecognitionBusy()) return
        viewModelScope.launch {
            runRecognitionPipeline(requireNewInk = false)
        }
    }

    private fun isRecognitionBusy(): Boolean =
        _recognitionState.value == RecognitionState.Recognizing ||
            _recognitionState.value == RecognitionState.DownloadingModel

    private fun clearRedo() {
        _redoStack.value = emptyList()
    }

    private suspend fun runRecognitionPipeline(requireNewInk: Boolean) {
        val currentNote = note.value ?: return
        val penAll = currentNote.strokes.penStrokesOnly()
        if (penAll.isEmpty()) {
            if (!requireNewInk) {
                _recognitionState.value = RecognitionState.Error("no_strokes")
            }
            return
        }
        if (requireNewInk && penAll.none { it.id !in currentNote.recognizedStrokeIds }) {
            return
        }

        try {
            _recognitionState.value = RecognitionState.DownloadingModel
            recognitionService.ensureModelsDownloaded(defaultRecognitionLanguageMode)

            _recognitionState.value = RecognitionState.Recognizing

            val result = recognitionPipeline.run(
                penStrokes = penAll,
                previousBlocks = currentNote.handwritingBlocks,
                languageMode = defaultRecognitionLanguageMode,
            )

            if (result.isEffectivelyEmpty) {
                val emptyKey =
                    if (currentNote.recognizedText.isBlank()) {
                        "recognition_empty_no_prior"
                    } else {
                        "recognition_empty_kept_prior"
                    }
                _recognitionState.value = RecognitionState.Error(emptyKey)
                return
            }

            val allIds = penAll.map { it.id }.toSet()
            repository.updateRecognizedContent(
                noteId = noteId,
                recognizedText = result.assembledText,
                recognizedStrokeIds = allIds,
                handwritingBlocks = result.blocks,
            )
            _recognitionState.value = RecognitionState.Success(result.assembledText)
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

    fun onManualTextChange(text: String) {
        repository.updateManualText(noteId, text)
    }

    fun onFolderSelected(folderId: String?) {
        repository.updateNoteFolder(noteId, folderId)
    }

    fun onStrokeAdded(stroke: InkStroke) {
        if (stroke.points.size < 2) return
        clearRedo()
        val currentStrokes = repository.getNote(noteId)?.strokes ?: emptyList()
        val penStroke = stroke.copy(toolType = ToolType.Pen)
        repository.updateNoteStrokes(noteId, currentStrokes + penStroke)
    }

    fun onStrokesReplaceAfterErase(newStrokes: List<InkStroke>) {
        clearRedo()
        repository.updateNoteStrokes(noteId, newStrokes)
    }

    fun onUndoStroke() {
        val currentStrokes = repository.getNote(noteId)?.strokes ?: emptyList()
        if (currentStrokes.isEmpty()) return
        val last = currentStrokes.last()
        repository.updateNoteStrokes(noteId, currentStrokes.dropLast(1))
        _redoStack.value = _redoStack.value + last
    }

    fun onRedoStroke() {
        val redo = _redoStack.value
        if (redo.isEmpty()) return
        val stroke = redo.last()
        val currentStrokes = repository.getNote(noteId)?.strokes ?: emptyList()
        repository.updateNoteStrokes(noteId, currentStrokes + stroke)
        _redoStack.value = redo.dropLast(1)
    }

    fun onClearStrokes() {
        clearRedo()
        repository.clearHandwriting(noteId)
    }

    companion object {
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
