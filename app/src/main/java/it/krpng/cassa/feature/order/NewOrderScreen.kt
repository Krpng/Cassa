package it.krpng.cassa.feature.order

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    viewModel: NewOrderViewModel = hiltViewModel(),
) {
    NewOrderScreen(
        state = viewModel.uiState.collectAsStateWithLifecycle().value,
        onBack = onBack,
        onRetry = viewModel::retry,
        onSearchQueryChanged = viewModel::updateSearchQuery,
        onFilterSelected = viewModel::selectFilter,
    )
}

@Composable
fun NewOrderScreen(
    state: NewOrderUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onFilterSelected: (OrderCatalogFilter) -> Unit,
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
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.isDraftEmpty) {
            Text(
                text = "Aggiungi un prodotto per iniziare l'ordine.",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
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
                    OrderCatalogResult(item)
                }
            }
        }
    }
}

@Composable
private fun OrderCatalogResult(item: OrderCatalogItem) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
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
