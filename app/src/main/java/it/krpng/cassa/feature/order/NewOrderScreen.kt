package it.krpng.cassa.feature.order

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.krpng.cassa.domain.pricing.OrderTotalResult
import it.krpng.cassa.ui.theme.customizedPizzaRowBackground

@Composable
fun NewOrderRoute(
    onBack: () -> Unit,
    onProductSelected: (Long) -> Unit = {},
    onOrderItemSelected: (String, String) -> Unit = { _, _ -> },
    viewModel: NewOrderViewModel = hiltViewModel(),
) {
    NewOrderScreen(
        state = viewModel.uiState.collectAsStateWithLifecycle().value,
        onBack = onBack,
        onRetry = viewModel::retry,
        onOpenNote = viewModel::openNoteOverlay,
        onCancelNote = viewModel::cancelNoteOverlay,
        onOpenSearch = viewModel::openSearch,
        onCloseSearch = viewModel::closeSearch,
        onSearchQueryChanged = viewModel::updateSearchQuery,
        onFilterSelected = viewModel::selectFilter,
        onProductSelected = onProductSelected,
        onOrderItemSelected = { orderItemId ->
            val state = viewModel.uiState.value as? NewOrderUiState.Ready
            if (state != null) onOrderItemSelected(state.draftId, orderItemId)
        },
        onIncreaseLineQuantity = viewModel::increaseLineQuantity,
        onDecreaseLineQuantity = viewModel::decreaseLineQuantity,
        onRemoveLine = viewModel::removeLine,
        onGeneralNoteChanged = viewModel::updateGeneralNoteEditor,
        onSaveGeneralNote = viewModel::saveGeneralNote,
        onDismissGeneralNoteError = viewModel::dismissGeneralNoteError,
        onQuickAdd = viewModel::quickAdd,
        onDismissQuickAddError = viewModel::dismissQuickAddError,
        onDismissLineMutationError = viewModel::dismissLineMutationError,
    )
}

@Composable
fun NewOrderScreen(
    state: NewOrderUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onOpenNote: () -> Unit = {},
    onCancelNote: () -> Unit = {},
    onOpenSearch: () -> Unit = {},
    onCloseSearch: () -> Unit = {},
    onSearchQueryChanged: (String) -> Unit,
    onFilterSelected: (OrderCatalogFilter) -> Unit,
    onProductSelected: (Long) -> Unit,
    onOrderItemSelected: (String) -> Unit = {},
    onIncreaseLineQuantity: (String) -> Unit = {},
    onDecreaseLineQuantity: (String) -> Unit = {},
    onRemoveLine: (String) -> Unit = {},
    onGeneralNoteChanged: (String) -> Unit = {},
    onSaveGeneralNote: () -> Unit = {},
    onDismissGeneralNoteError: () -> Unit = {},
    onQuickAdd: (Long) -> Unit,
    onDismissQuickAddError: () -> Unit,
    onDismissLineMutationError: () -> Unit = {},
) {
    val ready = state as? NewOrderUiState.Ready
    val noteOverlayOpen = ready?.noteOverlayOpen == true
    val searchMode = ready?.workspaceMode == NewOrderWorkspaceMode.SEARCH

    BackHandler(enabled = noteOverlayOpen) {
        onCancelNote()
    }
    BackHandler(enabled = searchMode && !noteOverlayOpen) {
        onCloseSearch()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (ready != null && searchMode) {
            SearchWorkspaceHeader(onBack = onCloseSearch)
            Text(
                text = "Cerca prodotto",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            SearchWorkspaceContent(
                state = ready,
                onSearchQueryChanged = onSearchQueryChanged,
                onFilterSelected = onFilterSelected,
                onProductSelected = onProductSelected,
                onQuickAdd = onQuickAdd,
                onDismissQuickAddError = onDismissQuickAddError,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )
        } else {
            if (ready != null) {
                MainOrderHeader(
                    onNote = onOpenNote,
                    onSearch = onOpenSearch,
                    onBack = onBack,
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    HeaderTextAction(
                        label = "← INDIETRO",
                        contentDescription = "Indietro",
                        onClick = onBack,
                    )
                }
            }
            Text(
                text = "Nuovo ordine",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )

            if (ready != null) {
                MainOrderContent(
                    state = ready,
                    onFilterSelected = onFilterSelected,
                    onProductSelected = onProductSelected,
                    onOrderItemSelected = onOrderItemSelected,
                    onIncreaseLineQuantity = onIncreaseLineQuantity,
                    onDecreaseLineQuantity = onDecreaseLineQuantity,
                    onRemoveLine = onRemoveLine,
                    onQuickAdd = onQuickAdd,
                    onDismissQuickAddError = onDismissQuickAddError,
                    onDismissLineMutationError = onDismissLineMutationError,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )
                HorizontalDivider()
                DraftOrderTotalFooter(orderTotal = ready.orderTotal)
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    when (state) {
                        NewOrderUiState.Loading -> CircularProgressIndicator(
                            modifier = Modifier.semantics {
                                contentDescription = "Caricamento ordine"
                            },
                        )

                        NewOrderUiState.NotFound -> ProblemContent(
                            message = "L'ordine non è più disponibile.",
                            onRetry = onRetry,
                        )

                        NewOrderUiState.NotEditable -> ProblemContent(
                            message = "Questo ordine non è una bozza modificabile.",
                            onRetry = onRetry,
                        )

                        is NewOrderUiState.Failure -> ProblemContent(
                            message = state.message,
                            onRetry = onRetry,
                        )

                        is NewOrderUiState.Ready -> Unit
                    }
                }
            }
        }
    }

    if (ready != null && noteOverlayOpen) {
        GeneralNoteOverlay(
            editor = ready.generalNoteEditor,
            canSave = ready.canSaveGeneralNote,
            saveInProgress = ready.generalNoteSaveInProgress,
            error = ready.generalNoteError,
            onEditorChanged = onGeneralNoteChanged,
            onCancel = onCancelNote,
            onSave = onSaveGeneralNote,
            onDismissError = onDismissGeneralNoteError,
        )
    }
}

@Composable
private fun MainOrderHeader(
    onNote: () -> Unit,
    onSearch: () -> Unit,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HeaderTextAction(
            label = "NOTE",
            contentDescription = "Nota ordine",
            onClick = onNote,
        )
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            HeaderTextAction(
                label = "CERCA",
                contentDescription = "Cerca prodotti",
                onClick = onSearch,
            )
        }
        HeaderTextAction(
            label = "← INDIETRO",
            contentDescription = "Indietro",
            onClick = onBack,
        )
    }
}

@Composable
private fun SearchWorkspaceHeader(onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        HeaderTextAction(
            label = "← INDIETRO",
            contentDescription = "Indietro",
            onClick = onBack,
        )
    }
}

@Composable
private fun HeaderTextAction(
    label: String,
    contentDescription: String,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .semantics { this.contentDescription = contentDescription },
    ) {
        Text(label)
    }
}

@Composable
private fun MainOrderContent(
    state: NewOrderUiState.Ready,
    onFilterSelected: (OrderCatalogFilter) -> Unit,
    onProductSelected: (Long) -> Unit,
    onOrderItemSelected: (String) -> Unit,
    onIncreaseLineQuantity: (String) -> Unit,
    onDecreaseLineQuantity: (String) -> Unit,
    onRemoveLine: (String) -> Unit,
    onQuickAdd: (Long) -> Unit,
    onDismissQuickAddError: () -> Unit,
    onDismissLineMutationError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CurrentOrderLines(
            orderLines = state.orderLines,
            lineMutationInProgressItemIds = state.lineMutationInProgressItemIds,
            onOrderItemSelected = onOrderItemSelected,
            onIncreaseLineQuantity = onIncreaseLineQuantity,
            onDecreaseLineQuantity = onDecreaseLineQuantity,
            onRemoveLine = onRemoveLine,
        )
        CategoryFilterChips(
            selected = state.selectedFilter,
            onFilterSelected = onFilterSelected,
        )
        state.quickAddError?.let { message ->
            ErrorBanner(message = message, onDismiss = onDismissQuickAddError)
        }
        state.lineMutationError?.let { message ->
            ErrorBanner(message = message, onDismiss = onDismissLineMutationError)
        }
        CatalogList(
            items = state.catalogItems,
            emptyMessage = "Nessun prodotto attivo.",
            quickAddInProgressProductIds = state.quickAddInProgressProductIds,
            onProductSelected = onProductSelected,
            onQuickAdd = onQuickAdd,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )
    }
}

@Composable
private fun CategoryFilterChips(
    selected: OrderCatalogFilter,
    onFilterSelected: (OrderCatalogFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(
            items = OrderCatalogFilter.entries,
            key = OrderCatalogFilter::name,
        ) { filter ->
            FilterChip(
                selected = filter == selected,
                onClick = { onFilterSelected(filter) },
                label = { Text(filter.label) },
            )
        }
    }
}

@Composable
private fun SearchWorkspaceContent(
    state: NewOrderUiState.Ready,
    onSearchQueryChanged: (String) -> Unit,
    onFilterSelected: (OrderCatalogFilter) -> Unit,
    onProductSelected: (Long) -> Unit,
    onQuickAdd: (Long) -> Unit,
    onDismissQuickAddError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = onSearchQueryChanged,
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = "Ricerca prodotti e ingredienti"
                },
            placeholder = { Text("Cerca prodotto o ingrediente...") },
            label = { Text("CERCA") },
            singleLine = true,
        )
        CategoryFilterChips(
            selected = state.searchSelectedFilter,
            onFilterSelected = onFilterSelected,
        )
        state.quickAddError?.let { message ->
            ErrorBanner(message = message, onDismiss = onDismissQuickAddError)
        }
        CatalogList(
            items = state.catalogItems,
            emptyMessage = if (state.searchQuery.isBlank()) {
                "Nessun prodotto attivo."
            } else {
                "Nessun risultato."
            },
            quickAddInProgressProductIds = state.quickAddInProgressProductIds,
            onProductSelected = onProductSelected,
            onQuickAdd = onQuickAdd,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )
    }
}

@Composable
private fun CatalogList(
    items: List<OrderCatalogItem>,
    emptyMessage: String,
    quickAddInProgressProductIds: Set<Long>,
    onProductSelected: (Long) -> Unit,
    onQuickAdd: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = emptyMessage,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    } else {
        LazyColumn(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(
                items = items,
                key = OrderCatalogItem::productId,
            ) { item ->
                OrderCatalogResult(
                    item = item,
                    onProductSelected = onProductSelected,
                    onQuickAdd = onQuickAdd,
                    isQuickAddInProgress = item.productId in quickAddInProgressProductIds,
                )
            }
        }
    }
}

@Composable
private fun CurrentOrderLines(
    orderLines: List<DraftOrderLine>,
    lineMutationInProgressItemIds: Set<String>,
    onOrderItemSelected: (String) -> Unit,
    onIncreaseLineQuantity: (String) -> Unit,
    onDecreaseLineQuantity: (String) -> Unit,
    onRemoveLine: (String) -> Unit,
) {
    var pendingRemovalItemId by remember { mutableStateOf<String?>(null) }
    val pendingRemovalLine = orderLines.firstOrNull { it.itemId == pendingRemovalItemId }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "ORDINE CORRENTE",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        if (orderLines.isEmpty()) {
            Text(
                text = "Aggiungi un prodotto per iniziare l'ordine.",
                style = MaterialTheme.typography.bodyLarge,
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 280.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(
                    items = orderLines,
                    key = DraftOrderLine::itemId,
                ) { line ->
                    val mutating = line.itemId in lineMutationInProgressItemIds
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (line.isCustomizedPizza) {
                                    MaterialTheme.colorScheme.customizedPizzaRowBackground
                                } else {
                                    Color.Transparent
                                },
                            ),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OutlinedButton(
                                onClick = { onDecreaseLineQuantity(line.itemId) },
                                enabled = !mutating && line.quantity > 1,
                                modifier = Modifier
                                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                    .semantics {
                                        contentDescription =
                                            "Diminuisci quantità ${line.productName}"
                                    },
                            ) {
                                Text("−")
                            }
                            Text(
                                text = line.quantity.toString(),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.semantics {
                                    contentDescription =
                                        "Quantità ${line.productName}: ${line.quantity}"
                                },
                            )
                            OutlinedButton(
                                onClick = { onIncreaseLineQuantity(line.itemId) },
                                enabled = !mutating,
                                modifier = Modifier
                                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                    .semantics {
                                        contentDescription =
                                            "Aumenta quantità ${line.productName}"
                                    },
                            ) {
                                Text("+")
                            }
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable(
                                        role = Role.Button,
                                        onClick = { onOrderItemSelected(line.itemId) },
                                    )
                                    .semantics {
                                        contentDescription = buildString {
                                            append("Apri dettaglio riga: ${line.quantity}x ")
                                            append(line.productName)
                                            append(", ")
                                            append(
                                                line.lineTotal?.formatEur()
                                                    ?: "totale non disponibile",
                                            )
                                            if (line.isCustomizedPizza) {
                                                append(", personalizzata")
                                            }
                                        }
                                    }
                                    .padding(vertical = 4.dp),
                            ) {
                                Text(
                                    text = line.productName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = line.lineTotal?.formatEur() ?: "—",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                            TextButton(
                                onClick = { pendingRemovalItemId = line.itemId },
                                enabled = !mutating,
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .semantics {
                                        contentDescription = "Rimuovi ${line.productName}"
                                    },
                            ) {
                                Text("RIMUOVI")
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    if (pendingRemovalLine != null) {
        AlertDialog(
            onDismissRequest = { pendingRemovalItemId = null },
            title = { Text("Rimuovere questa riga?") },
            text = {
                Text(
                    "${pendingRemovalLine.quantity}x ${pendingRemovalLine.productName}",
                )
            },
            dismissButton = {
                TextButton(
                    onClick = { pendingRemovalItemId = null },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text("ANNULLA")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val itemId = pendingRemovalLine.itemId
                        pendingRemovalItemId = null
                        onRemoveLine(itemId)
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text("RIMUOVI")
                }
            },
        )
    }
}

@Composable
private fun GeneralNoteOverlay(
    editor: String,
    canSave: Boolean,
    saveInProgress: Boolean,
    error: String?,
    onEditorChanged: (String) -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    onDismissError: () -> Unit,
) {
    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .imePadding()
                .navigationBarsPadding(),
            shape = MaterialTheme.shapes.large,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "NOTA ORDINE",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                OutlinedTextField(
                    value = editor,
                    onValueChange = onEditorChanged,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp)
                        .semantics {
                            contentDescription = "Nota generale dell'ordine"
                        },
                    minLines = 4,
                    maxLines = 8,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                    ),
                    enabled = !saveInProgress,
                )
                error?.let { message ->
                    ErrorBanner(message = message, onDismiss = onDismissError)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = onCancel,
                        enabled = !saveInProgress,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .semantics { contentDescription = "Annulla nota ordine" },
                    ) {
                        Text("ANNULLA")
                    }
                    Button(
                        onClick = onSave,
                        enabled = canSave && !saveInProgress,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .semantics { contentDescription = "Salva nota ordine" },
                    ) {
                        Text(if (saveInProgress) "SALVATAGGIO..." else "SALVA")
                    }
                }
            }
        }
    }
}

@Composable
private fun ErrorBanner(
    message: String,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = message,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
        )
        TextButton(onClick = onDismiss) {
            Text("CHIUDI")
        }
    }
}

@Composable
private fun DraftOrderTotalFooter(orderTotal: OrderTotalResult) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = "TOTALE",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        when (orderTotal) {
            is OrderTotalResult.Success -> {
                Text(
                    text = orderTotal.total.formatEur(),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics {
                        contentDescription = "Totale ordine: ${orderTotal.total.formatEur()}"
                    },
                )
            }
            OrderTotalResult.AmountOverflow -> {
                Text(
                    text = "Totale non calcolabile",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics {
                        contentDescription = "Totale ordine non calcolabile per overflow"
                    },
                )
            }
        }
    }
}

@Composable
private fun OrderCatalogResult(
    item: OrderCatalogItem,
    onProductSelected: (Long) -> Unit,
    onQuickAdd: (Long) -> Unit,
    isQuickAddInProgress: Boolean,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    role = Role.Button,
                    onClick = { onProductSelected(item.productId) },
                )
                .semantics {
                    contentDescription = "Apri dettagli ${item.name}"
                }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                item.matchedIngredient?.let { ingredient ->
                    Text(
                        text = "Contiene: $ingredient",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Text(
                    text = item.price.formatEur(),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            IconButton(
                onClick = { onQuickAdd(item.productId) },
                modifier = Modifier
                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .semantics {
                        contentDescription = if (isQuickAddInProgress) {
                            "Aggiunta in corso per ${item.name}"
                        } else {
                            "Aggiungi ${item.name}"
                        }
                    },
            ) {
                if (isQuickAddInProgress) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(
                        text = "+",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        HorizontalDivider()
    }
}

@Composable
private fun ProblemContent(
    message: String,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyLarge,
        )
        Button(onClick = onRetry) {
            Text("RIPROVA")
        }
    }
}
