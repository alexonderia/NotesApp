package com.example.notesapp.features.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.notesapp.R
import com.example.notesapp.core.model.Folder
import com.example.notesapp.core.recognition.HandwritingRecognitionService
import com.example.notesapp.core.recognition.RecognitionState
import com.example.notesapp.core.repository.NotesRepository
import com.example.notesapp.features.editor.components.HandwritingCanvas

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(
    repository: NotesRepository,
    noteId: String,
    recognitionService: HandwritingRecognitionService,
    onBack: () -> Unit,
    viewModel: NoteEditorViewModel = viewModel(
        key = noteId,
        factory = NoteEditorViewModel.factory(repository, noteId, recognitionService),
    ),
) {
    val note by viewModel.note.collectAsStateWithLifecycle()
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val editorMode by viewModel.editorMode.collectAsStateWithLifecycle()
    val recognitionState by viewModel.recognitionState.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val successMessage = stringResource(R.string.recognition_success)
    val noStrokesMessage = stringResource(R.string.recognition_no_strokes)

    LaunchedEffect(recognitionState) {
        when (val state = recognitionState) {
            is RecognitionState.Success -> {
                snackbarHostState.showSnackbar(successMessage)
                viewModel.dismissRecognitionState()
            }
            is RecognitionState.Error -> {
                val msg = if (state.message == "no_strokes") noStrokesMessage else state.message
                snackbarHostState.showSnackbar(msg)
                viewModel.dismissRecognitionState()
            }
            else -> Unit
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(note?.title ?: stringResource(R.string.note_unknown)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .imePadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = note?.title ?: "",
                onValueChange = viewModel::onTitleChange,
                label = { Text(stringResource(R.string.editor_title_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            FolderSelector(
                selectedFolderId = note?.folderId,
                folders = folders,
                onFolderSelected = viewModel::onFolderSelected,
                modifier = Modifier.fillMaxWidth(),
            )

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    onClick = { viewModel.setEditorMode(EditorMode.Handwriting) },
                    selected = editorMode == EditorMode.Handwriting,
                ) {
                    Text(stringResource(R.string.editor_mode_handwriting))
                }
                SegmentedButton(
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    onClick = { viewModel.setEditorMode(EditorMode.Text) },
                    selected = editorMode == EditorMode.Text,
                ) {
                    Text(stringResource(R.string.editor_mode_text))
                }
            }

            when (editorMode) {
                EditorMode.Handwriting -> {
                    val strokes = note?.strokes ?: emptyList()
                    val isBusy = recognitionState == RecognitionState.Recognizing ||
                        recognitionState == RecognitionState.DownloadingModel

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FilledTonalIconButton(
                            onClick = viewModel::onUndoStroke,
                            enabled = strokes.isNotEmpty() && !isBusy,
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Undo,
                                contentDescription = stringResource(R.string.editor_undo),
                            )
                        }
                        FilledTonalIconButton(
                            onClick = viewModel::onClearStrokes,
                            enabled = strokes.isNotEmpty() && !isBusy,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = stringResource(R.string.editor_clear),
                            )
                        }
                        Button(
                            onClick = viewModel::recognizeCurrentHandwriting,
                            enabled = strokes.isNotEmpty() && !isBusy,
                        ) {
                            Text(stringResource(R.string.recognition_action))
                        }
                    }

                    if (isBusy) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp))
                            val statusText = when (recognitionState) {
                                RecognitionState.DownloadingModel ->
                                    stringResource(R.string.recognition_downloading_model)
                                RecognitionState.Recognizing ->
                                    stringResource(R.string.recognition_recognizing)
                                else -> ""
                            }
                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    HandwritingCanvas(
                        strokes = strokes,
                        onStrokeFinished = viewModel::onStrokeAdded,
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                    )
                }

                EditorMode.Text -> {
                    val current = note
                    if (current == null) {
                        Text(text = stringResource(R.string.note_not_found))
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                        ) {
                            OutlinedTextField(
                                value = current.text,
                                onValueChange = viewModel::onTextChange,
                                modifier = Modifier.fillMaxSize(),
                                placeholder = { Text(stringResource(R.string.editor_text_hint)) },
                                minLines = 4,
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
private fun FolderSelector(
    selectedFolderId: String?,
    folders: List<Folder>,
    onFolderSelected: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = folders.find { it.id == selectedFolderId }?.name
        ?: stringResource(R.string.folder_none)

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.label_folder)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.folder_none)) },
                onClick = {
                    onFolderSelected(null)
                    expanded = false
                },
            )
            folders.forEach { folder ->
                DropdownMenuItem(
                    text = { Text(folder.name) },
                    onClick = {
                        onFolderSelected(folder.id)
                        expanded = false
                    },
                )
            }
        }
    }
}
