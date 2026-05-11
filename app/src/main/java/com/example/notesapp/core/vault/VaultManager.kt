package com.example.notesapp.core.vault

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

class VaultManager(private val context: Context) {

    private val preferences = VaultPreferences(context)

    fun getSavedVaultState(): VaultState {
        val uriString = preferences.getVaultUri() ?: return VaultState()
        val uri = Uri.parse(uriString)
        val accessible = hasAccess(uri)
        val displayName = getDisplayName(uri)
        return VaultState(
            selected = true,
            uri = uriString,
            displayName = displayName,
            accessAvailable = accessible,
            errorMessage = if (!accessible) context.getString(
                com.example.notesapp.R.string.vault_error_access
            ) else null,
        )
    }

    fun saveSelectedVaultUri(uri: Uri) {
        preferences.saveVaultUri(uri)
    }

    fun ensureVaultStructure(uri: Uri): Boolean {
        val root = DocumentFile.fromTreeUri(context, uri) ?: return false
        if (!root.canWrite()) return false
        listOf("notes", "ink", "metadata").forEach { dirName ->
            if (root.findFile(dirName) == null) {
                root.createDirectory(dirName)
            }
        }
        return true
    }

    fun hasAccess(uri: Uri): Boolean {
        return try {
            val docFile = DocumentFile.fromTreeUri(context, uri)
            docFile?.exists() == true && docFile.canRead()
        } catch (e: Exception) {
            false
        }
    }

    fun clearVault() {
        preferences.clearVaultUri()
    }

    private fun getDisplayName(uri: Uri): String? {
        return try {
            DocumentFile.fromTreeUri(context, uri)?.name
        } catch (e: Exception) {
            null
        }
    }
}
