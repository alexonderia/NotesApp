package com.example.notesapp.core.repository

import com.example.notesapp.core.model.Folder
import com.example.notesapp.core.model.InkStroke
import com.example.notesapp.core.model.Note
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class InMemoryNotesRepository : NotesRepository {

    private val defaultFolderId = "folder-default"

    private val _folders = MutableStateFlow(
        listOf(Folder(id = defaultFolderId, name = "Общее")),
    )
    override val folders: StateFlow<List<Folder>> = _folders.asStateFlow()

    private val _notes = MutableStateFlow<List<Note>>(emptyList())
    override val notes: StateFlow<List<Note>> = _notes.asStateFlow()

    override fun createNote(): Note {
        val now = System.currentTimeMillis()
        val note = Note(
            id = UUID.randomUUID().toString(),
            title = "Новая заметка",
            text = "",
            strokes = emptyList(),
            folderId = defaultFolderId,
            lastModifiedEpochMs = now,
        )
        _notes.update { it + note }
        return note
    }

    override fun updateNoteText(noteId: String, text: String) {
        val now = System.currentTimeMillis()
        _notes.update { list ->
            list.map { note ->
                if (note.id == noteId) note.copy(text = text, lastModifiedEpochMs = now) else note
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

    override fun getNote(noteId: String): Note? = _notes.value.find { it.id == noteId }
}
