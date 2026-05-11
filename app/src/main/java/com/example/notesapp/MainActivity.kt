package com.example.notesapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.notesapp.app.HandNotesApp
import com.example.notesapp.core.repository.SafFileNotesRepository
import com.example.notesapp.core.vault.VaultManager

class MainActivity : ComponentActivity() {

    private val vaultManager by lazy { VaultManager(applicationContext) }

    private val notesRepository by lazy {
        SafFileNotesRepository(applicationContext, vaultManager)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HandNotesApp(
                repository = notesRepository,
                isVaultAvailable = notesRepository.isVaultAvailable,
            )
        }
    }
}
