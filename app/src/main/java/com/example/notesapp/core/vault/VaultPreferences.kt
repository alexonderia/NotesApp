package com.example.notesapp.core.vault

import android.content.Context
import android.net.Uri

class VaultPreferences(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getVaultUri(): String? = prefs.getString(KEY_VAULT_URI, null)

    fun saveVaultUri(uri: Uri) {
        prefs.edit().putString(KEY_VAULT_URI, uri.toString()).apply()
    }

    fun clearVaultUri() {
        prefs.edit().remove(KEY_VAULT_URI).apply()
    }

    companion object {
        const val PREFS_NAME = "vault_prefs"
        const val KEY_VAULT_URI = "vault_uri"
    }
}
