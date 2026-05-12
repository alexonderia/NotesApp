package com.example.notesapp.features.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.notesapp.core.handwriting.HandwritingBlockBuilder
import com.example.notesapp.core.model.Folder
import com.example.notesapp.core.model.HandwritingBlock
import com.example.notesapp.core.model.InkStroke
import com.example.notesapp.core.model.Note
import com.example.notesapp.core.model.ToolType
import com.example.notesapp.core.model.penStrokesOnly
import com.example.notesapp.core.recognition.HandwritingRecognitionService
import com.example.notesapp.core.recognition.RecognitionLanguageMode
import com.example.notesapp.core.recognition.RecognitionOptions
import com.example.notesapp.core.recognition.RecognitionState
import com.example.notesapp.core.recognition.TextPostProcessor
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

    private fun recognitionOptions(preContext: String = "") = RecognitionOptions(
        languageMode = defaultRecognitionLanguageMode,
        preContext = preContext,
    )

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
                    runRecognizeNewStrokes()
                    _editorMode.value = EditorMode.Text
                }
                return
            }
        }
        _editorMode.value = mode
    }

    fun recognizeNewHandwriting() {
        if (isRecognitionBusy()) return

        val currentNote = note.value ?: return
        val newStrokes = currentNote.strokes
            .penStrokesOnly()
            .filter { it.id !in currentNote.recognizedStrokeIds }
        if (newStrokes.isEmpty()) {
            _recognitionState.value = RecognitionState.Error("no_new_strokes")
            return
        }

        viewModelScope.launch {
            runRecognizeNewStrokes()
        }
    }

    fun recognizeAllHandwriting() {
        if (isRecognitionBusy()) return

        val strokes = note.value?.strokes?.penStrokesOnly() ?: emptyList()
        if (strokes.isEmpty()) {
            _recognitionState.value = RecognitionState.Error("no_strokes")
            return
        }

        viewModelScope.launch {
            runRecognizeAllStrokesReplace(strokes)
        }
    }

    fun recognizeBlocks() {
        if (isRecognitionBusy()) return

        val strokes = note.value?.strokes?.penStrokesOnly() ?: emptyList()
        if (strokes.isEmpty()) {
            _recognitionState.value = RecognitionState.Error("no_strokes")
            return
        }

        viewModelScope.launch {
            runRecognizeByBlocks(strokes)
        }
    }

    private fun isRecognitionBusy(): Boolean =
        _recognitionState.value == RecognitionState.Recognizing ||
            _recognitionState.value == RecognitionState.DownloadingModel

    private fun clearRedo() {
        _redoStack.value = emptyList()
    }

    private fun assembleRecognizedText(blocks: List<HandwritingBlock>): String {
        val nonEmpty = blocks
            .sortedBy { it.orderIndex }
            .filter { it.recognizedText.isNotBlank() }
        if (nonEmpty.isEmpty()) return ""

        val paragraphGap = HandwritingBlockBuilder.PARAGRAPH_ORDER_INDEX_GAP / 2
        val groups = mutableListOf<MutableList<String>>()
        var prev: HandwritingBlock? = null
        for (block in nonEmpty) {
            val text = block.recognizedText
            if (prev == null) {
                groups.add(mutableListOf(text))
            } else {
                val orderDelta = block.orderIndex - prev.orderIndex
                if (orderDelta >= paragraphGap) {
                    groups.add(mutableListOf(text))
                } else {
                    groups.last().add(text)
                }
            }
            prev = block
        }
        return groups.joinToString("\n\n") { TextPostProcessor.assembleBlocks(it) }
    }

    private suspend fun recognizeBlocksInternal(
        strokes: List<InkStroke>,
    ): Pair<String, List<HandwritingBlock>> {
        val penStrokes = strokes.penStrokesOnly()
        if (penStrokes.isEmpty()) return "" to emptyList()

        val templateBlocks = HandwritingBlockBuilder.build(penStrokes)
        if (templateBlocks.isEmpty()) return "" to emptyList()

        val strokeById = penStrokes.associateBy { it.id }
        val now = System.currentTimeMillis()
        val finalized = templateBlocks.map { block ->
            val blockStrokes = block.strokeIds.mapNotNull { strokeById[it] }.penStrokesOnly()
            val lineText = if (blockStrokes.isEmpty()) {
                ""
            } else {
                val raw = recognitionService.recognize(
                    strokes = blockStrokes,
                    options = recognitionOptions(),
                ).text
                TextPostProcessor.cleanBlockText(raw)
            }
            block.copy(recognizedText = lineText, updatedAt = now)
        }
        val assembled = assembleRecognizedText(finalized)
        return assembled to finalized
    }

    private suspend fun runRecognizeNewStrokes() {
        val currentNote = note.value ?: return
        val penAll = currentNote.strokes.penStrokesOnly()
        val hasNew = penAll.any { it.id !in currentNote.recognizedStrokeIds }
        if (!hasNew) {
            _recognitionState.value = RecognitionState.Error("no_new_strokes")
            return
        }
        if (penAll.isEmpty()) {
            _recognitionState.value = RecognitionState.Error("no_new_strokes")
            return
        }
        try {
            _recognitionState.value = RecognitionState.DownloadingModel
            recognitionService.ensureModelsDownloaded(defaultRecognitionLanguageMode)

            _recognitionState.value = RecognitionState.Recognizing

            val (assembled, finalized) = recognizeBlocksInternal(penAll)
            if (finalized.isEmpty()) {
                _recognitionState.value = RecognitionState.Error("no_strokes")
                return
            }

            val allIds = penAll.map { it.id }.toSet()
            repository.updateRecognizedContent(noteId, assembled, allIds, finalized)
            _recognitionState.value = RecognitionState.Success(assembled)
        } catch (e: Exception) {
            _recognitionState.value = RecognitionState.Error(e.message ?: "Ошибка распознавания")
        }
    }

    private suspend fun runRecognizeAllStrokesReplace(allStrokes: List<InkStroke>) {
        val penOnly = allStrokes.penStrokesOnly()
        if (penOnly.isEmpty()) {
            _recognitionState.value = RecognitionState.Error("no_strokes")
            return
        }
        try {
            _recognitionState.value = RecognitionState.DownloadingModel
            recognitionService.ensureModelsDownloaded(defaultRecognitionLanguageMode)

            _recognitionState.value = RecognitionState.Recognizing

            val (assembled, finalized) = recognizeBlocksInternal(penOnly)
            if (finalized.isEmpty()) {
                _recognitionState.value = RecognitionState.Error("no_strokes")
                return
            }

            val allIds = penOnly.map { it.id }.toSet()
            repository.updateRecognizedContent(noteId, assembled, allIds, finalized)
            _recognitionState.value = RecognitionState.Success(assembled)
        } catch (e: Exception) {
            _recognitionState.value = RecognitionState.Error(e.message ?: "Ошибка распознавания")
        }
    }

    private suspend fun runRecognizeByBlocks(strokes: List<InkStroke>) {
        val penStrokes = strokes.penStrokesOnly()
        if (penStrokes.isEmpty()) {
            _recognitionState.value = RecognitionState.Error("no_strokes")
            return
        }
        try {
            _recognitionState.value = RecognitionState.DownloadingModel
            recognitionService.ensureModelsDownloaded(defaultRecognitionLanguageMode)

            _recognitionState.value = RecognitionState.Recognizing

            val (assembled, finalized) = recognizeBlocksInternal(penStrokes)
            if (finalized.isEmpty()) {
                _recognitionState.value = RecognitionState.Error("no_strokes")
                return
            }

            val allIds = penStrokes.map { it.id }.toSet()
            repository.updateRecognizedContent(noteId, assembled, allIds, finalized)
            _recognitionState.value = RecognitionState.Success(assembled)
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
