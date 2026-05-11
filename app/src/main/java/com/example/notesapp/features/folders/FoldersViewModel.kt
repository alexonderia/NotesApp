package com.example.notesapp.features.folders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.notesapp.core.repository.NotesRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class FolderUi(
    val id: String,
    val name: String,
    val noteCount: Int,
)

class FoldersViewModel(
    private val repository: NotesRepository,
) : ViewModel() {

    val folders: StateFlow<List<FolderUi>> = combine(
        repository.folders,
        repository.notes,
    ) { folders, notes ->
        folders.map { folder ->
            FolderUi(
                id = folder.id,
                name = folder.name,
                noteCount = notes.count { it.folderId == folder.id },
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    fun createFolder(name: String) {
        val trimmed = name.trim()
        if (trimmed.isNotEmpty()) {
            repository.createFolder(trimmed)
        }
    }

    fun renameFolder(id: String, name: String) {
        val trimmed = name.trim()
        if (trimmed.isNotEmpty()) {
            repository.updateFolderName(id, trimmed)
        }
    }

    fun deleteFolder(id: String) {
        repository.deleteFolder(id)
    }

    companion object {
        fun factory(repository: NotesRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass.isAssignableFrom(FoldersViewModel::class.java))
                    return FoldersViewModel(repository) as T
                }
            }
    }
}
