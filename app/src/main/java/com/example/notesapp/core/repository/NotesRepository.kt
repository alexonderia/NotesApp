package com.example.notesapp.core.repository

import com.example.notesapp.core.model.Folder
import com.example.notesapp.core.model.HandwritingBlock
import com.example.notesapp.core.model.InkStroke
import com.example.notesapp.core.model.Note
import kotlinx.coroutines.flow.StateFlow

interface NotesRepository {
    val notes: StateFlow<List<Note>>
    val folders: StateFlow<List<Folder>>

    // ── Notes ──────────────────────────────────────────────────────────
    fun createNote(): Note
    fun getNote(noteId: String): Note?
    /** [handwritingBlocks] == null — не менять блоки; иначе заменить целиком. */
    fun updateRecognizedContent(
        noteId: String,
        recognizedText: String,
        recognizedStrokeIds: Set<String>,
        handwritingBlocks: List<HandwritingBlock>? = null,
    )
    /** Очищает рукопись и зеркало распознавания; [Note.manualText] не меняется. */
    fun clearHandwriting(noteId: String)
    fun updateManualText(noteId: String, text: String)
    fun updateNoteTitle(noteId: String, title: String)
    fun updateNoteStrokes(noteId: String, strokes: List<InkStroke>)
    fun updateNoteFolder(noteId: String, folderId: String?)

    // ── Folders ────────────────────────────────────────────────────────
    fun createFolder(name: String): Folder
    fun updateFolderName(folderId: String, name: String)
    /** Удаляет папку; заметки из неё переходят в «без папки» (folderId = null). */
    fun deleteFolder(folderId: String)
}
