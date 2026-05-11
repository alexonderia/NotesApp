package com.example.notesapp.app.navigation

object AppRoutes {
    const val Notes = "notes"
    const val Folders = "folders"
    const val Editor = "editor/{noteId}"

    fun editorRoute(noteId: String) = "editor/$noteId"
}
