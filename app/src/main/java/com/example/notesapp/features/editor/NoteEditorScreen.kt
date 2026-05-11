package com.example.notesapp.features.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.notesapp.R
import com.example.notesapp.core.repository.NotesRepository
import com.example.notesapp.features.editor.components.HandwritingCanvas

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(
    repository: NotesRepository,
    noteId: String,
    onBack: () -> Unit,
    viewModel: NoteEditorViewModel = viewModel(
        key = noteId,
        factory = NoteEditorViewModel.factory(repository, noteId),
    ),
) {
    val note by viewModel.note.collectAsStateWithLifecycle()
    val editorMode by viewModel.editorMode.collectAsStateWithLifecycle()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
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
        ) {
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

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilledTonalIconButton(
                            onClick = viewModel::onUndoStroke,
                            enabled = strokes.isNotEmpty(),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Undo,
                                contentDescription = "Undo",
                            )
                        }
                        FilledTonalIconButton(
                            onClick = viewModel::onClearStrokes,
                            enabled = strokes.isNotEmpty(),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = "Clear",
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
                        Text(
                            text = stringResource(R.string.note_not_found),
                            modifier = Modifier.padding(top = 16.dp),
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(top = 16.dp),
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
