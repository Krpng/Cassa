package it.krpng.cassa.feature.menu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun MenuRoute(
    onBack: () -> Unit,
    viewModel: MenuViewModel = hiltViewModel(),
) {
    val state = viewModel.uiState.collectAsStateWithLifecycle().value

    MenuScreen(
        state = state,
        onBack = onBack,
        onSectionSelected = viewModel::selectSection,
        onSearchQueryChanged = viewModel::updateSearchQuery,
    )
}

@Composable
fun MenuScreen(
    state: MenuUiState,
    onBack: () -> Unit,
    onSectionSelected: (MenuSection) -> Unit,
    onSearchQueryChanged: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        TextButton(onClick = onBack) {
            Text("INDIETRO")
        }
        Text(
            text = "Menu",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(16.dp))
        MenuSectionSelector(
            selectedSection = state.selectedSection,
            onSectionSelected = onSectionSelected,
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = onSearchQueryChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("CERCA") },
            singleLine = true,
        )
        Spacer(modifier = Modifier.height(12.dp))
        MenuContent(
            state = state,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun MenuSectionSelector(
    selectedSection: MenuSection,
    onSectionSelected: (MenuSection) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        items(
            items = MenuSection.entries,
            key = MenuSection::name,
        ) { section ->
            FilterChip(
                selected = section == selectedSection,
                onClick = { onSectionSelected(section) },
                label = { Text(section.label) },
            )
        }
    }
}

@Composable
private fun MenuContent(
    state: MenuUiState,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        when {
            state.isLoading -> CircularProgressIndicator(
                modifier = Modifier.semantics {
                    contentDescription = "Caricamento menu"
                },
            )

            state.errorMessage != null -> Text(
                text = state.errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyLarge,
            )

            state.items.isEmpty() -> Text(
                text = emptyMessage(state.selectedSection, state.searchQuery),
                style = MaterialTheme.typography.bodyLarge,
            )

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(
                    items = state.items,
                    key = MenuListItem::key,
                ) { item ->
                    MenuItemCard(item)
                }
            }
        }
    }
}

@Composable
private fun MenuItemCard(item: MenuListItem) {
    val status = if (item.active) "ATTIVO" else "INATTIVO"
    val cardDescription = buildString {
        append(item.name)
        append(", ")
        append(item.price.formatEur())
        append(", ")
        append(status.lowercase())
        item.matchedIngredient?.let { ingredient ->
            append(", contiene ")
            append(ingredient)
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = cardDescription },
        colors = CardDefaults.cardColors(
            containerColor = if (item.active) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = item.name,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = item.price.formatEur(),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            item.matchedIngredient?.let { ingredient ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Contiene: $ingredient",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = status,
                color = if (item.active) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

private fun emptyMessage(
    section: MenuSection,
    searchQuery: String,
): String = if (searchQuery.isBlank()) {
    "Nessun elemento in ${section.label.lowercase()}."
} else {
    "Nessun risultato in ${section.label.lowercase()}."
}
