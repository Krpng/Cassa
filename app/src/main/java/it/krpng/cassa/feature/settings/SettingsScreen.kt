package it.krpng.cassa.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.krpng.cassa.domain.model.NumberingMode
import it.krpng.cassa.feature.common.CassaBackButton

@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state = viewModel.uiState.collectAsStateWithLifecycle().value
    SettingsScreen(
        state = state,
        onBack = onBack,
        onNumberingModeSelected = viewModel::selectNumberingMode,
        onRetrySave = viewModel::retrySave,
        onRetryLoad = viewModel::retryLoad,
    )
}

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onBack: () -> Unit,
    onNumberingModeSelected: (NumberingMode) -> Unit,
    onRetrySave: () -> Unit,
    onRetryLoad: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(24.dp),
    ) {
        CassaBackButton(onClick = onBack)
        Text(
            text = "Impostazioni",
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(modifier = Modifier.height(24.dp))

        when (state) {
            SettingsUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.semantics {
                            contentDescription = "Caricamento impostazioni"
                        },
                    )
                }
            }
            is SettingsUiState.LoadError -> {
                Text(
                    text = state.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge,
                )
                TextButton(onClick = onRetryLoad) {
                    Text("RIPROVA")
                }
            }
            is SettingsUiState.Loaded,
            is SettingsUiState.Saving,
            is SettingsUiState.SaveError,
            -> {
                NumberingModeSection(
                    state = state,
                    onNumberingModeSelected = onNumberingModeSelected,
                    onRetrySave = onRetrySave,
                )
            }
        }
    }
}

@Composable
private fun NumberingModeSection(
    state: SettingsUiState,
    onNumberingModeSelected: (NumberingMode) -> Unit,
    onRetrySave: () -> Unit,
) {
    val persistedMode = when (state) {
        is SettingsUiState.Loaded -> state.persistedMode
        is SettingsUiState.Saving -> state.persistedMode
        is SettingsUiState.SaveError -> state.persistedMode
        else -> return
    }
    val interactionEnabled = state !is SettingsUiState.Saving

    Text(
        text = "Modalità numerazione",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = "Applica solo alle accettazioni future.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(12.dp))

    Column(modifier = Modifier.selectableGroup()) {
        NumberingModeOption(
            label = "SEQUENZIALE",
            mode = NumberingMode.SEQUENTIAL,
            selected = persistedMode == NumberingMode.SEQUENTIAL,
            enabled = interactionEnabled,
            onSelected = onNumberingModeSelected,
        )
        NumberingModeOption(
            label = "CASUALE",
            mode = NumberingMode.RANDOM,
            selected = persistedMode == NumberingMode.RANDOM,
            enabled = interactionEnabled,
            onSelected = onNumberingModeSelected,
        )
    }

    when (state) {
        is SettingsUiState.Saving -> {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Salvataggio…",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.semantics {
                    contentDescription = "Salvataggio modalità numerazione"
                },
            )
        }
        is SettingsUiState.SaveError -> {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = state.message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyLarge,
            )
            TextButton(onClick = onRetrySave) {
                Text("RIPROVA")
            }
        }
        else -> Unit
    }
}

@Composable
private fun NumberingModeOption(
    label: String,
    mode: NumberingMode,
    selected: Boolean,
    enabled: Boolean,
    onSelected: (NumberingMode) -> Unit,
) {
    val stateDescriptionText = when {
        selected -> "selezionata"
        else -> "non selezionata"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = { onSelected(mode) },
            )
            .semantics {
                contentDescription = label
                stateDescription = stateDescriptionText
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            enabled = enabled,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}
