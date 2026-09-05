package it.krpng.cassa.feature.menu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import it.krpng.cassa.domain.model.Ingredient
import it.krpng.cassa.domain.model.ProductCategory

@Composable
fun ProductEditRoute(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: ProductEditViewModel = hiltViewModel(),
) {
    val state = viewModel.uiState.collectAsStateWithLifecycle().value

    LaunchedEffect(state.savedProductId) {
        if (state.savedProductId != null) {
            onSaved()
        }
    }

    ProductEditScreen(
        state = state,
        onBack = onBack,
        onNameChanged = viewModel::updateName,
        onPrintedNameChanged = viewModel::updatePrintedName,
        onPriceChanged = viewModel::updatePrice,
        onCategoryChanged = viewModel::updateCategory,
        onAutomaticExtrasPricingChanged = viewModel::updateAutomaticExtrasPricing,
        onActiveChanged = viewModel::updateActive,
        onIngredientAdded = viewModel::addIngredient,
        onIngredientRemoved = viewModel::removeIngredient,
        onIngredientMoved = viewModel::moveIngredient,
        onSave = viewModel::save,
    )
}

@Composable
fun ProductEditScreen(
    state: ProductEditUiState,
    onBack: () -> Unit,
    onNameChanged: (String) -> Unit,
    onPrintedNameChanged: (String) -> Unit,
    onPriceChanged: (String) -> Unit,
    onCategoryChanged: (ProductCategory) -> Unit,
    onAutomaticExtrasPricingChanged: (Boolean) -> Unit,
    onActiveChanged: (Boolean) -> Unit,
    onIngredientAdded: (Long) -> Unit,
    onIngredientRemoved: (Long) -> Unit,
    onIngredientMoved: (Long, Int) -> Unit,
    onSave: () -> Unit,
) {
    if (state.isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.semantics {
                    contentDescription = "Caricamento prodotto"
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
            TextButton(onClick = onBack, enabled = !state.isSaving) {
                Text("ANNULLA")
            }
        }
        item {
            Text(
                text = if (state.productId == null) "Nuovo prodotto" else "Modifica prodotto",
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
            Text(
                text = "CATEGORIA",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ProductCategory.entries, key = ProductCategory::name) { category ->
                    FilterChip(
                        selected = category == state.category,
                        onClick = { onCategoryChanged(category) },
                        enabled = !state.isSaving,
                        label = { Text(category.displayName()) },
                    )
                }
            }
        }
        item {
            SettingSwitch(
                label = "Prezzo automatico aggiunte",
                checked = state.automaticExtrasPricing,
                enabled = !state.isSaving,
                onCheckedChange = onAutomaticExtrasPricingChanged,
            )
        }
        item {
            SettingSwitch(
                label = "Prodotto attivo",
                checked = state.active,
                enabled = !state.isSaving,
                onCheckedChange = onActiveChanged,
            )
        }
        item {
            HorizontalDivider()
        }
        item {
            Text(
                text = "INGREDIENTI",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        if (state.selectedIngredients.isEmpty()) {
            item {
                Text("Nessun ingrediente associato.")
            }
        } else {
            itemsIndexed(
                items = state.selectedIngredients,
                key = { _, ingredient -> ingredient.id },
            ) { index, ingredient ->
                SelectedIngredientRow(
                    ingredient = ingredient,
                    canMoveUp = index > 0,
                    canMoveDown = index < state.selectedIngredients.lastIndex,
                    enabled = !state.isSaving,
                    onMoveUp = { onIngredientMoved(ingredient.id, -1) },
                    onMoveDown = { onIngredientMoved(ingredient.id, 1) },
                    onRemove = { onIngredientRemoved(ingredient.id) },
                )
            }
        }
        item {
            Text(
                text = "INGREDIENTI DISPONIBILI",
                style = MaterialTheme.typography.labelLarge,
            )
        }
        val selectedIds = state.selectedIngredients.mapTo(mutableSetOf(), Ingredient::id)
        val selectableIngredients = state.availableIngredients.filterNot { it.id in selectedIds }
        if (selectableIngredients.isEmpty()) {
            item {
                Text("Nessun altro ingrediente disponibile.")
            }
        } else {
            items(
                items = selectableIngredients,
                key = Ingredient::id,
            ) { ingredient ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = ingredient.name,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedButton(
                        onClick = { onIngredientAdded(ingredient.id) },
                        enabled = !state.isSaving,
                    ) {
                        Text("AGGIUNGI")
                    }
                }
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
                            contentDescription = "Salvataggio prodotto"
                        },
                    )
                } else {
                    Text("SALVA")
                }
            }
        }
    }
}

@Composable
private fun SettingSwitch(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
    }
}

@Composable
private fun SelectedIngredientRow(
    ingredient: Ingredient,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    enabled: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = ingredient.name,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = onMoveUp, enabled = enabled && canMoveUp) {
                Text("SU")
            }
            TextButton(onClick = onMoveDown, enabled = enabled && canMoveDown) {
                Text("GIÙ")
            }
            TextButton(onClick = onRemove, enabled = enabled) {
                Text("RIMUOVI")
            }
        }
    }
}

private fun ProductCategory.displayName(): String = when (this) {
    ProductCategory.PIZZA -> "PIZZA"
    ProductCategory.FRITTURA -> "FRITTURA"
    ProductCategory.BIBITA -> "BIBITA"
}
