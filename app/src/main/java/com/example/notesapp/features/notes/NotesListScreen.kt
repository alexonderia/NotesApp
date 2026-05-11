package com.example.notesapp.features.notes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.notesapp.R
import com.example.notesapp.core.model.Folder
import com.example.notesapp.core.repository.NotesRepository
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val WideLayoutMinWidth = 600.dp
private val ContentMaxWidth = 880.dp
private val GridSpacing = 16.dp
private val CardPadding = 16.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesListScreen(
    repository: NotesRepository,
    onOpenNote: (String) -> Unit,
    onOpenFolders: () -> Unit,
    viewModel: NotesListViewModel = viewModel(factory = NotesListViewModel.factory(repository)),
) {
    val noteCards by viewModel.noteCards.collectAsStateWithLifecycle()
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val selectedFilter by viewModel.selectedFilter.collectAsStateWithLifecycle()
    val dateFormatter = rememberNoteDateFormatter()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.notes_title)) },
                actions = {
                    IconButton(onClick = onOpenFolders) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = stringResource(R.string.folders_open),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    val id = viewModel.createNote()
                    onOpenNote(id)
                },
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.note_create),
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            FolderFilterRow(
                folders = folders,
                selected = selectedFilter,
                onSelect = viewModel::setFilter,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            )

            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(vertical = 8.dp),
            ) {
                val wide = maxWidth >= WideLayoutMinWidth
                val columnCount = if (wide) 2 else 1

                if (noteCards.isEmpty()) {
                    NotesListEmptyState(
                        modifier = Modifier
                            .widthIn(max = ContentMaxWidth)
                            .fillMaxWidth()
                            .align(Alignment.Center),
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columnCount),
                        horizontalArrangement = Arrangement.spacedBy(GridSpacing),
                        verticalArrangement = Arrangement.spacedBy(GridSpacing),
                        modifier = Modifier
                            .widthIn(max = ContentMaxWidth)
                            .fillMaxWidth()
                            .align(Alignment.TopCenter),
                    ) {
                        items(
                            items = noteCards,
                            key = { it.id },
                        ) { card ->
                            NoteSummaryCard(
                                card = card,
                                dateFormatter = dateFormatter,
                                onClick = { onOpenNote(card.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FolderFilterRow(
    folders: List<Folder>,
    selected: FolderFilter,
    onSelect: (FolderFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            FilterChip(
                selected = selected is FolderFilter.All,
                onClick = { onSelect(FolderFilter.All) },
                label = { Text(stringResource(R.string.filter_all)) },
            )
        }
        item {
            FilterChip(
                selected = selected is FolderFilter.NoFolder,
                onClick = { onSelect(FolderFilter.NoFolder) },
                label = { Text(stringResource(R.string.folder_none)) },
            )
        }
        items(items = folders, key = { it.id }) { folder ->
            FilterChip(
                selected = selected is FolderFilter.InFolder && selected.folderId == folder.id,
                onClick = { onSelect(FolderFilter.InFolder(folder.id)) },
                label = { Text(folder.name) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                },
            )
        }
    }
}

@Composable
private fun rememberNoteDateFormatter(): DateTimeFormatter {
    val locale = Locale.getDefault()
    return remember(locale) {
        DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", locale)
    }
}

@Composable
private fun formatModifiedAt(
    epochMs: Long,
    formatter: DateTimeFormatter,
): String {
    val z = ZoneId.systemDefault()
    val dt = Instant.ofEpochMilli(epochMs).atZone(z).toLocalDateTime()
    return formatter.format(dt)
}

@Composable
private fun NoteSummaryCard(
    card: NoteCardUi,
    dateFormatter: DateTimeFormatter,
    onClick: () -> Unit,
) {
    val preview = card.previewText.ifBlank {
        stringResource(R.string.note_no_text)
    }
    val dateLabel = formatModifiedAt(card.lastModifiedEpochMs, dateFormatter)

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(CardPadding)) {
            Text(
                text = card.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = preview,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val folder = card.folderName
                if (folder != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f, fill = false),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = folder,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }
                Text(
                    text = dateLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun NotesListEmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.EditNote,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.secondary,
        )
        Text(
            text = stringResource(R.string.notes_empty_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.notes_empty_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
