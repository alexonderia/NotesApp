package com.example.notesapp.features.notes

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import kotlinx.coroutines.flow.StateFlow

private val WideTwoColMinWidth = 600.dp
private val WideThreeColMinWidth = 960.dp
private val ContentMaxWidth = 1200.dp
private val GridSpacing = 16.dp
private val CardPadding = 16.dp
private val CardCorner = 20.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesListScreen(
    repository: NotesRepository,
    isVaultAvailable: StateFlow<Boolean>,
    onOpenNote: (String) -> Unit,
    onOpenFolders: () -> Unit,
    onOpenSettings: () -> Unit = {},
    viewModel: NotesListViewModel = viewModel(factory = NotesListViewModel.factory(repository)),
) {
    val noteCards by viewModel.noteCards.collectAsStateWithLifecycle()
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val selectedFilter by viewModel.selectedFilter.collectAsStateWithLifecycle()
    val vaultAvailable by isVaultAvailable.collectAsStateWithLifecycle()
    val dateFormatter = rememberNoteDateFormatter()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.notes_title),
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                actions = {
                    IconButton(onClick = onOpenFolders) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = stringResource(R.string.folders_open),
                        )
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.settings_title),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            )
        },
        floatingActionButton = {
            if (vaultAvailable) {
                FloatingActionButton(
                    onClick = {
                        val id = viewModel.createNote()
                        onOpenNote(id)
                    },
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.note_create),
                    )
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (!vaultAvailable) {
                NoVaultEmptyState(
                    onOpenSettings = onOpenSettings,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                )
                return@Column
            }

            FolderFilterRow(
                folders = folders,
                selected = selectedFilter,
                onSelect = viewModel::setFilter,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )

            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp),
            ) {
                val columnCount = when {
                    maxWidth >= WideThreeColMinWidth -> 3
                    maxWidth >= WideTwoColMinWidth -> 2
                    else -> 1
                }

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
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
    ) {
        LazyRow(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
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
                    label = { Text(folder.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
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

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(CardCorner),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
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
                maxLines = 3,
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
                            modifier = Modifier.size(16.dp),
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
    Surface(
        modifier = modifier.padding(24.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.EditNote,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.notes_empty_title),
                style = MaterialTheme.typography.titleLarge,
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
}

@Composable
private fun NoVaultEmptyState(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Default.FolderOff,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.secondary,
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.vault_no_folder_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.vault_no_folder_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onOpenSettings) {
            Text(stringResource(R.string.vault_open_settings))
        }
    }
}
