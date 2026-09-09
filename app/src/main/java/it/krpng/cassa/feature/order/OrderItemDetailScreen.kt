package it.krpng.cassa.feature.order

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.krpng.cassa.feature.common.CassaBackButton
import it.krpng.cassa.domain.model.ProductCategory

@Composable
fun OrderItemDetailRoute(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: OrderItemDetailViewModel = hiltViewModel(),
) {
    val state = viewModel.uiState.collectAsStateWithLifecycle().value
    LaunchedEffect(state.isSaved) {
        if (state.isSaved) onSaved()
    }
    OrderItemDetailScreen(
        state = state,
        onBack = onBack,
        onRetry = viewModel::retry,
        onQuantityChanged = viewModel::updateQuantity,
        onDecreaseQuantity = viewModel::decreaseQuantity,
        onIncreaseQuantity = viewModel::increaseQuantity,
        onNoteChanged = viewModel::updateNote,
        onStartManualPriceEdit = viewModel::startManualPriceEdit,
        onManualPriceChanged = viewModel::updateManualPrice,
        onSave = viewModel::save,
        onAdditionToggled = viewModel::toggleAddition,
        onCancelQuantityIncrease = viewModel::cancelQuantityIncrease,
        onConfirmQuantityIncrease = viewModel::confirmQuantityIncrease,
    )
}

@Composable
fun OrderItemDetailScreen(
    state: OrderItemDetailUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onQuantityChanged: (String) -> Unit,
    onDecreaseQuantity: () -> Unit,
    onIncreaseQuantity: () -> Unit,
    onNoteChanged: (String) -> Unit,
    onStartManualPriceEdit: () -> Unit,
    onManualPriceChanged: (String) -> Unit,
    onSave: () -> Unit,
    onAdditionToggled: (Long) -> Unit = {},
    onCancelQuantityIncrease: () -> Unit = {},
    onConfirmQuantityIncrease: () -> Unit = {},
) {
    if (state.isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                modifier = Modifier.semantics {
                    contentDescription = "Caricamento dettaglio riga"
                },
            )
        }
        return
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            if (state.canSave) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding(),
                    tonalElevation = 3.dp,
                    shadowElevation = 3.dp,
                ) {
                    Button(
                        onClick = onSave,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .heightIn(min = 48.dp),
                        enabled = !state.isSaving,
                    ) {
                        if (state.isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.semantics {
                                    contentDescription = "Salvataggio riga ordine"
                                },
                            )
                        } else {
                            Text("SALVA")
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
        item {
            CassaBackButton(onClick = onBack, enabled = !state.isSaving)
        }
        item {
            Text(
                text = state.productName.ifEmpty { "Dettaglio riga" },
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
        if (!state.canSave) {
            item {
                OutlinedButton(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
                    Text("RIPROVA")
                }
            }
            return@LazyColumn
        }
        item {
            Text(
                text = "Quantità",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = onDecreaseQuantity,
                    modifier = Modifier
                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        .semantics { contentDescription = "Diminuisci quantità" },
                    enabled = !state.isSaving,
                ) {
                    Text("−")
                }
                OutlinedTextField(
                    value = state.quantityInput,
                    onValueChange = onQuantityChanged,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { contentDescription = "Quantità riga" },
                    label = { Text("QUANTITÀ") },
                    singleLine = true,
                    enabled = !state.isSaving,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = state.validationErrors.quantity != null,
                    supportingText = state.validationErrors.quantity?.let { error ->
                        { Text(error) }
                    },
                )
                OutlinedButton(
                    onClick = onIncreaseQuantity,
                    modifier = Modifier
                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        .semantics { contentDescription = "Aumenta quantità" },
                    enabled = !state.isSaving,
                ) {
                    Text("+")
                }
            }
        }
        item {
            OutlinedTextField(
                value = state.note,
                onValueChange = onNoteChanged,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Nota riga" },
                label = { Text("NOTA") },
                enabled = !state.isSaving,
                minLines = 2,
                maxLines = 4,
            )
        }
        item {
            Text(
                text = "Prezzo automatico: ${state.automaticUnitPrice.formatEur()}",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        if (state.category == ProductCategory.PIZZA) {
            item {
                Text(
                    text = "AGGIUNTE",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            state.additionMessage?.let { message ->
                item {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (state.additionOptions.isEmpty()) {
                item {
                    Text(
                        text = "Nessuna aggiunta disponibile.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                items(
                    count = state.additionOptions.size,
                    key = { index -> state.additionOptions[index].id },
                ) { index ->
                    val option = state.additionOptions[index]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .toggleable(
                                value = option.isSelected,
                                enabled = state.canEditAdditions && !state.isSaving,
                                role = Role.Checkbox,
                                onValueChange = { onAdditionToggled(option.id) },
                            )
                            .semantics {
                                contentDescription = if (option.isSelected) {
                                    "Aggiunta ${option.name}, selezionata"
                                } else {
                                    "Aggiunta ${option.name}, non selezionata"
                                }
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Checkbox(
                            checked = option.isSelected,
                            onCheckedChange = null,
                            enabled = state.canEditAdditions && !state.isSaving,
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(option.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                option.price.formatEur(),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        }
        if (state.manualPriceInput == null) {
            item {
                OutlinedButton(
                    onClick = onStartManualPriceEdit,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isSaving,
                ) {
                    Text("MODIFICA PREZZO")
                }
            }
        } else {
            item {
                OutlinedTextField(
                    value = state.manualPriceInput,
                    onValueChange = onManualPriceChanged,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "Prezzo manuale unitario" },
                    label = { Text("PREZZO MANUALE UNITARIO (€)") },
                    singleLine = true,
                    enabled = !state.isSaving,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = state.validationErrors.manualPrice != null,
                    supportingText = state.validationErrors.manualPrice?.let { error ->
                        { Text(error) }
                    },
                )
            }
        }
        }
    }

    if (state.showQuantityIncreaseConfirmation) {
        AlertDialog(
            onDismissRequest = onCancelQuantityIncrease,
            title = { Text("Applica le aggiunte a tutte?") },
            text = {
                Text(
                    "La riga diventerà ${state.quantityInput}x ${state.productName}. " +
                        "Le aggiunte selezionate saranno applicate a tutte le " +
                        "${state.quantityInput} pizze.",
                )
            },
            dismissButton = {
                TextButton(onClick = onCancelQuantityIncrease) { Text("ANNULLA") }
            },
            confirmButton = {
                TextButton(onClick = onConfirmQuantityIncrease) { Text("CONTINUA") }
            },
        )
    }
}
