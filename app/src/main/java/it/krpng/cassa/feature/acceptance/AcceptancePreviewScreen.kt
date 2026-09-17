package it.krpng.cassa.feature.acceptance

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
import it.krpng.cassa.domain.pricing.OrderTotalResult
import it.krpng.cassa.feature.common.CassaBackButton
import kotlinx.coroutines.delay

@Composable
fun AcceptancePreviewRoute(
    onBack: () -> Unit,
    onHome: () -> Unit,
    onOpenNewOrder: (draftId: String) -> Unit,
    viewModel: AcceptancePreviewViewModel = hiltViewModel(),
) {
    val state = viewModel.uiState.collectAsStateWithLifecycle().value
    val draftPrintState = viewModel.draftPrintUiState.collectAsStateWithLifecycle().value

    LaunchedEffect(viewModel, onHome, onOpenNewOrder) {
        viewModel.navigationEvents.collect { event ->
            when (event) {
                AcceptanceNavigationEvent.GoHome -> onHome()
                is AcceptanceNavigationEvent.OpenNewOrder -> onOpenNewOrder(event.draftId)
            }
        }
    }

    // Keyed by draftPrintState: prior delay is cancelled when state changes (D-064).
    LaunchedEffect(draftPrintState) {
        when (draftPrintState) {
            is DraftPrintUiState.Success,
            is DraftPrintUiState.Error,
            -> {
                delay(3_000)
                viewModel.consumeDraftPrintFeedback()
            }
            DraftPrintUiState.Idle,
            DraftPrintUiState.Printing,
            -> Unit
        }
    }

    BackHandler(enabled = state is AcceptancePreviewUiState.Accepted) {
        viewModel.goHome()
    }

    AcceptancePreviewScreen(
        state = state,
        draftPrintState = draftPrintState,
        onBack = onBack,
        onRetry = viewModel::retry,
        onAccept = viewModel::accept,
        onDraftPrint = viewModel::runDraftPrint,
        onHome = viewModel::goHome,
        onNewOrder = viewModel::startNewOrder,
    )
}

@Composable
fun AcceptancePreviewScreen(
    state: AcceptancePreviewUiState,
    draftPrintState: DraftPrintUiState = DraftPrintUiState.Idle,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onAccept: () -> Unit,
    onDraftPrint: () -> Unit = {},
    onHome: () -> Unit = {},
    onNewOrder: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 24.dp, vertical = 12.dp),
    ) {
        when (state) {
            is AcceptancePreviewUiState.Accepted -> {
                // No preview back affordance: HOME / system Back leave Accepted safely.
            }
            else -> CassaBackButton(onClick = onBack)
        }
        Text(
            text = when (state) {
                is AcceptancePreviewUiState.Accepted -> "Ordine accettato"
                else -> "Anteprima accettazione"
            },
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(12.dp))

        when (state) {
            AcceptancePreviewUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.semantics {
                            contentDescription = "Caricamento anteprima"
                        },
                    )
                }
            }
            AcceptancePreviewUiState.NotFound -> {
                PreviewMessage(
                    message = "Ordine non trovato.",
                    onRetry = onRetry,
                    modifier = Modifier.weight(1f),
                )
            }
            AcceptancePreviewUiState.NotDraft -> {
                PreviewMessage(
                    message = "L'ordine non è più una bozza.",
                    onRetry = null,
                    modifier = Modifier.weight(1f),
                )
            }
            AcceptancePreviewUiState.EmptyDraft -> {
                PreviewMessage(
                    message = "Aggiungi almeno un prodotto per accettare l'ordine.",
                    onRetry = null,
                    modifier = Modifier.weight(1f),
                )
            }
            is AcceptancePreviewUiState.Failure -> {
                PreviewMessage(
                    message = state.message,
                    onRetry = onRetry,
                    modifier = Modifier.weight(1f),
                )
            }
            is AcceptancePreviewUiState.Accepted -> {
                AcceptedBody(
                    state = state,
                    modifier = Modifier.weight(1f),
                )
                AcceptedActions(
                    isCreatingNewOrder = state.isCreatingNewOrder,
                    onPrint = {},
                    onHome = onHome,
                    onNewOrder = onNewOrder,
                )
            }
            is AcceptancePreviewUiState.Ready -> {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    state.sections.forEach { section ->
                        item(key = "header-${section.title}") {
                            Text(
                                text = section.title,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        items(section.lines, key = { it.itemId }) { line ->
                            PreviewLine(line = line)
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
                        PreviewTotal(orderTotal = state.orderTotal)
                    }
                    if (state.isAccepting) {
                        item(key = "accepting") {
                            Text(
                                text = "Accettazione in corso…",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.semantics {
                                    contentDescription = "Accettazione in corso"
                                },
                            )
                        }
                    }
                    state.acceptError?.let { error ->
                        item(key = "accept-error") {
                            Text(
                                text = error,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }

                PreviewActions(
                    acceptEnabled = state.isAcceptEnabled,
                    isAccepting = state.isAccepting,
                    hasOverflow = state.hasTotalOverflow,
                    draftPrintState = draftPrintState,
                    onBack = onBack,
                    onAccept = onAccept,
                    onDraftPrint = onDraftPrint,
                )
            }
        }
    }
}

@Composable
private fun AcceptedBody(
    state: AcceptancePreviewUiState.Accepted,
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
        }
        state.sections.forEach { section ->
            item(key = "accepted-header-${section.title}") {
                Text(
                    text = section.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
            items(section.lines, key = { "accepted-${it.itemId}" }) { line ->
                PreviewLine(line = line)
            }
        }
        val note = state.generalNote
        if (!note.isNullOrBlank()) {
            item(key = "accepted-general-note") {
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
        item(key = "accepted-total") {
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "TOTALE",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = state.total.formatEur(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics {
                    contentDescription = "Totale ordine: ${state.total.formatEur()}"
                },
            )
        }
        state.actionError?.let { error ->
            item(key = "accepted-action-error") {
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

@Composable
private fun AcceptedActions(
    isCreatingNewOrder: Boolean,
    onPrint: () -> Unit,
    onHome: () -> Unit,
    onNewOrder: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(
            onClick = onPrint,
            enabled = false,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .semantics { contentDescription = "Stampa non disponibile" },
        ) {
            Text("STAMPA")
        }
        OutlinedButton(
            onClick = onHome,
            enabled = !isCreatingNewOrder,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .semantics { contentDescription = "Torna alla home" },
        ) {
            Text("HOME")
        }
        Button(
            onClick = onNewOrder,
            enabled = !isCreatingNewOrder,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .semantics {
                    contentDescription = if (isCreatingNewOrder) {
                        "Creazione nuovo ordine"
                    } else {
                        "Nuovo ordine"
                    }
                },
        ) {
            Text(if (isCreatingNewOrder) "CREAZIONE…" else "NUOVO ORDINE")
        }
    }
}

@Composable
private fun PreviewLine(line: AcceptancePreviewLineUi) {
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
private fun PreviewTotal(orderTotal: OrderTotalResult) {
    HorizontalDivider()
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = "TOTALE",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
    )
    when (orderTotal) {
        is OrderTotalResult.Success -> {
            Text(
                text = orderTotal.total.formatEur(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics {
                    contentDescription = "Totale anteprima: ${orderTotal.total.formatEur()}"
                },
            )
        }
        OrderTotalResult.AmountOverflow -> {
            Text(
                text = "Totale non calcolabile",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics {
                    contentDescription = "Totale anteprima non calcolabile per overflow"
                },
            )
            Text(
                text = "L'accettazione non è disponibile.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun PreviewActions(
    acceptEnabled: Boolean,
    isAccepting: Boolean,
    hasOverflow: Boolean,
    draftPrintState: DraftPrintUiState,
    onBack: () -> Unit,
    onAccept: () -> Unit,
    onDraftPrint: () -> Unit,
) {
    val isPrinting = draftPrintState is DraftPrintUiState.Printing
    val printEnabled = !isAccepting && !isPrinting
    val acceptButtonEnabled = acceptEnabled && !hasOverflow && !isPrinting

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (isAccepting) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.semantics {
                        contentDescription = "Accettazione in corso"
                    },
                )
            }
        }
        OutlinedButton(
            onClick = onDraftPrint,
            enabled = printEnabled,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .semantics {
                    contentDescription =
                        if (isPrinting) {
                            "Stampa bozza in corso"
                        } else {
                            "Stampa bozza"
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
                Text("STAMPA BOZZA")
            }
        }
        when (draftPrintState) {
            is DraftPrintUiState.Success -> {
                Text(
                    text = draftPrintState.message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.semantics {
                        contentDescription = draftPrintState.message
                    },
                )
            }
            is DraftPrintUiState.Error -> {
                Text(
                    text = draftPrintState.message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics {
                        contentDescription = draftPrintState.message
                    },
                )
            }
            DraftPrintUiState.Idle,
            DraftPrintUiState.Printing,
            -> Unit
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TextButton(
                onClick = onBack,
                enabled = !isAccepting,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .semantics { contentDescription = "Annulla anteprima" },
            ) {
                Text("ANNULLA")
            }
            Button(
                onClick = onAccept,
                enabled = acceptButtonEnabled,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .semantics {
                        contentDescription = if (acceptButtonEnabled) {
                            "Accetta ordine"
                        } else {
                            "Accetta ordine non disponibile"
                        }
                    },
            ) {
                Text("ACCETTA")
            }
        }
    }
}

@Composable
private fun PreviewMessage(
    message: String,
    onRetry: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = message, style = MaterialTheme.typography.bodyLarge)
        if (onRetry != null) {
            TextButton(onClick = onRetry) {
                Text("RIPROVA")
            }
        }
    }
}
