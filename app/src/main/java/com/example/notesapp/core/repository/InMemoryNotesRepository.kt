package com.example.notesapp.core.repository

import com.example.notesapp.core.model.Folder
import com.example.notesapp.core.model.HandwritingBlock
import com.example.notesapp.core.model.InkStroke
import com.example.notesapp.core.model.Note
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class InMemoryNotesRepository : NotesRepository {

    private val _folders = MutableStateFlow<List<Folder>>(emptyList())
    override val folders: StateFlow<List<Folder>> = _folders.asStateFlow()

    private val _notes = MutableStateFlow<List<Note>>(emptyList())
    override val notes: StateFlow<List<Note>> = _notes.asStateFlow()

    // ── Notes ──────────────────────────────────────────────────────────

    override fun createNote(): Note {
        val now = System.currentTimeMillis()
        val note = Note(
            id = UUID.randomUUID().toString(),
            title = "Новая заметка",
            recognizedText = "",
            manualText = "",
            strokes = emptyList(),
            folderId = null,
            lastModifiedEpochMs = now,
            createdAt = now,
        )
        _notes.update { it + note }
        return note
    }

    override fun getNote(noteId: String): Note? = _notes.value.find { it.id == noteId }

    override fun updateRecognizedContent(
        noteId: String,
        recognizedText: String,
        recognizedStrokeIds: Set<String>,
        handwritingBlocks: List<HandwritingBlock>?,
    ) {
        val now = System.currentTimeMillis()
        _notes.update { list ->
            list.map { note ->
                if (note.id == noteId) {
                    val blocks = handwritingBlocks ?: note.handwritingBlocks
                    note.copy(
                        recognizedText = recognizedText,
                        recognizedStrokeIds = recognizedStrokeIds,
                        handwritingBlocks = blocks,
                        lastModifiedEpochMs = now,
                    )
                } else {
                    note
                }
            }
        }
    }

    override fun clearHandwriting(noteId: String) {
        val now = System.currentTimeMillis()
        _notes.update { list ->
            list.map { note ->
                if (note.id == noteId) {
                    note.copy(
                        strokes = emptyList(),
                        recognizedText = "",
                        recognizedStrokeIds = emptySet(),
                        handwritingBlocks = emptyList(),
                        lastModifiedEpochMs = now,
                    )
                } else {
                    note
                }
            }
        }
    }

    override fun updateManualText(noteId: String, text: String) {
        val now = System.currentTimeMillis()
        _notes.update { list ->
            list.map { note ->
                if (note.id == noteId) note.copy(manualText = text, lastModifiedEpochMs = now) else note
            }
        }
    }

    override fun updateNoteTitle(noteId: String, title: String) {
        val now = System.currentTimeMillis()
        _notes.update { list ->
            list.map { note ->
                if (note.id == noteId) note.copy(title = title, lastModifiedEpochMs = now) else note
            }
        }
    }

    override fun updateNoteStrokes(noteId: String, strokes: List<InkStroke>) {
        val now = System.currentTimeMillis()
        _notes.update { list ->
            list.map { note ->
                if (note.id == noteId) note.copy(strokes = strokes, lastModifiedEpochMs = now) else note
            }
        }
    }

    override fun updateNoteFolder(noteId: String, folderId: String?) {
        val now = System.currentTimeMillis()
        _notes.update { list ->
            list.map { note ->
                if (note.id == noteId) note.copy(folderId = folderId, lastModifiedEpochMs = now) else note
            }
        }
    }

    // ── Folders ────────────────────────────────────────────────────────

    override fun createFolder(name: String): Folder {
        val now = System.currentTimeMillis()
        val folder = Folder(id = UUID.randomUUID().toString(), name = name, createdAt = now, updatedAt = now)
        _folders.update { it + folder }
        return folder
    }

    override fun updateFolderName(folderId: String, name: String) {
        val now = System.currentTimeMillis()
        _folders.update { list ->
            list.map { folder ->
                if (folder.id == folderId) folder.copy(name = name, updatedAt = now) else folder
            }
        }
    }

    override fun deleteFolder(folderId: String) {
        _folders.update { list -> list.filter { it.id != folderId } }
        _notes.update { list ->
            list.map { note ->
                if (note.folderId == folderId) note.copy(folderId = null) else note
            }
        }
    }
}
