package it.krpng.cassa.feature.acceptance

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import it.krpng.cassa.domain.pricing.OrderTotalResult
import it.krpng.cassa.feature.common.CassaBackButton

@Composable
fun AcceptancePreviewRoute(
    onBack: () -> Unit,
    viewModel: AcceptancePreviewViewModel = hiltViewModel(),
) {
    AcceptancePreviewScreen(
        state = viewModel.uiState.collectAsStateWithLifecycle().value,
        onBack = onBack,
        onRetry = viewModel::retry,
        onAccept = {},
    )
}

@Composable
fun AcceptancePreviewScreen(
    state: AcceptancePreviewUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onAccept: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 24.dp, vertical = 12.dp),
    ) {
        CassaBackButton(onClick = onBack)
        Text(
            text = "Anteprima accettazione",
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
                }

                PreviewActions(
                    acceptEnabled = state.isAcceptEnabled,
                    hasOverflow = state.hasTotalOverflow,
                    onBack = onBack,
                    onAccept = onAccept,
                )
            }
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
    hasOverflow: Boolean,
    onBack: () -> Unit,
    onAccept: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TextButton(
                onClick = onBack,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .semantics { contentDescription = "Annulla anteprima" },
            ) {
                Text("ANNULLA")
            }
            Button(
                onClick = onAccept,
                enabled = acceptEnabled && !hasOverflow,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .semantics {
                        contentDescription = if (acceptEnabled && !hasOverflow) {
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
