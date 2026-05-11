package com.example.notesapp.features.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.notesapp.core.model.InkStroke
import com.example.notesapp.core.model.Note
import com.example.notesapp.core.repository.NotesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

enum class EditorMode {
    Handwriting,
    Text,
}

class NoteEditorViewModel(
    private val repository: NotesRepository,
    private val noteId: String,
) : ViewModel() {

    val note: StateFlow<Note?> = repository.notes
        .map { list -> list.find { it.id == noteId } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = repository.getNote(noteId),
        )

    private val _editorMode = MutableStateFlow(EditorMode.Text)
    val editorMode: StateFlow<EditorMode> = _editorMode.asStateFlow()

    fun setEditorMode(mode: EditorMode) {
        _editorMode.value = mode
    }

    fun onTextChange(text: String) {
        repository.updateNoteText(noteId, text)
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
        fun factory(repository: NotesRepository, noteId: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass.isAssignableFrom(NoteEditorViewModel::class.java))
                    return NoteEditorViewModel(repository, noteId) as T
                }
            }
    }
}
