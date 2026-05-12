package com.example.notesapp.features.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.notesapp.R
import com.example.notesapp.core.model.Folder
import com.example.notesapp.core.model.Note
import com.example.notesapp.core.model.ToolType
import com.example.notesapp.core.model.penStrokesOnly
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
    val noNewStrokesMessage = stringResource(R.string.recognition_no_new_strokes)

    LaunchedEffect(recognitionState) {
        when (val state = recognitionState) {
            is RecognitionState.Success -> {
                snackbarHostState.showSnackbar(successMessage)
                viewModel.dismissRecognitionState()
            }
            is RecognitionState.Error -> {
                val msg = when (state.message) {
                    "no_strokes" -> noStrokesMessage
                    "no_new_strokes" -> noNewStrokesMessage
                    else -> state.message
                }
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
                    val penStrokes = strokes.penStrokesOnly()
                    val recognizedIds = note?.recognizedStrokeIds ?: emptySet()
                    val hasNewStrokes = penStrokes.any { it.id !in recognizedIds }
                    val recognizedOnCanvas = penStrokes.count { it.id in recognizedIds }
                    val isBusy = recognitionState == RecognitionState.Recognizing ||
                        recognitionState == RecognitionState.DownloadingModel

                    val selectedTool by viewModel.selectedTool.collectAsStateWithLifecycle()
                    val selectedColor by viewModel.selectedColor.collectAsStateWithLifecycle()
                    val selectedWidth by viewModel.selectedWidth.collectAsStateWithLifecycle()
                    val redoStack by viewModel.redoStack.collectAsStateWithLifecycle()

                    HandwritingToolbar(
                        selectedTool = selectedTool,
                        onToolChange = viewModel::setSelectedTool,
                        selectedWidth = selectedWidth,
                        onWidthChange = viewModel::setSelectedWidth,
                        selectedColor = selectedColor,
                        onColorChange = viewModel::setSelectedColor,
                        canUndo = strokes.isNotEmpty(),
                        canRedo = redoStack.isNotEmpty(),
                        onUndo = viewModel::onUndoStroke,
                        onRedo = viewModel::onRedoStroke,
                        onClear = viewModel::onClearStrokes,
                        canClear = strokes.isNotEmpty(),
                        enabled = !isBusy,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Button(
                            onClick = viewModel::recognizeNewHandwriting,
                            enabled = hasNewStrokes && !isBusy,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.recognition_action_new))
                        }
                        OutlinedButton(
                            onClick = viewModel::recognizeAllHandwriting,
                            enabled = penStrokes.isNotEmpty() && !isBusy,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.recognition_action_rerun_all))
                        }
                    }

                    Text(
                        text = stringResource(
                            R.string.recognition_strokes_status,
                            recognizedOnCanvas,
                            penStrokes.size,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Text(
                        text = stringResource(
                            R.string.recognition_blocks_status,
                            note?.handwritingBlocks?.size ?: 0,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    OutlinedButton(
                        onClick = viewModel::recognizeBlocks,
                        enabled = penStrokes.isNotEmpty() && !isBusy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.recognition_action_by_lines))
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
                        selectedTool = selectedTool,
                        penColor = selectedColor,
                        penWidth = selectedWidth,
                        eraserRadius = 40f,
                        onPenStrokeFinished = viewModel::onStrokeAdded,
                        onStrokesReplace = viewModel::onStrokesReplaceAfterErase,
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
                        val scroll = rememberScrollState()
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .verticalScroll(scroll),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            OutlinedTextField(
                                value = current.recognizedText,
                                onValueChange = {},
                                readOnly = true,
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(stringResource(R.string.editor_recognized_label)) },
                                placeholder = {
                                    Text(stringResource(R.string.editor_recognized_empty_hint))
                                },
                                minLines = 4,
                            )
                            OutlinedTextField(
                                value = current.manualText,
                                onValueChange = viewModel::onManualTextChange,
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(stringResource(R.string.editor_manual_label)) },
                                placeholder = { Text(stringResource(R.string.editor_manual_hint)) },
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
private fun HandwritingToolbar(
    selectedTool: ToolType,
    onToolChange: (ToolType) -> Unit,
    selectedWidth: Float,
    onWidthChange: (Float) -> Unit,
    selectedColor: Long,
    onColorChange: (Long) -> Unit,
    canUndo: Boolean,
    canRedo: Boolean,
    canClear: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onClear: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val widths = listOf(2f, 4f, 8f, 12f)
    val palette = listOf(
        0xFF000000L,
        0xFF1976D2L,
        0xFFD32F2FL,
        0xFF388E3CL,
    )
    val scroll = rememberScrollState()

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.horizontalScroll(scroll),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = selectedTool == ToolType.Pen,
                onClick = { onToolChange(ToolType.Pen) },
                label = { Text(stringResource(R.string.editor_tool_pen)) },
                leadingIcon = {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
                enabled = enabled,
            )
            FilterChip(
                selected = selectedTool == ToolType.Eraser,
                onClick = { onToolChange(ToolType.Eraser) },
                label = { Text(stringResource(R.string.editor_tool_eraser)) },
                leadingIcon = {
                    Icon(
                        Icons.Outlined.RemoveCircleOutline,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
                enabled = enabled,
            )

            FilledTonalIconButton(onClick = onUndo, enabled = canUndo && enabled) {
                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = stringResource(R.string.editor_undo))
            }
            FilledTonalIconButton(onClick = onRedo, enabled = canRedo && enabled) {
                Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = stringResource(R.string.editor_redo))
            }
            FilledTonalIconButton(onClick = onClear, enabled = canClear && enabled) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.editor_clear))
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            widths.forEach { w ->
                FilterChip(
                    selected = kotlin.math.abs(selectedWidth - w) < 0.5f,
                    onClick = { onWidthChange(w) },
                    label = { Text("${w.toInt()}") },
                    enabled = enabled,
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            palette.forEach { c ->
                val selected = selectedColor == c
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(c))
                        .border(
                            width = if (selected) 3.dp else 1.dp,
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                            },
                            shape = CircleShape,
                        )
                        .clickable(enabled = enabled) { onColorChange(c) },
                )
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
