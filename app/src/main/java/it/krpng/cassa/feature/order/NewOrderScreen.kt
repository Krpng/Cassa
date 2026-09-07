package it.krpng.cassa.feature.order

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
    )
}

@Composable
fun NewOrderScreen(
    state: NewOrderUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CassaBackButton(onClick = onBack)
        Text(
            text = "Nuovo ordine",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )

        when (state) {
            NewOrderUiState.Loading -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.semantics {
                        contentDescription = "Caricamento ordine"
                    },
                )
            }

            is NewOrderUiState.Ready -> Text(
                text = if (state.isEmpty) {
                    "Aggiungi un prodotto per iniziare l'ordine."
                } else {
                    "Ordine in corso."
                },
                style = MaterialTheme.typography.bodyLarge,
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
        }
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
