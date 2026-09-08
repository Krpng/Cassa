package it.krpng.cassa.feature.order

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.krpng.cassa.feature.common.CassaBackButton

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
        onSearchQueryChanged = viewModel::updateSearchQuery,
        onFilterSelected = viewModel::selectFilter,
        onProductSelected = onProductSelected,
        onOrderItemSelected = { orderItemId ->
            val state = viewModel.uiState.value as? NewOrderUiState.Ready
            if (state != null) onOrderItemSelected(state.draftId, orderItemId)
        },
        onQuickAdd = viewModel::quickAdd,
        onDismissQuickAddError = viewModel::dismissQuickAddError,
    )
}

@Composable
fun NewOrderScreen(
    state: NewOrderUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onFilterSelected: (OrderCatalogFilter) -> Unit,
    onProductSelected: (Long) -> Unit,
    onOrderItemSelected: (String) -> Unit = {},
    onQuickAdd: (Long) -> Unit,
    onDismissQuickAddError: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CassaBackButton(onClick = onBack)
        Text(
            text = "Nuovo ordine",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )

        if (state is NewOrderUiState.Ready) {
            OrderCatalogContent(
                state = state,
                onSearchQueryChanged = onSearchQueryChanged,
                onFilterSelected = onFilterSelected,
                onProductSelected = onProductSelected,
                onOrderItemSelected = onOrderItemSelected,
                onQuickAdd = onQuickAdd,
                onDismissQuickAddError = onDismissQuickAddError,
                modifier = Modifier.weight(1f),
            )
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

@Composable
private fun OrderCatalogContent(
    state: NewOrderUiState.Ready,
    onSearchQueryChanged: (String) -> Unit,
    onFilterSelected: (OrderCatalogFilter) -> Unit,
    onProductSelected: (Long) -> Unit,
    onOrderItemSelected: (String) -> Unit,
    onQuickAdd: (Long) -> Unit,
    onDismissQuickAddError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CurrentOrderContent(
            orderLines = state.orderLines,
            onOrderItemSelected = onOrderItemSelected,
        )
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
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(
                items = OrderCatalogFilter.entries,
                key = OrderCatalogFilter::name,
            ) { filter ->
                FilterChip(
                    selected = filter == state.selectedFilter,
                    onClick = { onFilterSelected(filter) },
                    label = { Text(filter.label) },
                )
            }
        }
        state.quickAddError?.let { message ->
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
                TextButton(onClick = onDismissQuickAddError) {
                    Text("CHIUDI")
                }
            }
        }
        if (state.catalogItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (state.searchQuery.isBlank()) {
                        "Nessun prodotto attivo."
                    } else {
                        "Nessun risultato."
                    },
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(
                    items = state.catalogItems,
                    key = OrderCatalogItem::productId,
                ) { item ->
                    OrderCatalogResult(
                        item = item,
                        onProductSelected = onProductSelected,
                        onQuickAdd = onQuickAdd,
                        isQuickAddInProgress = item.productId in
                            state.quickAddInProgressProductIds,
                    )
                }
            }
        }
    }
}

@Composable
private fun CurrentOrderContent(
    orderLines: List<DraftOrderLine>,
    onOrderItemSelected: (String) -> Unit,
) {
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
                    .heightIn(max = 168.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(
                    items = orderLines,
                    key = DraftOrderLine::itemId,
                ) { line ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                role = Role.Button,
                                onClick = { onOrderItemSelected(line.itemId) },
                            )
                            .semantics {
                                contentDescription =
                                    "Modifica riga: ${line.quantity}x ${line.productName}, " +
                                        line.lineTotal.formatEur()
                            }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "${line.quantity}x ${line.productName}",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = line.lineTotal.formatEur(),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                    HorizontalDivider()
                }
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
                        contentDescription = "Aggiungi ${item.name}"
                    },
            ) {
                if (isQuickAddInProgress) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(24.dp)
                            .semantics {
                                contentDescription = "Aggiunta in corso per ${item.name}"
                            },
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
