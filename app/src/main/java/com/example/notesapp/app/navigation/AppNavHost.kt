package com.example.notesapp.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.notesapp.core.repository.NotesRepository
import com.example.notesapp.features.editor.NoteEditorScreen
import com.example.notesapp.features.folders.FoldersScreen
import com.example.notesapp.features.notes.NotesListScreen
import com.example.notesapp.features.settings.SettingsScreen

@Composable
fun AppNavHost(
    repository: NotesRepository,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = AppRoutes.Notes,
        modifier = modifier,
    ) {
        composable(AppRoutes.Notes) {
            NotesListScreen(
                repository = repository,
                onOpenNote = { noteId -> navController.navigate(AppRoutes.editorRoute(noteId)) },
                onOpenFolders = { navController.navigate(AppRoutes.Folders) },
                onOpenSettings = { navController.navigate(AppRoutes.Settings) },
            )
        }
        composable(AppRoutes.Folders) {
            FoldersScreen(
                repository = repository,
                onBack = { navController.popBackStack() },
            )
        }
        composable(AppRoutes.Settings) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = AppRoutes.Editor,
            arguments = listOf(
                navArgument("noteId") { type = NavType.StringType },
            ),
        ) { entry ->
            val noteId = entry.arguments?.getString("noteId").orEmpty()
            NoteEditorScreen(
                repository = repository,
                noteId = noteId,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
