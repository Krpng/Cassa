package it.krpng.cassa.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun HomeRoute(
    onOpenDraft: (String) -> Unit,
    onTodayOrders: () -> Unit,
    onArchive: () -> Unit,
    onMenu: () -> Unit,
    onSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state = viewModel.uiState.collectAsStateWithLifecycle().value

    LaunchedEffect(viewModel, onOpenDraft) {
        viewModel.navigationEvents.collect { event ->
            when (event) {
                is HomeNavigationEvent.OpenDraft -> onOpenDraft(event.draftId)
            }
        }
    }

    HomeScreen(
        state = state,
        onNewOrder = viewModel::startNewOrder,
        onResumeDraft = onOpenDraft,
        onConflictResume = viewModel::resumeConflictingDraft,
        onConflictReplaceRequest = viewModel::requestReplaceDraft,
        onConflictCancel = viewModel::cancelNewOrderConflict,
        onReplaceConfirm = viewModel::confirmReplaceDraft,
        onReplaceCancel = viewModel::cancelReplaceDraft,
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
    onConflictResume: () -> Unit,
    onConflictReplaceRequest: () -> Unit,
    onConflictCancel: () -> Unit,
    onReplaceConfirm: () -> Unit,
    onReplaceCancel: () -> Unit,
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
            enabled = !state.isNewOrderOperationInProgress,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = if (state.isNewOrderOperationInProgress &&
                    state.newOrderConflict == null
                ) {
                    "CREAZIONE…"
                } else {
                    "NUOVO ORDINE"
                },
                style = MaterialTheme.typography.titleLarge,
            )
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

    if (state.showReplaceConfirmation) {
        AlertDialog(
            onDismissRequest = onReplaceCancel,
            title = { Text("Eliminare l'ordine in corso?") },
            text = { Text("La bozza corrente sarà eliminata e sostituita da un nuovo ordine.") },
            confirmButton = {
                TextButton(
                    onClick = onReplaceConfirm,
                    enabled = !state.isNewOrderOperationInProgress,
                ) {
                    Text(
                        if (state.isNewOrderOperationInProgress) {
                            "SOSTITUZIONE…"
                        } else {
                            "ELIMINA E CREA NUOVO"
                        },
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onReplaceCancel,
                    enabled = !state.isNewOrderOperationInProgress,
                ) {
                    Text("ANNULLA")
                }
            },
        )
    } else if (state.newOrderConflict != null) {
        AlertDialog(
            onDismissRequest = onConflictCancel,
            title = { Text("Esiste già un ordine in corso.") },
            text = state.errorMessage?.let { message ->
                {
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            confirmButton = {
                Column {
                    TextButton(
                        onClick = onConflictResume,
                        enabled = !state.isNewOrderOperationInProgress,
                    ) {
                        Text("RIPRENDI")
                    }
                    TextButton(
                        onClick = onConflictReplaceRequest,
                        enabled = !state.isNewOrderOperationInProgress,
                    ) {
                        Text("ELIMINA E CREA NUOVO")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = onConflictCancel) {
                    Text("ANNULLA")
                }
            },
        )
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
