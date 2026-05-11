package com.example.notesapp.features.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.notesapp.core.model.Folder
import com.example.notesapp.core.repository.NotesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

sealed class FolderFilter {
    data object All : FolderFilter()
    data object NoFolder : FolderFilter()
    data class InFolder(val folderId: String) : FolderFilter()
}

data class NoteCardUi(
    val id: String,
    val title: String,
    /** Первая осмысленная строка или обрезка текста для превью. */
    val previewText: String,
    val lastModifiedEpochMs: Long,
    val folderName: String?,
)

class NotesListViewModel(
    private val repository: NotesRepository,
) : ViewModel() {

    val folders: StateFlow<List<Folder>> = repository.folders
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    private val _selectedFilter = MutableStateFlow<FolderFilter>(FolderFilter.All)
    val selectedFilter: StateFlow<FolderFilter> = _selectedFilter.asStateFlow()

    fun setFilter(filter: FolderFilter) {
        _selectedFilter.value = filter
    }

    val noteCards: StateFlow<List<NoteCardUi>> = combine(
        repository.notes,
        repository.folders,
        _selectedFilter,
    ) { notes, folders, filter ->
        val folderById = folders.associateBy { it.id }
        notes
            .filter { note ->
                when (filter) {
                    FolderFilter.All -> true
                    FolderFilter.NoFolder -> note.folderId == null
                    is FolderFilter.InFolder -> note.folderId == filter.folderId
                }
            }
            .sortedByDescending { it.lastModifiedEpochMs }
            .map { note ->
                val preview = note.text
                    .trim()
                    .lines()
                    .firstOrNull { it.isNotBlank() }
                    ?.trim()
                    .orEmpty()
                    .let { line ->
                        if (line.length > PREVIEW_MAX_CHARS) {
                            line.take(PREVIEW_MAX_CHARS).trimEnd() + "…"
                        } else {
                            line
                        }
                    }
                NoteCardUi(
                    id = note.id,
                    title = note.title,
                    previewText = preview,
                    lastModifiedEpochMs = note.lastModifiedEpochMs,
                    folderName = note.folderId?.let { folderById[it]?.name },
                )
            }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    fun createNote(): String = repository.createNote().id

    companion object {
        private const val PREVIEW_MAX_CHARS = 140

        fun factory(repository: NotesRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass.isAssignableFrom(NotesListViewModel::class.java))
                    return NotesListViewModel(repository) as T
                }
            }
    }
}
