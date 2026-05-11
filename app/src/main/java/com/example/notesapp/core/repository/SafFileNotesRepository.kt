package com.example.notesapp.core.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.example.notesapp.core.model.Folder
import com.example.notesapp.core.model.InkStroke
import com.example.notesapp.core.model.Note
import com.example.notesapp.core.vault.VaultManager
import com.example.notesapp.core.vault.VaultPreferences
import com.example.notesapp.core.vault.file.FolderMetadataParser
import com.example.notesapp.core.vault.file.InkJsonParser
import com.example.notesapp.core.vault.file.MarkdownNoteParser
import com.example.notesapp.core.vault.file.SafDocumentTree
import com.example.notesapp.core.vault.file.VaultFileNames
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * File-backed [NotesRepository] that stores every note as a Markdown file
 * in the user-selected vault folder (accessed via Storage Access Framework).
 *
 * Layout:
 * ```
 * <vault>/
 *   notes/
 *     <noteId>.md                 ← notes without a folder
 *     <folderSafeName>/
 *       <noteId>.md               ← foldered notes
 *   ink/
 *     <noteId>.ink.json
 *   metadata/
 *     folders.json
 * ```
 *
 * All mutations update in-memory state immediately (for instant UI feedback)
 * and write to disk on a background [Dispatchers.IO] coroutine.
 */
class SafFileNotesRepository(
    private val context: Context,
    private val vaultManager: VaultManager,
) : NotesRepository {

    private val TAG = "SafFileRepo"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ── Public state ──────────────────────────────────────────────────────────

    private val _notes = MutableStateFlow<List<Note>>(emptyList())
    override val notes: StateFlow<List<Note>> = _notes.asStateFlow()

    private val _folders = MutableStateFlow<List<Folder>>(emptyList())
    override val folders: StateFlow<List<Folder>> = _folders.asStateFlow()

    /** Emits true when a vault is configured and accessible. */
    private val _isVaultAvailable = MutableStateFlow(vaultManager.getVaultRoot() != null)
    val isVaultAvailable: StateFlow<Boolean> = _isVaultAvailable.asStateFlow()

    // ── SharedPreferences listener for vault changes ──────────────────────────

    private val vaultPrefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == VaultPreferences.KEY_VAULT_URI) {
            scope.launch {
                val available = vaultManager.getVaultRoot() != null
                _isVaultAvailable.value = available
                if (available) {
                    loadFromDisk()
                } else {
                    _notes.value = emptyList()
                    _folders.value = emptyList()
                }
            }
        }
    }

    init {
        context.getSharedPreferences(VaultPreferences.PREFS_NAME, Context.MODE_PRIVATE)
            .registerOnSharedPreferenceChangeListener(vaultPrefsListener)
        scope.launch { loadFromDisk() }
    }

    // ── Vault root helpers ────────────────────────────────────────────────────

    private fun vaultRoot(): DocumentFile? = vaultManager.getVaultRoot()

    private fun notesDir(root: DocumentFile): DocumentFile? =
        SafDocumentTree.findOrCreateDir(root, VaultFileNames.NOTES_DIR)

    private fun inkDir(root: DocumentFile): DocumentFile? =
        SafDocumentTree.findOrCreateDir(root, VaultFileNames.INK_DIR)

    private fun metadataDir(root: DocumentFile): DocumentFile? =
        SafDocumentTree.findOrCreateDir(root, VaultFileNames.METADATA_DIR)

    // ── Loading from disk ─────────────────────────────────────────────────────

    private fun loadFromDisk() {
        val root = vaultRoot() ?: run {
            Log.d(TAG, "loadFromDisk: no vault root")
            return
        }
        try {
            val loadedFolders = loadFolders(root)
            _folders.value = loadedFolders

            val inkDirectory = inkDir(root)
            val loadedNotes = loadNotes(root, loadedFolders, inkDirectory)
            _notes.value = loadedNotes.sortedByDescending { it.lastModifiedEpochMs }
            Log.d(TAG, "loadFromDisk: loaded ${loadedNotes.size} notes, ${loadedFolders.size} folders")
        } catch (e: Exception) {
            Log.e(TAG, "loadFromDisk failed: ${e.message}")
        }
    }

    private fun loadFolders(root: DocumentFile): List<Folder> {
        val metaDir = metadataDir(root) ?: return emptyList()
        val file = SafDocumentTree.findFile(metaDir, VaultFileNames.FOLDERS_JSON) ?: return emptyList()
        val content = SafDocumentTree.readText(context, file.uri) ?: return emptyList()
        return FolderMetadataParser.parse(content).map { FolderMetadataParser.toFolder(it) }
    }

    private fun loadNotes(
        root: DocumentFile,
        folders: List<Folder>,
        inkDirectory: DocumentFile?,
    ): List<Note> {
        val notesDirectory = notesDir(root) ?: return emptyList()
        val result = mutableListOf<Note>()

        SafDocumentTree.listFiles(notesDirectory).forEach { entry ->
            when {
                !entry.isDirectory && entry.name?.endsWith(VaultFileNames.NOTE_EXTENSION) == true -> {
                    loadNote(entry, inkDirectory, folderId = null)?.let { result.add(it) }
                }
                entry.isDirectory -> {
                    val folderForDir = folders.find {
                        VaultFileNames.safeFolderName(it.name) == entry.name
                    }
                    SafDocumentTree.listFiles(entry).forEach { noteFile ->
                        if (noteFile.name?.endsWith(VaultFileNames.NOTE_EXTENSION) == true) {
                            loadNote(noteFile, inkDirectory, folderId = folderForDir?.id)
                                ?.let { result.add(it) }
                        }
                    }
                }
            }
        }
        return result
    }

    private fun loadNote(
        file: DocumentFile,
        inkDirectory: DocumentFile?,
        folderId: String?,
    ): Note? = try {
        val content = SafDocumentTree.readText(context, file.uri) ?: return null
        val data = MarkdownNoteParser.parse(content) ?: run {
            Log.w(TAG, "Could not parse front matter: ${file.name}")
            return null
        }
        val strokes = if (inkDirectory != null) {
            val inkFile = SafDocumentTree.findFile(inkDirectory, VaultFileNames.inkFileName(data.id))
            if (inkFile != null) {
                val inkContent = SafDocumentTree.readText(context, inkFile.uri) ?: ""
                InkJsonParser.parse(inkContent)
            } else emptyList()
        } else emptyList()

        Note(
            id = data.id,
            title = data.title,
            text = data.body,
            strokes = strokes,
            folderId = data.folderId ?: folderId,
            lastModifiedEpochMs = data.updatedAt,
            createdAt = data.createdAt,
        )
    } catch (e: Exception) {
        Log.w(TAG, "loadNote failed for ${file.name}: ${e.message}")
        null
    }

    // ── Notes CRUD ────────────────────────────────────────────────────────────

    override fun createNote(): Note {
        val now = System.currentTimeMillis()
        val noteId = "note_${UUID.randomUUID()}"
        val note = Note(
            id = noteId,
            title = "Новая заметка",
            text = "",
            strokes = emptyList(),
            folderId = null,
            lastModifiedEpochMs = now,
            createdAt = now,
        )
        _notes.update { (it + note).sortedByDescending { n -> n.lastModifiedEpochMs } }

        scope.launch {
            val root = vaultRoot() ?: return@launch
            val nd = notesDir(root) ?: return@launch
            writeNoteFile(nd, note, folderName = null)
            val id_ = inkDir(root)
            if (id_ != null) {
                val inkFile = SafDocumentTree.getOrCreateFile(
                    id_, VaultFileNames.inkFileName(noteId), VaultFileNames.INK_MIME
                )
                if (inkFile != null) {
                    SafDocumentTree.writeText(context, inkFile.uri, InkJsonParser.serialize(noteId, emptyList()))
                }
            }
        }
        return note
    }

    override fun getNote(noteId: String): Note? = _notes.value.find { it.id == noteId }

    override fun updateNoteText(noteId: String, text: String) {
        val now = System.currentTimeMillis()
        _notes.update { list ->
            list.map { if (it.id == noteId) it.copy(text = text, lastModifiedEpochMs = now) else it }
        }
        scope.launch { persistNote(noteId) }
    }

    override fun updateNoteTitle(noteId: String, title: String) {
        val now = System.currentTimeMillis()
        _notes.update { list ->
            list.map { if (it.id == noteId) it.copy(title = title, lastModifiedEpochMs = now) else it }
        }
        scope.launch { persistNote(noteId) }
    }

    override fun updateNoteStrokes(noteId: String, strokes: List<InkStroke>) {
        val now = System.currentTimeMillis()
        _notes.update { list ->
            list.map { if (it.id == noteId) it.copy(strokes = strokes, lastModifiedEpochMs = now) else it }
        }
        scope.launch {
            val root = vaultRoot() ?: return@launch
            val id_ = inkDir(root) ?: return@launch
            val inkFile = SafDocumentTree.getOrCreateFile(
                id_, VaultFileNames.inkFileName(noteId), VaultFileNames.INK_MIME
            ) ?: return@launch
            SafDocumentTree.writeText(context, inkFile.uri, InkJsonParser.serialize(noteId, strokes))
            persistNote(noteId)
        }
    }

    override fun updateNoteFolder(noteId: String, folderId: String?) {
        val now = System.currentTimeMillis()
        val oldFolderId = _notes.value.find { it.id == noteId }?.folderId
        _notes.update { list ->
            list.map { if (it.id == noteId) it.copy(folderId = folderId, lastModifiedEpochMs = now) else it }
        }
        scope.launch {
            val root = vaultRoot() ?: return@launch
            val nd = notesDir(root) ?: return@launch
            moveNoteFile(noteId, oldFolderId, folderId, nd)
        }
    }

    // ── Folders CRUD ──────────────────────────────────────────────────────────

    override fun createFolder(name: String): Folder {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "Folder name must not be empty" }
        val now = System.currentTimeMillis()
        val folder = Folder(id = "folder_${UUID.randomUUID()}", name = trimmed, createdAt = now, updatedAt = now)
        _folders.update { it + folder }

        scope.launch {
            val root = vaultRoot() ?: return@launch
            val nd = notesDir(root) ?: return@launch
            SafDocumentTree.findOrCreateDir(nd, VaultFileNames.safeFolderName(trimmed))
            saveFoldersJson(root)
        }
        return folder
    }

    override fun updateFolderName(folderId: String, name: String) {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "Folder name must not be empty" }
        val now = System.currentTimeMillis()
        val oldFolder = _folders.value.find { it.id == folderId } ?: return
        val oldSafeName = VaultFileNames.safeFolderName(oldFolder.name)
        val newSafeName = VaultFileNames.safeFolderName(trimmed)

        _folders.update { list ->
            list.map { if (it.id == folderId) it.copy(name = trimmed, updatedAt = now) else it }
        }
        _notes.update { list ->
            list.map { note ->
                if (note.folderId == folderId) note.copy(lastModifiedEpochMs = now) else note
            }
        }

        scope.launch {
            val root = vaultRoot() ?: return@launch
            val nd = notesDir(root) ?: return@launch

            if (oldSafeName != newSafeName) {
                val oldDir = nd.findFile(oldSafeName)?.takeIf { it.isDirectory }
                val newDir = SafDocumentTree.findOrCreateDir(nd, newSafeName)
                if (oldDir != null && newDir != null) {
                    SafDocumentTree.listFiles(oldDir).forEach { noteFile ->
                        if (noteFile.name?.endsWith(VaultFileNames.NOTE_EXTENSION) == true) {
                            val content = SafDocumentTree.readText(context, noteFile.uri) ?: return@forEach
                            val dest = SafDocumentTree.getOrCreateFile(newDir, noteFile.name!!, VaultFileNames.NOTE_MIME)
                            if (dest != null) {
                                SafDocumentTree.writeText(context, dest.uri, content)
                                SafDocumentTree.deleteFile(noteFile)
                            }
                        }
                    }
                    if (SafDocumentTree.listFiles(oldDir).isEmpty()) SafDocumentTree.deleteFile(oldDir)
                }
            }

            // Persist updated folder name in front matter of affected notes
            val targetDir = nd.findFile(newSafeName)?.takeIf { it.isDirectory }
            if (targetDir != null) {
                _notes.value.filter { it.folderId == folderId }.forEach { note ->
                    val noteFile = SafDocumentTree.findFile(targetDir, VaultFileNames.noteFileName(note.id))
                        ?: return@forEach
                    SafDocumentTree.writeText(
                        context, noteFile.uri,
                        MarkdownNoteParser.serialize(
                            id = note.id, title = note.title,
                            createdAt = note.createdAt, updatedAt = now,
                            folderId = folderId, folderName = trimmed,
                            body = note.text,
                        )
                    )
                }
            }
            saveFoldersJson(root)
        }
    }

    override fun deleteFolder(folderId: String) {
        val now = System.currentTimeMillis()
        val folder = _folders.value.find { it.id == folderId } ?: return
        val safeName = VaultFileNames.safeFolderName(folder.name)

        _notes.update { list ->
            list.map { note ->
                if (note.folderId == folderId) note.copy(folderId = null, lastModifiedEpochMs = now) else note
            }
        }
        _folders.update { it.filter { f -> f.id != folderId } }

        scope.launch {
            val root = vaultRoot() ?: return@launch
            val nd = notesDir(root) ?: return@launch
            val folderDir = nd.findFile(safeName)?.takeIf { it.isDirectory }
            if (folderDir != null) {
                SafDocumentTree.listFiles(folderDir).forEach { noteFile ->
                    if (noteFile.name?.endsWith(VaultFileNames.NOTE_EXTENSION) == true) {
                        val rawContent = SafDocumentTree.readText(context, noteFile.uri) ?: return@forEach
                        val parsed = MarkdownNoteParser.parse(rawContent)
                        val updatedContent = if (parsed != null) {
                            MarkdownNoteParser.serialize(
                                id = parsed.id, title = parsed.title,
                                createdAt = parsed.createdAt, updatedAt = now,
                                folderId = null, folderName = null,
                                body = parsed.body,
                            )
                        } else rawContent
                        val dest = SafDocumentTree.getOrCreateFile(nd, noteFile.name!!, VaultFileNames.NOTE_MIME)
                        if (dest != null) {
                            SafDocumentTree.writeText(context, dest.uri, updatedContent)
                            SafDocumentTree.deleteFile(noteFile)
                        }
                    }
                }
                if (SafDocumentTree.listFiles(folderDir).isEmpty()) SafDocumentTree.deleteFile(folderDir)
            }
            saveFoldersJson(root)
        }
    }

    // ── Private file I/O helpers ──────────────────────────────────────────────

    /** Writes (or overwrites) the note's .md file at its current folder location. */
    private fun persistNote(noteId: String) {
        val root = vaultRoot() ?: return
        val note = _notes.value.find { it.id == noteId } ?: return
        val nd = notesDir(root) ?: return
        val targetDir = folderDir(nd, note.folderId)
        val folderName = note.folderId?.let { id -> _folders.value.find { it.id == id }?.name }
        writeNoteFile(targetDir, note, folderName)
    }

    private fun writeNoteFile(dir: DocumentFile, note: Note, folderName: String?) {
        val file = SafDocumentTree.getOrCreateFile(
            dir, VaultFileNames.noteFileName(note.id), VaultFileNames.NOTE_MIME
        ) ?: return
        val content = MarkdownNoteParser.serialize(
            id = note.id,
            title = note.title,
            createdAt = note.createdAt,
            updatedAt = note.lastModifiedEpochMs,
            folderId = note.folderId,
            folderName = folderName,
            body = note.text,
        )
        SafDocumentTree.writeText(context, file.uri, content)
    }

    private fun moveNoteFile(
        noteId: String,
        oldFolderId: String?,
        newFolderId: String?,
        notesDirectory: DocumentFile,
    ) {
        val note = _notes.value.find { it.id == noteId } ?: return
        val fileName = VaultFileNames.noteFileName(noteId)
        val folders = _folders.value
        val newFolderName = newFolderId?.let { id -> folders.find { it.id == id }?.name }

        // Locate the file in the existing tree (search old folder dir first, then root)
        val sourceFile = findNoteFile(notesDirectory, noteId)

        // Target directory
        val targetDir = folderDir(notesDirectory, newFolderId)

        val content = MarkdownNoteParser.serialize(
            id = note.id, title = note.title,
            createdAt = note.createdAt, updatedAt = note.lastModifiedEpochMs,
            folderId = newFolderId, folderName = newFolderName,
            body = note.text,
        )
        val targetFile = SafDocumentTree.getOrCreateFile(targetDir, fileName, VaultFileNames.NOTE_MIME) ?: return
        SafDocumentTree.writeText(context, targetFile.uri, content)

        if (sourceFile != null && sourceFile.uri != targetFile.uri) {
            SafDocumentTree.deleteFile(sourceFile)
        }
    }

    /** Returns the dir where a note with the given [folderId] should live. */
    private fun folderDir(notesDirectory: DocumentFile, folderId: String?): DocumentFile {
        if (folderId == null) return notesDirectory
        val folder = _folders.value.find { it.id == folderId } ?: return notesDirectory
        return SafDocumentTree.findOrCreateDir(notesDirectory, VaultFileNames.safeFolderName(folder.name))
            ?: notesDirectory
    }

    /** Searches the notes/ directory tree for a .md file with the given noteId. */
    private fun findNoteFile(notesDirectory: DocumentFile, noteId: String): DocumentFile? {
        val fileName = VaultFileNames.noteFileName(noteId)
        SafDocumentTree.findFile(notesDirectory, fileName)?.let { return it }
        SafDocumentTree.listFiles(notesDirectory).forEach { entry ->
            if (entry.isDirectory) {
                entry.findFile(fileName)?.takeIf { it.isFile }?.let { return it }
            }
        }
        return null
    }

    private fun saveFoldersJson(root: DocumentFile) {
        val metaDir = metadataDir(root) ?: return
        val file = SafDocumentTree.getOrCreateFile(
            metaDir, VaultFileNames.FOLDERS_JSON, VaultFileNames.JSON_MIME
        ) ?: return
        val metas = _folders.value.map { FolderMetadataParser.fromFolder(it) }
        SafDocumentTree.writeText(context, file.uri, FolderMetadataParser.serialize(metas))
    }
}
