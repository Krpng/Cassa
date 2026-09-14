package it.krpng.cassa.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.krpng.cassa.domain.model.NumberingMode
import it.krpng.cassa.domain.repository.NumberingModeLoadResult
import it.krpng.cassa.domain.repository.SettingsRepository
import it.krpng.cassa.domain.repository.UpdateNumberingModeResult
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Settings UI state. Selected mode always reflects persisted source of truth,
 * never an optimistic "already saved" selection.
 */
sealed interface SettingsUiState {
    data object Loading : SettingsUiState

    data class Loaded(
        val persistedMode: NumberingMode,
    ) : SettingsUiState

    data class Saving(
        val persistedMode: NumberingMode,
        val requestedMode: NumberingMode,
    ) : SettingsUiState

    data class SaveError(
        val persistedMode: NumberingMode,
        val attemptedMode: NumberingMode,
        val message: String,
    ) : SettingsUiState

    data class LoadError(
        val message: String,
    ) : SettingsUiState
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<SettingsUiState>(SettingsUiState.Loading)
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private var observeJob: Job? = null
    private var saveJob: Job? = null

    init {
        observeSettings()
    }

    fun selectNumberingMode(mode: NumberingMode) {
        val current = _uiState.value
        val persisted = when (current) {
            is SettingsUiState.Loaded -> current.persistedMode
            is SettingsUiState.SaveError -> current.persistedMode
            is SettingsUiState.Saving -> return
            is SettingsUiState.Loading,
            is SettingsUiState.LoadError,
            -> return
        }
        if (mode == persisted) {
            if (current is SettingsUiState.SaveError) {
                _uiState.value = SettingsUiState.Loaded(persisted)
            }
            return
        }
        if (saveJob?.isActive == true) return

        _uiState.value = SettingsUiState.Saving(
            persistedMode = persisted,
            requestedMode = mode,
        )
        saveJob = viewModelScope.launch {
            when (val result = settingsRepository.updateNumberingMode(mode)) {
                UpdateNumberingModeResult.Updated -> {
                    // Observe flow will emit Loaded; keep Saving until then, or set Loaded.
                    _uiState.value = SettingsUiState.Loaded(mode)
                }
                UpdateNumberingModeResult.InvalidStoredMode -> {
                    _uiState.value = SettingsUiState.SaveError(
                        persistedMode = persisted,
                        attemptedMode = mode,
                        message = "Modalità numerazione non valida nello storage.",
                    )
                }
                UpdateNumberingModeResult.PersistenceFailure -> {
                    _uiState.value = SettingsUiState.SaveError(
                        persistedMode = persisted,
                        attemptedMode = mode,
                        message = "Impossibile salvare la modalità numerazione.",
                    )
                }
            }
        }
    }

    fun retrySave() {
        val current = _uiState.value
        if (current !is SettingsUiState.SaveError) return
        selectNumberingMode(current.attemptedMode)
    }

    fun retryLoad() {
        observeSettings()
    }

    private fun observeSettings() {
        observeJob?.cancel()
        _uiState.value = SettingsUiState.Loading
        observeJob = viewModelScope.launch {
            settingsRepository.observeNumberingMode().collect { result ->
                // Do not overwrite an in-flight save or save-error UX with a stale observe tick
                // that still shows the previous mode; after Updated we already set Loaded.
                when (_uiState.value) {
                    is SettingsUiState.Saving,
                    is SettingsUiState.SaveError,
                    -> return@collect
                    else -> Unit
                }
                _uiState.value = when (result) {
                    is NumberingModeLoadResult.Loaded ->
                        SettingsUiState.Loaded(result.mode)
                    NumberingModeLoadResult.InvalidStoredMode ->
                        SettingsUiState.LoadError(
                            "Modalità numerazione non valida nello storage.",
                        )
                    NumberingModeLoadResult.PersistenceFailure ->
                        SettingsUiState.LoadError(
                            "Impossibile caricare le impostazioni.",
                        )
                }
            }
        }
    }
}
