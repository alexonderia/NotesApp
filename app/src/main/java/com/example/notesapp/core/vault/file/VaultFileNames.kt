package com.example.notesapp.core.vault.file

object VaultFileNames {
    const val NOTES_DIR = "notes"
    const val INK_DIR = "ink"
    const val METADATA_DIR = "metadata"
    const val FOLDERS_JSON = "folders.json"

    const val NOTE_EXTENSION = ".md"
    const val INK_SUFFIX = ".ink.json"

    /** Generic MIME type prevents providers from appending their own extension. */
    const val NOTE_MIME = "application/octet-stream"
    const val INK_MIME = "application/octet-stream"
    const val JSON_MIME = "application/octet-stream"

    fun noteFileName(noteId: String) = "$noteId$NOTE_EXTENSION"
    fun inkFileName(noteId: String) = "$noteId$INK_SUFFIX"

    /** Sanitizes a folder name so it can be used as a directory name on any Android FS. */
    fun safeFolderName(name: String): String =
        name.replace(Regex("""[/\\:*?"<>|\u0000]"""), "_")
            .trim('.')
            .trim()
            .ifEmpty { "untitled" }
            .take(200)
}
