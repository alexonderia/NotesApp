package com.example.notesapp.features.editor

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
import kotlin.math.abs

private val TabletBreakpoint = 700.dp
private val TabletPaperMaxWidth = 760.dp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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
    var showClearHandwritingDialog by remember { mutableStateOf(false) }
    val propertiesSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val pagerState = rememberPagerState(pageCount = { 2 })

    LaunchedEffect(editorMode) {
        val target = editorMode.ordinal
        if (pagerState.currentPage != target) {
            pagerState.animateScrollToPage(target)
        }
    }

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
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = note?.title ?: stringResource(R.string.note_unknown),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
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
                    IconButton(
                        onClick = { showPropertiesSheet = true },
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.EditNote,
                            contentDescription = stringResource(R.string.editor_note_properties),
                        )
                    }
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
                                    showClearHandwritingDialog = true
                                },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
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
            val paperMaxWidth = if (maxWidth >= TabletBreakpoint) TabletPaperMaxWidth else null
            EditorTwoPagesLayout(
                editorMode = editorMode,
                onEditorModeChange = viewModel::setEditorMode,
                pagerState = pagerState,
                note = note,
                viewModel = viewModel,
                recognitionState = recognitionState,
                paperMaxWidth = paperMaxWidth,
            )
        }
    }

    if (showPropertiesSheet) {
        ModalBottomSheet(
            onDismissRequest = { showPropertiesSheet = false },
            sheetState = propertiesSheetState,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        ) {
            NotePropertiesSheetContent(
                note = note,
                folders = folders,
                onTitleChange = viewModel::onTitleChange,
                onFolderSelected = viewModel::onFolderSelected,
                onDone = { showPropertiesSheet = false },
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showClearHandwritingDialog) {
        AlertDialog(
            onDismissRequest = { showClearHandwritingDialog = false },
            title = { Text(stringResource(R.string.editor_clear_confirm_title)) },
            text = { Text(stringResource(R.string.editor_clear_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearHandwritingDialog = false
                        viewModel.onClearStrokes()
                    },
                ) {
                    Text(stringResource(R.string.editor_clear_confirm_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHandwritingDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun EditorTwoPagesLayout(
    editorMode: EditorMode,
    onEditorModeChange: (EditorMode) -> Unit,
    pagerState: PagerState,
    note: Note?,
    viewModel: NoteEditorViewModel,
    recognitionState: RecognitionState,
    paperMaxWidth: Dp?,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        PrimaryTabRow(
            selectedTabIndex = editorMode.ordinal,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Tab(
                selected = editorMode == EditorMode.Handwriting,
                onClick = { onEditorModeChange(EditorMode.Handwriting) },
                text = { Text(stringResource(R.string.editor_mode_handwriting)) },
            )
            Tab(
                selected = editorMode == EditorMode.Text,
                onClick = { onEditorModeChange(EditorMode.Text) },
                text = { Text(stringResource(R.string.editor_mode_printed)) },
            )
        }
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            userScrollEnabled = false,
            beyondViewportPageCount = 1,
        ) { page ->
            when (page) {
                0 -> {
                    Column(Modifier.fillMaxSize()) {
                        HandwritingWorkspace(
                            note = note,
                            viewModel = viewModel,
                            recognitionState = recognitionState,
                            toolsModifier = Modifier.fillMaxWidth(),
                            canvasModifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            paperMaxWidth = paperMaxWidth,
                        )
                    }
                }
                else -> {
                    TypedTextPage(
                        note = note,
                        onManualTextChange = viewModel::onManualTextChange,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

@Composable
private fun TypedTextPage(
    note: Note?,
    onManualTextChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp, bottomStart = 20.dp, bottomEnd = 20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
    ) {
        TextMirrorPanel(
            note = note,
            onManualTextChange = onManualTextChange,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            showSectionTitle = true,
        )
    }
}

@Composable
private fun NotePropertiesSheetContent(
    note: Note?,
    folders: List<Folder>,
    onTitleChange: (String) -> Unit,
    onFolderSelected: (String?) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.editor_note_properties),
                style = MaterialTheme.typography.titleLarge,
            )
            TextButton(onClick = onDone) {
                Text(stringResource(R.string.editor_done))
            }
        }
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
private fun HandwritingWorkspace(
    note: Note?,
    viewModel: NoteEditorViewModel,
    recognitionState: RecognitionState,
    toolsModifier: Modifier,
    canvasModifier: Modifier,
    paperMaxWidth: Dp?,
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

    val onPrimaryRecognize = {
        if (hasNewStrokes) viewModel.recognizeNewHandwriting()
        else viewModel.recognizeAllHandwriting()
    }

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
            canRecognize = penStrokes.isNotEmpty() && !isBusy,
            onRecognize = onPrimaryRecognize,
            enabled = !isBusy,
            modifier = toolsModifier,
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
        Box(
            modifier = canvasModifier,
            contentAlignment = Alignment.Center,
        ) {
            val surfaceModifier = if (paperMaxWidth != null) {
                Modifier
                    .widthIn(max = paperMaxWidth)
                    .fillMaxWidth()
                    .fillMaxHeight()
            } else {
                Modifier.fillMaxSize()
            }
            Surface(
                modifier = surfaceModifier,
                shape = RoundedCornerShape(20.dp),
                tonalElevation = 2.dp,
                shadowElevation = 6.dp,
                color = MaterialTheme.colorScheme.surface,
            ) {
                HandwritingCanvas(
                    strokes = strokes,
                    selectedTool = selectedTool,
                    penColor = selectedColor,
                    penWidth = selectedWidth,
                    eraserRadius = 40f,
                    onPenStrokeFinished = viewModel::onStrokeAdded,
                    onStrokesReplace = viewModel::onStrokesReplaceAfterErase,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun TextMirrorPanel(
    note: Note?,
    onManualTextChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    showSectionTitle: Boolean = true,
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
        if (showSectionTitle) {
            Text(
                text = stringResource(R.string.editor_text_panel_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Column(Modifier.padding(12.dp)) {
                Text(
                    text = stringResource(R.string.editor_recognized_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = current.recognizedText.ifBlank {
                        stringResource(R.string.editor_recognized_empty_hint)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        OutlinedTextField(
            value = current.manualText,
            onValueChange = onManualTextChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.editor_manual_label)) },
            placeholder = { Text(stringResource(R.string.editor_manual_hint)) },
            minLines = 4,
            shape = RoundedCornerShape(16.dp),
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
    canRecognize: Boolean,
    onRecognize: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier
                .horizontalScroll(scroll)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToolIconToggle(
                selected = selectedTool == ToolType.Pen,
                onClick = { onToolChange(ToolType.Pen) },
                enabled = enabled,
                contentDescription = stringResource(R.string.editor_tool_pen),
                icon = Icons.Filled.BorderColor,
            )
            ToolIconToggle(
                selected = selectedTool == ToolType.Eraser,
                onClick = { onToolChange(ToolType.Eraser) },
                enabled = enabled,
                contentDescription = stringResource(R.string.editor_tool_eraser),
                icon = Icons.Outlined.RemoveCircleOutline,
            )
            VerticalDivider(
                modifier = Modifier
                    .height(28.dp)
                    .padding(horizontal = 2.dp),
            )
            FilledTonalIconButton(
                onClick = onUndo,
                enabled = canUndo && enabled,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = stringResource(R.string.editor_undo))
            }
            FilledTonalIconButton(
                onClick = onRedo,
                enabled = canRedo && enabled,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = stringResource(R.string.editor_redo))
            }
            VerticalDivider(
                modifier = Modifier
                    .height(28.dp)
                    .padding(horizontal = 2.dp),
            )
            StrokeWidthChips(
                selectedWidth = selectedWidth,
                onWidthChange = onWidthChange,
                enabled = enabled,
            )
            VerticalDivider(
                modifier = Modifier
                    .height(28.dp)
                    .padding(horizontal = 2.dp),
            )
            ColorSwatches(
                selectedColor = selectedColor,
                onColorChange = onColorChange,
                enabled = enabled,
            )
            VerticalDivider(
                modifier = Modifier
                    .height(28.dp)
                    .padding(horizontal = 2.dp),
            )
            FilledTonalIconButton(
                onClick = onRecognize,
                enabled = canRecognize && enabled,
                modifier = Modifier.size(44.dp),
            ) {
                Icon(
                    Icons.Filled.AutoAwesome,
                    contentDescription = stringResource(R.string.recognition_action_primary),
                )
            }
        }
    }
}

@Composable
private fun ToolIconToggle(
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean,
    contentDescription: String,
    icon: ImageVector,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    Color.Transparent
                },
            ),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(22.dp),
            tint = if (selected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun StrokeWidthChips(
    selectedWidth: Float,
    onWidthChange: (Float) -> Unit,
    enabled: Boolean,
) {
    val widths = listOf(2f, 4f, 8f, 12f)
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        widths.forEach { w ->
            FilterChip(
                selected = abs(selectedWidth - w) < 0.5f,
                onClick = { onWidthChange(w) },
                label = { Text("${w.toInt()}") },
                enabled = enabled,
                modifier = Modifier.heightIn(min = 32.dp),
            )
        }
    }
}

@Composable
private fun ColorSwatches(
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
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        palette.forEach { c ->
            val selected = selectedColor == c
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color(c))
                    .border(
                        width = if (selected) 2.dp else 1.dp,
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                        },
                        shape = CircleShape,
                    )
                    .clickable(enabled = enabled) { onColorChange(c) },
            )
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
        shape = RoundedCornerShape(16.dp),
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
