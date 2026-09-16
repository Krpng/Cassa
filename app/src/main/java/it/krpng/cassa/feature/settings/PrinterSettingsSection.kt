package it.krpng.cassa.feature.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import it.krpng.cassa.domain.model.PricePrintMode

@Composable
fun PrinterSettingsSection(
    state: PrinterSettingsUiState,
    onRetryLoad: () -> Unit,
    onRetryPermission: () -> Unit,
    onPermissionResult: ((String) -> Boolean) -> Unit,
    onSelectPrinter: (String) -> Unit,
    onClearPrinter: () -> Unit,
    onSelectPricePrintMode: (PricePrintMode) -> Unit,
    onRetryPersistence: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    val shouldShowRationale: (String) -> Boolean = { permission ->
        activity?.shouldShowRequestPermissionRationale(permission) == true
    }

    // Gates automatic first request only — never blocks an explicit AUTORIZZA/RIPROVA tap.
    var autoRequestedOnce by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) {
            onPermissionResult(shouldShowRationale)
        }

    LaunchedEffect(state) {
        val required =
            (state as? PrinterSettingsUiState.PermissionRequired)?.takeIf { it.requestable }
                ?: return@LaunchedEffect
        if (autoRequestedOnce) return@LaunchedEffect
        if (required.missingPermissions.isEmpty()) return@LaunchedEffect
        autoRequestedOnce = true
        permissionLauncher.launch(required.missingPermissions.toTypedArray())
    }

    Text(
        text = "Stampante",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
    Spacer(modifier = Modifier.height(8.dp))

    when (state) {
        PrinterSettingsUiState.Loading -> {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.semantics {
                        contentDescription = "Caricamento impostazioni stampante"
                    },
                )
            }
        }
        is PrinterSettingsUiState.PermissionRequired -> {
            Text(
                text = "Serve l'autorizzazione Bluetooth per elencare le stampanti associate.",
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (state.requestable) {
                TextButton(
                    onClick = {
                        // Manual request: not gated by autoRequestedOnce.
                        if (state.missingPermissions.isNotEmpty()) {
                            permissionLauncher.launch(state.missingPermissions.toTypedArray())
                        }
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text("AUTORIZZA")
                }
            } else {
                NonRequestablePermissionCta(onOpenAppSettings = { openApplicationSettings(context) })
            }
        }
        is PrinterSettingsUiState.PermissionDenied -> {
            if (state.requestable) {
                Text(
                    text = "Autorizzazione Bluetooth negata. Non è possibile elencare le stampanti associate.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = {
                        onRetryPermission()
                        if (state.missingPermissions.isNotEmpty()) {
                            permissionLauncher.launch(state.missingPermissions.toTypedArray())
                        }
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text("RIPROVA")
                }
            } else {
                Text(
                    text = "Autorizzazione Bluetooth necessaria.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Consenti i dispositivi nelle vicinanze dalle impostazioni dell'app.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
                NonRequestablePermissionCta(onOpenAppSettings = { openApplicationSettings(context) })
            }
        }
        PrinterSettingsUiState.BluetoothUnavailable -> {
            Text(
                text = "Bluetooth non disponibile su questo dispositivo.",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        PrinterSettingsUiState.BluetoothDisabled -> {
            Text(
                text = "Bluetooth disattivato. Attivalo per selezionare una stampante.",
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(
                onClick = { openBluetoothSettings(context) },
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text("Apri impostazioni Bluetooth")
            }
        }
        is PrinterSettingsUiState.LoadError -> {
            Text(
                text = state.message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyLarge,
            )
            TextButton(
                onClick = onRetryLoad,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text("RIPROVA")
            }
        }
        is PrinterSettingsUiState.Ready -> {
            PrinterReadyContent(
                state = state,
                onSelectPrinter = onSelectPrinter,
                onClearPrinter = onClearPrinter,
                onSelectPricePrintMode = onSelectPricePrintMode,
                onRetryPersistence = onRetryPersistence,
                onOpenBluetoothSettings = { openBluetoothSettings(context) },
            )
        }
    }
}

@Composable
private fun NonRequestablePermissionCta(onOpenAppSettings: () -> Unit) {
    TextButton(
        onClick = onOpenAppSettings,
        modifier = Modifier.heightIn(min = 48.dp),
    ) {
        Text("Apri impostazioni app")
    }
}

@Composable
private fun PrinterReadyContent(
    state: PrinterSettingsUiState.Ready,
    onSelectPrinter: (String) -> Unit,
    onClearPrinter: () -> Unit,
    onSelectPricePrintMode: (PricePrintMode) -> Unit,
    onRetryPersistence: () -> Unit,
    onOpenBluetoothSettings: () -> Unit,
) {
    if (state.selectedIsStale && state.selectedPrinterId != null) {
        Text(
            text = "Stampante selezionata non disponibile",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.error,
        )
        Text(
            text = state.selectedPrinterId,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.semantics {
                contentDescription = "Indirizzo stampante selezionata ${state.selectedPrinterId}"
            },
        )
        Text(
            text = "Non attualmente associata",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(12.dp))
    }

    if (state.devices.isEmpty()) {
        Text(
            text = "Nessun dispositivo Bluetooth associato.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(
            onClick = onOpenBluetoothSettings,
            modifier = Modifier.heightIn(min = 48.dp),
        ) {
            Text("Apri impostazioni Bluetooth")
        }
    } else {
        Text(
            text = "Dispositivi associati",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Column(modifier = Modifier.selectableGroup()) {
            state.devices.forEach { device ->
                val selected = device.id == state.selectedPrinterId && !state.selectedIsStale
                PrinterDeviceRow(
                    device = device,
                    selected = selected,
                    onSelected = { onSelectPrinter(device.id) },
                )
            }
            ClearPrinterOption(
                selected = state.selectedPrinterId == null,
                onSelected = onClearPrinter,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(
            onClick = onOpenBluetoothSettings,
            modifier = Modifier.heightIn(min = 48.dp),
        ) {
            Text("Apri impostazioni Bluetooth")
        }
    }

    if (state.devices.isEmpty() && state.selectedPrinterId != null) {
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(
            onClick = onClearPrinter,
            modifier = Modifier.heightIn(min = 48.dp),
        ) {
            Text("Nessuna stampante")
        }
    }

    Spacer(modifier = Modifier.height(20.dp))
    Text(
        text = "Prezzi in stampa",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
    )
    Spacer(modifier = Modifier.height(8.dp))
    Column(modifier = Modifier.selectableGroup()) {
        PricePrintModeOption(
            label = "Prezzi dettagliati",
            mode = PricePrintMode.DETAILED,
            selected = state.pricePrintMode == PricePrintMode.DETAILED,
            onSelected = onSelectPricePrintMode,
        )
        PricePrintModeOption(
            label = "Solo totale",
            mode = PricePrintMode.TOTAL_ONLY,
            selected = state.pricePrintMode == PricePrintMode.TOTAL_ONLY,
            onSelected = onSelectPricePrintMode,
        )
    }

    state.persistenceMessage?.let { message ->
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = message,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyLarge,
        )
        TextButton(
            onClick = onRetryPersistence,
            modifier = Modifier.heightIn(min = 48.dp),
        ) {
            Text("RIPROVA")
        }
    }
}

@Composable
private fun PrinterDeviceRow(
    device: PrinterDeviceUi,
    selected: Boolean,
    onSelected: () -> Unit,
) {
    val stateDescriptionText = if (selected) "selezionata" else "non selezionata"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onSelected,
            )
            .semantics {
                contentDescription = "${device.displayName}, ${device.id}"
                stateDescription = stateDescriptionText
            }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = device.displayName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            )
            Text(
                text = device.id,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ClearPrinterOption(
    selected: Boolean,
    onSelected: () -> Unit,
) {
    val stateDescriptionText = if (selected) "selezionata" else "non selezionata"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onSelected,
            )
            .semantics {
                contentDescription = "Nessuna stampante"
                stateDescription = stateDescriptionText
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
        )
        Text(
            text = "Nessuna stampante",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
private fun PricePrintModeOption(
    label: String,
    mode: PricePrintMode,
    selected: Boolean,
    onSelected: (PricePrintMode) -> Unit,
) {
    val stateDescriptionText = if (selected) "selezionata" else "non selezionata"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = { onSelected(mode) },
            )
            .semantics {
                contentDescription = label
                stateDescription = stateDescriptionText
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

private fun openBluetoothSettings(context: android.content.Context) {
    val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}

private fun openApplicationSettings(context: android.content.Context) {
    val intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}
