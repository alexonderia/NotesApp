package com.example.notesapp.core.vault

data class VaultState(
    val selected: Boolean = false,
    val uri: String? = null,
    val displayName: String? = null,
    val accessAvailable: Boolean = false,
    val errorMessage: String? = null,
)
