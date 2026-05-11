package com.example.notesapp.features.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.notesapp.R
import com.example.notesapp.core.vault.VaultManager
import com.example.notesapp.core.vault.VaultState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val vaultManager: VaultManager,
    private val appContext: Context,
) : ViewModel() {

    private val _vaultState = MutableStateFlow(VaultState())
    val vaultState: StateFlow<VaultState> = _vaultState.asStateFlow()

    /** One-shot messages to show as Snackbar. */
    private val _snackbar = MutableSharedFlow<String>()
    val snackbar: SharedFlow<String> = _snackbar.asSharedFlow()

    init {
        loadVaultState()
    }

    private fun loadVaultState() {
        _vaultState.value = vaultManager.getSavedVaultState()
    }

    fun onVaultSelected(uri: Uri) {
        vaultManager.saveSelectedVaultUri(uri)
        val structureCreated = vaultManager.ensureVaultStructure(uri)
        _vaultState.value = vaultManager.getSavedVaultState().let { state ->
            if (!structureCreated) {
                state.copy(errorMessage = appContext.getString(R.string.vault_error_structure))
            } else {
                state
            }
        }
        viewModelScope.launch {
            val msg = if (structureCreated) {
                appContext.getString(R.string.vault_structure_created)
            } else {
                appContext.getString(R.string.vault_error_structure)
            }
            _snackbar.emit(msg)
        }
    }

    fun onVaultSelectionFailed(reason: String) {
        _vaultState.value = _vaultState.value.copy(errorMessage = reason)
        viewModelScope.launch { _snackbar.emit(reason) }
    }

    fun checkAccess() {
        loadVaultState()
        val state = _vaultState.value
        if (!state.selected) return
        viewModelScope.launch {
            val msg = if (state.accessAvailable) {
                appContext.getString(R.string.vault_access_confirmed)
            } else {
                appContext.getString(R.string.vault_error_access)
            }
            _snackbar.emit(msg)
        }
    }

    fun resetVault() {
        vaultManager.clearVault()
        _vaultState.value = VaultState()
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    SettingsViewModel(
                        vaultManager = VaultManager(context.applicationContext),
                        appContext = context.applicationContext,
                    ) as T
            }
    }
}
