package com.example.notesapp.app

import androidx.compose.runtime.Composable
import com.example.notesapp.app.navigation.AppNavHost
import com.example.notesapp.core.repository.NotesRepository
import com.example.notesapp.core.ui.AppTheme

@Composable
fun HandNotesApp(repository: NotesRepository) {
    AppTheme {
        AppNavHost(repository = repository)
    }
}
