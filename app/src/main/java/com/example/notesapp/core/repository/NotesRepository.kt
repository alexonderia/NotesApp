package com.example.notesapp.core.repository

import com.example.notesapp.core.model.Folder
import com.example.notesapp.core.model.Note
import kotlinx.coroutines.flow.StateFlow

interface NotesRepository {
    val notes: StateFlow<List<Note>>
    val folders: StateFlow<List<Folder>>

    fun createNote(): Note

    fun updateNoteText(noteId: String, text: String)

    fun getNote(noteId: String): Note?
}
