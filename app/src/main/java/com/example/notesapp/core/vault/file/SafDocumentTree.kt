package com.example.notesapp.core.vault.file

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile

object SafDocumentTree {

    private const val TAG = "SafDocumentTree"

    fun findOrCreateDir(parent: DocumentFile, name: String): DocumentFile? =
        parent.findFile(name)?.takeIf { it.isDirectory }
            ?: parent.createDirectory(name)

    /** Returns an existing file child by name, or null (does not create). */
    fun findFile(parent: DocumentFile, name: String): DocumentFile? =
        parent.findFile(name)?.takeIf { it.isFile }

    /** Returns an existing file child by name, creating it if it does not exist. */
    fun getOrCreateFile(parent: DocumentFile, name: String, mimeType: String): DocumentFile? =
        parent.findFile(name)?.takeIf { it.isFile }
            ?: parent.createFile(mimeType, name)

    fun readText(context: Context, uri: Uri): String? = try {
        context.contentResolver.openInputStream(uri)
            ?.bufferedReader(Charsets.UTF_8)
            ?.use { it.readText() }
    } catch (e: Exception) {
        Log.w(TAG, "readText failed for $uri: ${e.message}")
        null
    }

    fun writeText(context: Context, uri: Uri, text: String) {
        try {
            // "wt" = write + truncate
            context.contentResolver.openOutputStream(uri, "wt")
                ?.bufferedWriter(Charsets.UTF_8)
                ?.use { it.write(text) }
        } catch (e: Exception) {
            Log.e(TAG, "writeText failed for $uri: ${e.message}")
        }
    }

    fun deleteFile(file: DocumentFile): Boolean = try {
        file.delete()
    } catch (e: Exception) {
        Log.w(TAG, "deleteFile failed for ${file.uri}: ${e.message}")
        false
    }

    fun listFiles(dir: DocumentFile): List<DocumentFile> = try {
        dir.listFiles().toList()
    } catch (e: Exception) {
        emptyList()
    }
}
