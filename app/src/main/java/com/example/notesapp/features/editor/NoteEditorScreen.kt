package com.example.notesapp.features.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberModalBottomSheetState
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

private val TabletBreakpoint = 700.dp

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

    var showPropertiesSheet by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    val propertiesSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
                title = {
                    Text(
                        note?.title ?: stringResource(R.string.note_unknown),
                        maxLines = 1,
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(
                                imageVector = Icons.Filled.MoreVert,
                                contentDescription = stringResource(R.string.editor_menu_more),
                            )
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.editor_note_properties)) },
                                onClick = {
                                    menuExpanded = false
                                    showPropertiesSheet = true
                                },
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.recognition_action_rerun_all)) },
                                onClick = {
                                    menuExpanded = false
                                    viewModel.recognizeAllHandwriting()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.recognition_action_by_lines)) },
                                onClick = {
                                    menuExpanded = false
                                    viewModel.recognizeBlocks()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.editor_clear)) },
                                onClick = {
                                    menuExpanded = false
                                    viewModel.onClearStrokes()
                                },
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .imePadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            val isTablet = maxWidth >= TabletBreakpoint
            if (isTablet) {
                if (editorMode == EditorMode.Text) {
                    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        EditorModeSelector(
                            editorMode = editorMode,
                            onModeChange = viewModel::setEditorMode,
                            modifier = Modifier.widthIn(max = 420.dp),
                        )
                        TextMirrorPanel(
                            note = note,
                            onManualTextChange = viewModel::onManualTextChange,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                        )
                    }
                } else {
                    TabletSplitEditorLayout(
                        note = note,
                        viewModel = viewModel,
                        recognitionState = recognitionState,
                        onSwitchToText = { viewModel.setEditorMode(EditorMode.Text) },
                    )
                }
            } else {
                CompactEditorLayout(
                    note = note,
                    editorMode = editorMode,
                    viewModel = viewModel,
                    recognitionState = recognitionState,
                )
            }
        }
    }

    if (showPropertiesSheet) {
        ModalBottomSheet(
            onDismissRequest = { showPropertiesSheet = false },
            sheetState = propertiesSheetState,
        ) {
            NotePropertiesSheetContent(
                note = note,
                folders = folders,
                onTitleChange = viewModel::onTitleChange,
                onFolderSelected = viewModel::onFolderSelected,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun NotePropertiesSheetContent(
    note: Note?,
    folders: List<Folder>,
    onTitleChange: (String) -> Unit,
    onFolderSelected: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = stringResource(R.string.editor_note_properties),
            style = MaterialTheme.typography.titleLarge,
        )
        OutlinedTextField(
            value = note?.title ?: "",
            onValueChange = onTitleChange,
            label = { Text(stringResource(R.string.editor_title_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        FolderSelector(
            selectedFolderId = note?.folderId,
            folders = folders,
            onFolderSelected = onFolderSelected,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun CompactEditorLayout(
    note: Note?,
    editorMode: EditorMode,
    viewModel: NoteEditorViewModel,
    recognitionState: RecognitionState,
) {
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        EditorModeSelector(
            editorMode = editorMode,
            onModeChange = viewModel::setEditorMode,
            modifier = Modifier.fillMaxWidth(),
        )
        when (editorMode) {
            EditorMode.Handwriting -> {
                HandwritingWorkspace(
                    note = note,
                    viewModel = viewModel,
                    recognitionState = recognitionState,
                    toolsModifier = Modifier.fillMaxWidth(),
                    canvasModifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
            }
            EditorMode.Text -> {
                TextMirrorPanel(
                    note = note,
                    onManualTextChange = viewModel::onManualTextChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
            }
        }
    }
}

@Composable
private fun TabletSplitEditorLayout(
    note: Note?,
    viewModel: NoteEditorViewModel,
    recognitionState: RecognitionState,
    onSwitchToText: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        EditorModeSelector(
            editorMode = EditorMode.Handwriting,
            onModeChange = { mode ->
                if (mode == EditorMode.Text) onSwitchToText()
            },
            modifier = Modifier.widthIn(max = 420.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                modifier = Modifier
                    .weight(0.58f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HandwritingWorkspace(
                    note = note,
                    viewModel = viewModel,
                    recognitionState = recognitionState,
                    toolsModifier = Modifier.widthIn(max = 480.dp),
                    canvasModifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
            }
            VerticalDivider(modifier = Modifier.fillMaxHeight().padding(vertical = 4.dp))
            Column(
                modifier = Modifier
                    .weight(0.42f)
                    .fillMaxHeight(),
            ) {
                Text(
                    text = stringResource(R.string.editor_text_panel_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                TextMirrorPanel(
                    note = note,
                    onManualTextChange = viewModel::onManualTextChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
            }
        }
    }
}

@Composable
private fun EditorModeSelector(
    editorMode: EditorMode,
    onModeChange: (EditorMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier) {
        SegmentedButton(
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            onClick = { onModeChange(EditorMode.Handwriting) },
            selected = editorMode == EditorMode.Handwriting,
        ) {
            Text(stringResource(R.string.editor_mode_handwriting))
        }
        SegmentedButton(
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            onClick = { onModeChange(EditorMode.Text) },
            selected = editorMode == EditorMode.Text,
        ) {
            Text(stringResource(R.string.editor_mode_text))
        }
    }
}

@Composable
private fun HandwritingWorkspace(
    note: Note?,
    viewModel: NoteEditorViewModel,
    recognitionState: RecognitionState,
    toolsModifier: Modifier,
    canvasModifier: Modifier,
) {
    val strokes = note?.strokes ?: emptyList()
    val penStrokes = strokes.penStrokesOnly()
    val recognizedIds = note?.recognizedStrokeIds ?: emptySet()
    val hasNewStrokes = penStrokes.any { it.id !in recognizedIds }
    val isBusy = recognitionState == RecognitionState.Recognizing ||
        recognitionState == RecognitionState.DownloadingModel

    val selectedTool by viewModel.selectedTool.collectAsStateWithLifecycle()
    val selectedColor by viewModel.selectedColor.collectAsStateWithLifecycle()
    val selectedWidth by viewModel.selectedWidth.collectAsStateWithLifecycle()
    val redoStack by viewModel.redoStack.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
            modifier = toolsModifier,
        )
        RecognitionPrimaryRow(
            penStrokesNotEmpty = penStrokes.isNotEmpty(),
            isBusy = isBusy,
            onPrimaryRecognize = {
                if (hasNewStrokes) viewModel.recognizeNewHandwriting()
                else viewModel.recognizeAllHandwriting()
            },
        )
        if (isBusy) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                Text(
                    text = when (recognitionState) {
                        RecognitionState.DownloadingModel ->
                            stringResource(R.string.recognition_downloading_model)
                        RecognitionState.Recognizing ->
                            stringResource(R.string.recognition_recognizing)
                        else -> ""
                    },
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
            modifier = canvasModifier,
        )
    }
}

@Composable
private fun RecognitionPrimaryRow(
    penStrokesNotEmpty: Boolean,
    isBusy: Boolean,
    onPrimaryRecognize: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilledTonalButton(
            onClick = onPrimaryRecognize,
            enabled = penStrokesNotEmpty && !isBusy,
            modifier = Modifier.widthIn(min = 160.dp),
        ) {
            Text(stringResource(R.string.recognition_action_primary))
        }
    }
}

@Composable
private fun TextMirrorPanel(
    note: Note?,
    onManualTextChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val current = note
    if (current == null) {
        Text(text = stringResource(R.string.note_not_found))
        return
    }
    val scroll = rememberScrollState()
    Column(
        modifier = modifier.verticalScroll(scroll),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = current.recognizedText,
            onValueChange = {},
            readOnly = true,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.editor_recognized_label)) },
            placeholder = { Text(stringResource(R.string.editor_recognized_empty_hint)) },
            minLines = 4,
        )
        OutlinedTextField(
            value = current.manualText,
            onValueChange = onManualTextChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.editor_manual_label)) },
            placeholder = { Text(stringResource(R.string.editor_manual_hint)) },
            minLines = 4,
        )
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
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToolSelector(
                selectedTool = selectedTool,
                onToolChange = onToolChange,
                enabled = enabled,
            )
            HistoryActions(
                canUndo = canUndo,
                canRedo = canRedo,
                canClear = canClear,
                onUndo = onUndo,
                onRedo = onRedo,
                onClear = onClear,
                enabled = enabled,
            )
        }
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StrokeWidthSelector(
                selectedWidth = selectedWidth,
                onWidthChange = onWidthChange,
                enabled = enabled,
            )
        }
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ColorSelector(
                selectedColor = selectedColor,
                onColorChange = onColorChange,
                enabled = enabled,
            )
        }
    }
}

@Composable
private fun ToolSelector(
    selectedTool: ToolType,
    onToolChange: (ToolType) -> Unit,
    enabled: Boolean,
) {
    FilterChip(
        selected = selectedTool == ToolType.Pen,
        onClick = { onToolChange(ToolType.Pen) },
        label = { Text(stringResource(R.string.editor_tool_pen)) },
        leadingIcon = {
            Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
        },
        enabled = enabled,
    )
    FilterChip(
        selected = selectedTool == ToolType.Eraser,
        onClick = { onToolChange(ToolType.Eraser) },
        label = { Text(stringResource(R.string.editor_tool_eraser)) },
        leadingIcon = {
            Icon(Icons.Outlined.RemoveCircleOutline, contentDescription = null, modifier = Modifier.size(18.dp))
        },
        enabled = enabled,
    )
}

@Composable
private fun StrokeWidthSelector(
    selectedWidth: Float,
    onWidthChange: (Float) -> Unit,
    enabled: Boolean,
) {
    val widths = listOf(2f, 4f, 8f, 12f)
    widths.forEach { w ->
        FilterChip(
            selected = kotlin.math.abs(selectedWidth - w) < 0.5f,
            onClick = { onWidthChange(w) },
            label = { Text("${w.toInt()}") },
            enabled = enabled,
        )
    }
}

@Composable
private fun ColorSelector(
    selectedColor: Long,
    onColorChange: (Long) -> Unit,
    enabled: Boolean,
) {
    val palette = listOf(
        0xFF000000L,
        0xFF1976D2L,
        0xFFD32F2FL,
        0xFF388E3CL,
    )
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

@Composable
private fun HistoryActions(
    canUndo: Boolean,
    canRedo: Boolean,
    canClear: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onClear: () -> Unit,
    enabled: Boolean,
) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FolderSelector(
    selectedFolderId: String?,
    folders: List<Folder>,
    onFolderSelected: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showFolderPicker by remember { mutableStateOf(false) }
    val selectedName = folders.find { it.id == selectedFolderId }?.name
        ?: stringResource(R.string.folder_none)

    OutlinedTextField(
        value = selectedName,
        onValueChange = {},
        readOnly = true,
        label = { Text(stringResource(R.string.label_folder)) },
        trailingIcon = {
            IconButton(onClick = { showFolderPicker = true }) {
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = stringResource(R.string.label_folder),
                )
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .clickable { showFolderPicker = true },
    )

    if (showFolderPicker) {
        AlertDialog(
            onDismissRequest = { showFolderPicker = false },
            title = { Text(stringResource(R.string.label_folder)) },
            text = {
                val scroll = rememberScrollState()
                Column(
                    modifier = Modifier.verticalScroll(scroll),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    TextButton(
                        onClick = {
                            onFolderSelected(null)
                            showFolderPicker = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.folder_none))
                    }
                    HorizontalDivider()
                    folders.forEach { folder ->
                        TextButton(
                            onClick = {
                                onFolderSelected(folder.id)
                                showFolderPicker = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(folder.name)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showFolderPicker = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}
