package com.example.notesapp.features.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.notesapp.R
import com.example.notesapp.core.vault.VaultState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.factory(LocalContext.current)
    ),
) {
    val context = LocalContext.current
    val vaultState by viewModel.vaultState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.snackbar.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            viewModel.onVaultSelected(uri)
        } catch (e: Exception) {
            viewModel.onVaultSelectionFailed(
                context.getString(R.string.vault_error_selecting) + ": ${e.message}"
            )
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
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
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            VaultSection(
                vaultState = vaultState,
                onSelectFolder = { folderPicker.launch(null) },
                onCheckAccess = viewModel::checkAccess,
                onReset = viewModel::resetVault,
            )
        }
    }
}

@Composable
private fun VaultSection(
    vaultState: VaultState,
    onSelectFolder: () -> Unit,
    onCheckAccess: () -> Unit,
    onReset: () -> Unit,
) {
    Surface(
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.storage_title),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = stringResource(R.string.storage_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            VaultStatusRow(vaultState = vaultState)

            Spacer(modifier = Modifier.height(4.dp))

            Button(
                onClick = onSelectFolder,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(18.dp),
                )
                Text(stringResource(R.string.vault_select_folder))
            }

            if (vaultState.selected) {
                FilledTonalButton(
                    onClick = onCheckAccess,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.vault_check_access))
                }

                OutlinedButton(
                    onClick = onReset,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.vault_reset))
                }
            }
        }
    }
}

@Composable
private fun VaultStatusRow(vaultState: VaultState) {
    val (icon, label, color) = when {
        !vaultState.selected -> Triple(
            Icons.Default.FolderOff,
            stringResource(R.string.vault_not_selected),
            MaterialTheme.colorScheme.onSurfaceVariant,
        )
        vaultState.accessAvailable -> Triple(
            Icons.Default.CheckCircle,
            stringResource(R.string.vault_selected, vaultState.displayName ?: vaultState.uri.orEmpty()),
            MaterialTheme.colorScheme.primary,
        )
        else -> Triple(
            Icons.Default.Error,
            stringResource(R.string.vault_access_lost),
            MaterialTheme.colorScheme.error,
        )
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = color,
        )
    }

    if (vaultState.errorMessage != null) {
        Text(
            text = vaultState.errorMessage,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}
