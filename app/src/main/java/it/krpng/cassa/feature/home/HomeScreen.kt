package it.krpng.cassa.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun HomeRoute(
    onNewOrder: () -> Unit,
    onResumeDraft: (String) -> Unit,
    onTodayOrders: () -> Unit,
    onArchive: () -> Unit,
    onMenu: () -> Unit,
    onSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state = viewModel.uiState.collectAsStateWithLifecycle().value

    HomeScreen(
        state = state,
        onNewOrder = onNewOrder,
        onResumeDraft = onResumeDraft,
        onTodayOrders = onTodayOrders,
        onArchive = onArchive,
        onMenu = onMenu,
        onSettings = onSettings,
    )
}

@Composable
fun HomeScreen(
    state: HomeUiState,
    onNewOrder: () -> Unit,
    onResumeDraft: (String) -> Unit,
    onTodayOrders: () -> Unit,
    onArchive: () -> Unit,
    onMenu: () -> Unit,
    onSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text = "Cassa", style = MaterialTheme.typography.headlineLarge)
        Button(
            onClick = onNewOrder,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = "NUOVO ORDINE", style = MaterialTheme.typography.titleLarge)
        }
        when {
            state.isLoading -> CircularProgressIndicator()
            state.errorMessage != null -> Text(
                text = state.errorMessage,
                color = MaterialTheme.colorScheme.error,
            )

            state.activeDraft != null -> ActiveDraftBanner(
                draft = state.activeDraft,
                onResumeDraft = onResumeDraft,
            )
        }
        OutlinedButton(onClick = onTodayOrders, modifier = Modifier.fillMaxWidth()) {
            Text("ORDINI DI OGGI")
        }
        OutlinedButton(onClick = onArchive, modifier = Modifier.fillMaxWidth()) {
            Text("ARCHIVIO")
        }
        OutlinedButton(onClick = onMenu, modifier = Modifier.fillMaxWidth()) {
            Text("MENU")
        }
        OutlinedButton(onClick = onSettings, modifier = Modifier.fillMaxWidth()) {
            Text("IMPOSTAZIONI")
        }
    }
}

@Composable
private fun ActiveDraftBanner(
    draft: HomeDraftSummary,
    onResumeDraft: (String) -> Unit,
) {
    val itemLabel = if (draft.itemCount == 1) {
        "1 articolo"
    } else {
        "${draft.itemCount} articoli"
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "ORDINE IN CORSO",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text("$itemLabel · ${draft.total.formatEur()}")
            Button(
                onClick = { onResumeDraft(draft.draftId) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("RIPRENDI ORDINE")
            }
        }
    }
}
