package it.krpng.cassa.feature.menu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.krpng.cassa.feature.common.CassaBackButton

@Composable
fun AdditionEditRoute(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: AdditionEditViewModel = hiltViewModel(),
) {
    val state = viewModel.uiState.collectAsStateWithLifecycle().value

    LaunchedEffect(state.savedAdditionId) {
        if (state.savedAdditionId != null) {
            onSaved()
        }
    }

    AdditionEditScreen(
        state = state,
        onBack = onBack,
        onNameChanged = viewModel::updateName,
        onPrintedNameChanged = viewModel::updatePrintedName,
        onPriceChanged = viewModel::updatePrice,
        onActiveChanged = viewModel::updateActive,
        onSave = viewModel::save,
    )
}

@Composable
fun AdditionEditScreen(
    state: AdditionEditUiState,
    onBack: () -> Unit,
    onNameChanged: (String) -> Unit,
    onPrintedNameChanged: (String) -> Unit,
    onPriceChanged: (String) -> Unit,
    onActiveChanged: (Boolean) -> Unit,
    onSave: () -> Unit,
) {
    if (state.isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.semantics {
                    contentDescription = "Caricamento aggiunta"
                },
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            CassaBackButton(onClick = onBack, enabled = !state.isSaving)
        }
        item {
            Text(
                text = if (state.additionId == null) "Nuova aggiunta" else "Modifica aggiunta",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        state.errorMessage?.let { message ->
            item {
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
        item {
            OutlinedTextField(
                value = state.name,
                onValueChange = onNameChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("NOME") },
                singleLine = true,
                enabled = !state.isSaving,
                isError = state.validationErrors.name != null,
                supportingText = state.validationErrors.name?.let { error ->
                    { Text(error) }
                },
            )
        }
        item {
            OutlinedTextField(
                value = state.printedName,
                onValueChange = onPrintedNameChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("NOME STAMPATO (OPZIONALE)") },
                singleLine = true,
                enabled = !state.isSaving,
            )
        }
        item {
            OutlinedTextField(
                value = state.priceInput,
                onValueChange = onPriceChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("PREZZO (€)") },
                singleLine = true,
                enabled = !state.isSaving,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = state.validationErrors.price != null,
                supportingText = state.validationErrors.price?.let { error ->
                    { Text(error) }
                },
            )
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Aggiunta attiva",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Switch(
                    checked = state.active,
                    onCheckedChange = onActiveChanged,
                    enabled = !state.isSaving,
                )
            }
        }
        item {
            Button(
                onClick = onSave,
                modifier = Modifier.fillMaxWidth(),
                enabled = state.canSave && !state.isSaving,
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.semantics {
                            contentDescription = "Salvataggio aggiunta"
                        },
                    )
                } else {
                    Text("SALVA")
                }
            }
        }
    }
}
