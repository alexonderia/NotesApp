package com.example.notesapp.app

import androidx.compose.runtime.Composable
import com.example.notesapp.app.navigation.AppNavHost
import com.example.notesapp.core.repository.NotesRepository
import com.example.notesapp.core.ui.AppTheme
import kotlinx.coroutines.flow.StateFlow

@Composable
fun HandNotesApp(
    repository: NotesRepository,
    isVaultAvailable: StateFlow<Boolean>,
) {
    AppTheme {
        AppNavHost(
            repository = repository,
            isVaultAvailable = isVaultAvailable,
        )
    }
}
