package it.krpng.cassa.feature.importmenu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
fun ImportPreviewRoute(
    onBack: () -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    viewModel: ImportPreviewViewModel = hiltViewModel(),
) {
    val state = viewModel.uiState.collectAsStateWithLifecycle().value
    ImportPreviewScreen(
        state = state,
        onBack = onBack,
        onCancel = onCancel,
        onConfirm = onConfirm,
    )
}

@Composable
fun ImportPreviewScreen(
    state: ImportPreviewUiState,
    onBack: () -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        CassaBackButton(onClick = onBack)
        Text(
            text = "Anteprima importazione",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            when (state) {
                ImportPreviewUiState.Loading -> CircularProgressIndicator(
                    modifier = Modifier.semantics {
                        contentDescription = "Preparazione anteprima importazione"
                    },
                )

                is ImportPreviewUiState.Ready -> ReadyPreview(state.summary)
                is ImportPreviewUiState.Invalid -> InvalidPreview(state.errors)
                is ImportPreviewUiState.Failure -> Text(
                    text = state.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = onConfirm,
            enabled = state.canConfirm,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("CONFERMA IMPORTAZIONE")
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("ANNULLA")
        }
    }
}

@Composable
private fun ReadyPreview(summary: ImportPreviewSummary) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                text = "Il file è valido. Controlla il riepilogo prima di confermare.",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        item { PreviewCount("Prodotti da creare", summary.productsToCreate) }
        item { PreviewCount("Prodotti da aggiornare", summary.productsToUpdate) }
        item { PreviewCount("Prodotti invariati", summary.unchangedProducts) }
        item { PreviewCount("Aggiunte da creare", summary.additionsToCreate) }
        item { PreviewCount("Aggiunte da aggiornare", summary.additionsToUpdate) }
        item { PreviewCount("Aggiunte invariate", summary.unchangedAdditions) }
        item {
            Text(
                text = "Gli elementi assenti dal file resteranno invariati.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun PreviewCount(label: String, count: Int) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "$label: $count",
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun InvalidPreview(errors: List<ImportPreviewError>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                text = "Importazione bloccata: ${errors.size} errori.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        items(errors) { error ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Foglio: ${error.sheet} · Riga: ${error.row}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Campo: ${error.field}")
                    Text(
                        text = "Problema: ${error.problem}",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}
