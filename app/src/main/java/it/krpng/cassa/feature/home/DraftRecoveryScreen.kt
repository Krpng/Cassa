package it.krpng.cassa.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun DraftRecoveryRoute(
    onContinueToHome: () -> Unit,
    onResumeDraft: (String) -> Unit,
    viewModel: DraftRecoveryViewModel = hiltViewModel(),
) {
    val state = viewModel.uiState.collectAsStateWithLifecycle().value

    LaunchedEffect(state) {
        if (state is DraftRecoveryUiState.NoRecoveryNeeded) {
            onContinueToHome()
        }
    }

    DraftRecoveryScreen(
        state = state,
        onResumeDraft = onResumeDraft,
        onDeleteRequest = viewModel::requestDelete,
        onDeleteConfirm = viewModel::confirmDelete,
        onDeleteCancel = viewModel::cancelDelete,
        onRetry = viewModel::retry,
    )
}

@Composable
fun DraftRecoveryScreen(
    state: DraftRecoveryUiState,
    onResumeDraft: (String) -> Unit,
    onDeleteRequest: () -> Unit,
    onDeleteConfirm: () -> Unit,
    onDeleteCancel: () -> Unit,
    onRetry: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        when (state) {
            DraftRecoveryUiState.Loading -> CircularProgressIndicator(
                modifier = Modifier.semantics {
                    contentDescription = "Controllo ordine in corso"
                },
            )

            DraftRecoveryUiState.NoRecoveryNeeded -> Unit

            is DraftRecoveryUiState.DraftAvailable -> DraftAvailableContent(
                state = state,
                onResumeDraft = onResumeDraft,
                onDeleteRequest = onDeleteRequest,
            )

            is DraftRecoveryUiState.Failure -> Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = state.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Button(onClick = onRetry) {
                    Text("RIPROVA")
                }
            }
        }
    }

    val available = state as? DraftRecoveryUiState.DraftAvailable
    if (available?.showDeleteConfirmation == true) {
        AlertDialog(
            onDismissRequest = onDeleteCancel,
            title = { Text("Eliminare l'ordine in corso?") },
            text = { Text("Questa azione elimina definitivamente la bozza non completata.") },
            confirmButton = {
                TextButton(onClick = onDeleteConfirm) {
                    Text("ELIMINA")
                }
            },
            dismissButton = {
                TextButton(onClick = onDeleteCancel) {
                    Text("ANNULLA")
                }
            },
        )
    }
}

@Composable
private fun DraftAvailableContent(
    state: DraftRecoveryUiState.DraftAvailable,
    onResumeDraft: (String) -> Unit,
    onDeleteRequest: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Hai un ordine non completato.",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = { onResumeDraft(state.draft.id) },
            enabled = !state.isDeleting,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("RIPRENDI")
        }
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(
            onClick = onDeleteRequest,
            enabled = !state.isDeleting,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (state.isDeleting) "ELIMINAZIONE…" else "ELIMINA")
        }
        state.errorMessage?.let { message ->
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}
