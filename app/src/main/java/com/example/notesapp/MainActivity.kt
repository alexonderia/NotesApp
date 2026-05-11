package com.example.notesapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.notesapp.app.HandNotesApp
import com.example.notesapp.core.repository.InMemoryNotesRepository
import com.example.notesapp.core.repository.NotesRepository

class MainActivity : ComponentActivity() {

    private val notesRepository: NotesRepository = InMemoryNotesRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HandNotesApp(repository = notesRepository)
        }
    }
}
