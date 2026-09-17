package it.krpng.cassa.feature.accepteddetail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import it.krpng.cassa.feature.common.CassaBackButton
import kotlinx.coroutines.delay

@Composable
fun AcceptedOrderDetailRoute(
    onBackToToday: () -> Unit,
    onHome: () -> Unit,
    onOpenNewOrder: (draftId: String) -> Unit,
    viewModel: AcceptedOrderDetailViewModel = hiltViewModel(),
) {
    val state = viewModel.uiState.collectAsStateWithLifecycle().value
    val acceptedPrintState = viewModel.acceptedPrintUiState.collectAsStateWithLifecycle().value
    BackHandler(onBack = onBackToToday)
    LaunchedEffect(viewModel, onOpenNewOrder) {
        viewModel.navigationEvents.collect { event ->
            when (event) {
                is AcceptedOrderDetailNavigationEvent.OpenNewOrder ->
                    onOpenNewOrder(event.draftId)
            }
        }
    }
    LaunchedEffect(acceptedPrintState) {
        when (acceptedPrintState) {
            is AcceptedPrintUiState.Success,
            is AcceptedPrintUiState.Error,
            -> {
                delay(3_000)
                viewModel.consumeAcceptedPrintFeedback()
            }
            AcceptedPrintUiState.Idle,
            AcceptedPrintUiState.Printing,
            -> Unit
        }
    }
    AcceptedOrderDetailScreen(
        state = state,
        acceptedPrintState = acceptedPrintState,
        onBackToToday = onBackToToday,
        onHome = onHome,
        onDuplicateOrder = viewModel::onDuplicateOrder,
        onAcceptedPrint = viewModel::runAcceptedPrint,
        onClearDuplicateError = viewModel::clearDuplicateError,
        onConflictCancel = viewModel::onConflictCancel,
        onConflictResume = viewModel::onConflictResume,
        onConflictReplace = viewModel::onConflictReplace,
        onRetry = viewModel::retry,
    )
}

@Composable
fun AcceptedOrderDetailScreen(
    state: AcceptedOrderDetailUiState,
    acceptedPrintState: AcceptedPrintUiState = AcceptedPrintUiState.Idle,
    onBackToToday: () -> Unit,
    onHome: () -> Unit,
    onDuplicateOrder: () -> Unit = {},
    onAcceptedPrint: () -> Unit = {},
    onClearDuplicateError: () -> Unit = {},
    onConflictCancel: () -> Unit = {},
    onConflictResume: () -> Unit = {},
    onConflictReplace: () -> Unit = {},
    onRetry: () -> Unit = {},
) {
    val isPrinting = acceptedPrintState is AcceptedPrintUiState.Printing
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 24.dp, vertical = 12.dp),
    ) {
        CassaBackButton(onClick = onBackToToday)
        Text(
            text = "Dettaglio ordine",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(12.dp))

        when (state) {
            AcceptedOrderDetailUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.semantics {
                            contentDescription = "Caricamento dettaglio ordine"
                        },
                    )
                }
                DetailActions(
                    onHome = onHome,
                    onDuplicateOrder = null,
                    onAcceptedPrint = onAcceptedPrint,
                    acceptedPrintState = acceptedPrintState,
                    printEnabled = false,
                    duplicateEnabled = false,
                )
            }
            AcceptedOrderDetailUiState.Unavailable -> {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Ordine non disponibile.",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.semantics {
                            contentDescription = "Ordine non disponibile"
                        },
                    )
                }
                DetailActions(
                    onHome = onHome,
                    onDuplicateOrder = null,
                    onAcceptedPrint = onAcceptedPrint,
                    acceptedPrintState = acceptedPrintState,
                    printEnabled = false,
                    duplicateEnabled = false,
                )
            }
            is AcceptedOrderDetailUiState.Error -> {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                    TextButton(onClick = onRetry) {
                        Text("RIPROVA")
                    }
                }
                DetailActions(
                    onHome = onHome,
                    onDuplicateOrder = null,
                    onAcceptedPrint = onAcceptedPrint,
                    acceptedPrintState = acceptedPrintState,
                    printEnabled = false,
                    duplicateEnabled = false,
                )
            }
            is AcceptedOrderDetailUiState.Content -> {
                DetailContent(
                    state = state,
                    modifier = Modifier.weight(1f),
                )
                val duplicateError = state.duplicateError
                if (duplicateError != null && state.activeDraftConflict == null) {
                    Text(
                        text = duplicateError,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp)
                            .semantics {
                                contentDescription = "Errore duplicazione: $duplicateError"
                            },
                    )
                    TextButton(onClick = onClearDuplicateError) {
                        Text("CHIUDI")
                    }
                }
                if (state.isDuplicating || state.isReplacing) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.semantics {
                                contentDescription = if (state.isReplacing) {
                                    "Sostituzione ordine in corso"
                                } else {
                                    "Creazione nuovo ordine"
                                }
                            },
                        )
                    }
                }
                DetailActions(
                    onHome = onHome,
                    onDuplicateOrder = onDuplicateOrder,
                    onAcceptedPrint = onAcceptedPrint,
                    acceptedPrintState = acceptedPrintState,
                    printEnabled = !state.isDuplicating && !state.isReplacing && !isPrinting,
                    duplicateEnabled = !state.isDuplicating && !state.isReplacing && !isPrinting,
                )
                if (state.activeDraftConflict != null) {
                    ActiveDraftConflictDialog(
                        isReplacing = state.isReplacing,
                        replaceError = state.duplicateError,
                        onResume = onConflictResume,
                        onReplace = onConflictReplace,
                        onCancel = onConflictCancel,
                    )
                }
            }
        }
    }
}

@Composable
private fun ActiveDraftConflictDialog(
    isReplacing: Boolean,
    replaceError: String?,
    onResume: () -> Unit,
    onReplace: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = {
            if (!isReplacing) onCancel()
        },
        title = {
            Text(
                text = "C'È GIÀ UN ORDINE IN CORSO",
                modifier = Modifier.semantics {
                    contentDescription = "Dialog conflitto ordine in corso"
                },
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Puoi riprendere l'ordine in corso oppure eliminarlo e creare un nuovo ordine da questo.",
                )
                if (replaceError != null) {
                    Text(
                        text = replaceError,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.semantics {
                            contentDescription = "Errore sostituzione: $replaceError"
                        },
                    )
                }
            }
        },
        confirmButton = {
            Column {
                TextButton(
                    onClick = onResume,
                    enabled = !isReplacing,
                    modifier = Modifier.semantics {
                        contentDescription = "Riprendi ordine in corso"
                    },
                ) {
                    Text("RIPRENDI ORDINE IN CORSO")
                }
                TextButton(
                    onClick = onReplace,
                    enabled = !isReplacing,
                    modifier = Modifier.semantics {
                        contentDescription = "Elimina draft e duplica"
                    },
                ) {
                    Text(
                        if (isReplacing) {
                            "SOSTITUZIONE…"
                        } else {
                            "ELIMINA DRAFT E DUPLICA"
                        },
                    )
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onCancel,
                enabled = !isReplacing,
                modifier = Modifier.semantics {
                    contentDescription = "Annulla conflitto duplicazione"
                },
            ) {
                Text("ANNULLA")
            }
        },
    )
}

@Composable
private fun DetailContent(
    state: AcceptedOrderDetailUiState.Content,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "display-number") {
            Text(
                text = state.displayNumber,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics {
                    contentDescription = "Numero ordine ${state.displayNumber}"
                },
            )
            Text(
                text = state.acceptedAtLabel,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics {
                    contentDescription = "Data e ora ${state.acceptedAtLabel}"
                },
            )
        }
        state.sections.forEach { section ->
            item(key = "header-${section.title}") {
                Text(
                    text = section.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
            items(section.lines, key = { it.itemId }) { line ->
                DetailLine(line = line)
            }
        }
        val note = state.generalNote
        if (!note.isNullOrBlank()) {
            item(key = "general-note") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Nota ordine",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = note,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.semantics {
                            contentDescription = "Nota ordine: $note"
                        },
                    )
                }
            }
        }
        item(key = "total") {
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "TOTALE",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = state.totalLabel,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics {
                    contentDescription = "Totale ordine: ${state.totalLabel}"
                },
            )
        }
    }
}

@Composable
private fun DetailLine(line: AcceptedOrderDetailLineUi) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = buildString {
                    append("${line.quantity}x ${line.productName}")
                    if (line.additionNames.isNotEmpty()) {
                        append(", aggiunte ${line.additionNames.joinToString()}")
                    }
                    if (line.removalNames.isNotEmpty()) {
                        append(", rimossi ${line.removalNames.joinToString()}")
                    }
                    line.note?.takeIf { it.isNotBlank() }?.let { append(", nota $it") }
                }
            },
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = "${line.quantity} × ${line.productName}",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
        )
        if (line.productPrintedName.isNotBlank() &&
            line.productPrintedName != line.productName
        ) {
            Text(
                text = line.productPrintedName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (line.additionNames.isNotEmpty()) {
            Text(
                text = "Aggiunte: ${line.additionNames.joinToString()}",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (line.removalNames.isNotEmpty()) {
            Text(
                text = "Rimossi: ${line.removalNames.joinToString()}",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        line.note?.takeIf { it.isNotBlank() }?.let { note ->
            Text(
                text = "Nota: $note",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        line.manualUnitPrice?.let { manual ->
            Text(
                text = "Prezzo manuale: ${manual.formatEur()}",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Text(
            text = "Prezzo: ${line.finalUnitPrice.formatEur()}",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = "Riga: ${line.lineTotal?.formatEur() ?: "—"}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun DetailActions(
    onHome: () -> Unit,
    onDuplicateOrder: (() -> Unit)?,
    onAcceptedPrint: () -> Unit,
    acceptedPrintState: AcceptedPrintUiState,
    printEnabled: Boolean,
    duplicateEnabled: Boolean,
) {
    val isPrinting = acceptedPrintState is AcceptedPrintUiState.Printing
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (onDuplicateOrder != null) {
            Button(
                onClick = onDuplicateOrder,
                enabled = duplicateEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .semantics { contentDescription = "Nuovo ordine da questo" },
            ) {
                Text("NUOVO ORDINE DA QUESTO")
            }
        }
        Button(
            onClick = onAcceptedPrint,
            enabled = printEnabled,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .semantics {
                    contentDescription =
                        if (isPrinting) {
                            "Stampa ordine in corso"
                        } else if (printEnabled) {
                            "Stampa ordine"
                        } else {
                            "Stampa non disponibile"
                        }
                },
        ) {
            if (isPrinting) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                    Text("STAMPA IN CORSO…")
                }
            } else {
                Text("STAMPA")
            }
        }
        when (acceptedPrintState) {
            is AcceptedPrintUiState.Success -> {
                Text(
                    text = acceptedPrintState.message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.semantics {
                        contentDescription = acceptedPrintState.message
                    },
                )
            }
            is AcceptedPrintUiState.Error -> {
                Text(
                    text = acceptedPrintState.message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics {
                        contentDescription = acceptedPrintState.message
                    },
                )
            }
            AcceptedPrintUiState.Idle,
            AcceptedPrintUiState.Printing,
            -> Unit
        }
        OutlinedButton(
            onClick = onHome,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .semantics { contentDescription = "Torna alla home" },
        ) {
            Text("HOME")
        }
    }
}
