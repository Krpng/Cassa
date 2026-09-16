package it.krpng.cassa.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.activity.ComponentActivity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.krpng.cassa.domain.model.NumberingMode
import it.krpng.cassa.domain.model.PricePrintMode
import it.krpng.cassa.feature.common.CassaBackButton
import kotlinx.coroutines.delay

@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
    printerViewModel: PrinterSettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    val shouldShowRationale: (String) -> Boolean = { permission ->
        activity?.shouldShowRequestPermissionRationale(permission) == true
    }

    // First ON_RESUME = enter load; later resumes reload after system Bluetooth / app settings.
    // Does not start test print (D-063).
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        printerViewModel.refresh(shouldShowRationale)
    }

    val state = viewModel.uiState.collectAsStateWithLifecycle().value
    val printerState = printerViewModel.uiState.collectAsStateWithLifecycle().value
    val testPrintState = printerViewModel.testPrintUiState.collectAsStateWithLifecycle().value

    LaunchedEffect(testPrintState) {
        when (testPrintState) {
            is TestPrintUiState.Success,
            is TestPrintUiState.Error,
            -> {
                delay(3_000)
                printerViewModel.consumeTestPrintFeedback()
            }
            TestPrintUiState.Idle,
            TestPrintUiState.Printing,
            -> Unit
        }
    }

    SettingsScreen(
        state = state,
        printerState = printerState,
        testPrintState = testPrintState,
        onBack = onBack,
        onNumberingModeSelected = viewModel::selectNumberingMode,
        onRetrySave = viewModel::retrySave,
        onRetryLoad = viewModel::retryLoad,
        onPrinterRetryLoad = { printerViewModel.refresh(shouldShowRationale) },
        onPrinterRetryPermission = printerViewModel::retryPermission,
        onPrinterPermissionResult = printerViewModel::onPermissionResult,
        onSelectPrinter = printerViewModel::selectPrinter,
        onClearPrinter = printerViewModel::clearPrinter,
        onSelectPricePrintMode = printerViewModel::selectPricePrintMode,
        onPrinterRetryPersistence = printerViewModel::retryPersistence,
        onTestPrint = printerViewModel::runTestPrint,
    )
}

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    printerState: PrinterSettingsUiState,
    testPrintState: TestPrintUiState = TestPrintUiState.Idle,
    onBack: () -> Unit,
    onNumberingModeSelected: (NumberingMode) -> Unit,
    onRetrySave: () -> Unit,
    onRetryLoad: () -> Unit,
    onPrinterRetryLoad: () -> Unit = {},
    onPrinterRetryPermission: () -> Unit = {},
    onPrinterPermissionResult: ((String) -> Boolean) -> Unit = {},
    onSelectPrinter: (String) -> Unit = {},
    onClearPrinter: () -> Unit = {},
    onSelectPricePrintMode: (PricePrintMode) -> Unit = {},
    onPrinterRetryPersistence: () -> Unit = {},
    onTestPrint: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        CassaBackButton(onClick = onBack)
        Text(
            text = "Impostazioni",
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(modifier = Modifier.height(24.dp))

        when (state) {
            SettingsUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.semantics {
                            contentDescription = "Caricamento impostazioni"
                        },
                    )
                }
            }
            is SettingsUiState.LoadError -> {
                Text(
                    text = state.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge,
                )
                TextButton(onClick = onRetryLoad) {
                    Text("RIPROVA")
                }
            }
            is SettingsUiState.Loaded,
            is SettingsUiState.Saving,
            is SettingsUiState.SaveError,
            -> {
                NumberingModeSection(
                    state = state,
                    onNumberingModeSelected = onNumberingModeSelected,
                    onRetrySave = onRetrySave,
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(24.dp))

        PrinterSettingsSection(
            state = printerState,
            testPrintState = testPrintState,
            onRetryLoad = onPrinterRetryLoad,
            onRetryPermission = onPrinterRetryPermission,
            onPermissionResult = onPrinterPermissionResult,
            onSelectPrinter = onSelectPrinter,
            onClearPrinter = onClearPrinter,
            onSelectPricePrintMode = onSelectPricePrintMode,
            onRetryPersistence = onPrinterRetryPersistence,
            onTestPrint = onTestPrint,
        )
    }
}

@Composable
private fun NumberingModeSection(
    state: SettingsUiState,
    onNumberingModeSelected: (NumberingMode) -> Unit,
    onRetrySave: () -> Unit,
) {
    val persistedMode = when (state) {
        is SettingsUiState.Loaded -> state.persistedMode
        is SettingsUiState.Saving -> state.persistedMode
        is SettingsUiState.SaveError -> state.persistedMode
        else -> return
    }
    val interactionEnabled = state !is SettingsUiState.Saving

    Text(
        text = "Modalità numerazione",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = "Applica solo alle accettazioni future.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(12.dp))

    Column(modifier = Modifier.selectableGroup()) {
        NumberingModeOption(
            label = "SEQUENZIALE",
            mode = NumberingMode.SEQUENTIAL,
            selected = persistedMode == NumberingMode.SEQUENTIAL,
            enabled = interactionEnabled,
            onSelected = onNumberingModeSelected,
        )
        NumberingModeOption(
            label = "CASUALE",
            mode = NumberingMode.RANDOM,
            selected = persistedMode == NumberingMode.RANDOM,
            enabled = interactionEnabled,
            onSelected = onNumberingModeSelected,
        )
    }

    when (state) {
        is SettingsUiState.Saving -> {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Salvataggio…",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.semantics {
                    contentDescription = "Salvataggio modalità numerazione"
                },
            )
        }
        is SettingsUiState.SaveError -> {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = state.message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyLarge,
            )
            TextButton(onClick = onRetrySave) {
                Text("RIPROVA")
            }
        }
        else -> Unit
    }
}

@Composable
private fun NumberingModeOption(
    label: String,
    mode: NumberingMode,
    selected: Boolean,
    enabled: Boolean,
    onSelected: (NumberingMode) -> Unit,
) {
    val stateDescriptionText = when {
        selected -> "selezionata"
        else -> "non selezionata"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .selectable(
                selected = selected,
                enabled = enabled,
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
            enabled = enabled,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}
