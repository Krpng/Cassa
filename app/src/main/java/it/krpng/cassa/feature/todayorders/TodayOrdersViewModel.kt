package it.krpng.cassa.feature.todayorders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.krpng.cassa.core.datetime.BusinessDateCalculator
import it.krpng.cassa.core.datetime.ClockProvider
import it.krpng.cassa.domain.model.AcceptedOrderSummary
import it.krpng.cassa.domain.repository.BusinessDaySettingsLoadResult
import it.krpng.cassa.domain.repository.OrderRepository
import it.krpng.cassa.domain.repository.SettingsRepository
import java.time.DateTimeException
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

data class TodayOrderRowUi(
    val orderId: String,
    val displayNumber: String,
    val acceptedAtLabel: String,
    val totalLabel: String,
)

sealed interface TodayOrdersUiState {
    data object Loading : TodayOrdersUiState

    data class Empty(
        val businessDate: LocalDate,
    ) : TodayOrdersUiState

    data class Content(
        val businessDate: LocalDate,
        val rows: List<TodayOrderRowUi>,
    ) : TodayOrdersUiState

    data class Error(
        val message: String,
    ) : TodayOrdersUiState
}

@HiltViewModel
class TodayOrdersViewModel @Inject constructor(
    private val orderRepository: OrderRepository,
    private val settingsRepository: SettingsRepository,
    private val clockProvider: ClockProvider,
) : ViewModel() {
    private val _uiState = MutableStateFlow<TodayOrdersUiState>(TodayOrdersUiState.Loading)
    val uiState: StateFlow<TodayOrdersUiState> = _uiState.asStateFlow()

    private var observeJob: Job? = null

    init {
        loadToday()
    }

    fun retry() {
        loadToday()
    }

    private fun loadToday() {
        observeJob?.cancel()
        _uiState.value = TodayOrdersUiState.Loading
        observeJob = viewModelScope.launch {
            val settings = when (val loaded = settingsRepository.getBusinessDaySettings()) {
                is BusinessDaySettingsLoadResult.Loaded -> loaded.settings
                BusinessDaySettingsLoadResult.PersistenceFailure -> {
                    _uiState.value = TodayOrdersUiState.Error(
                        message = "Impossibile caricare le impostazioni della giornata operativa.",
                    )
                    return@launch
                }
            }
            val zoneId = try {
                ZoneId.of(settings.timezoneId)
            } catch (_: DateTimeException) {
                _uiState.value = TodayOrdersUiState.Error(
                    message = "Fuso orario non valido.",
                )
                return@launch
            }
            val businessDate = BusinessDateCalculator.calculate(
                instant = clockProvider.now(),
                zoneId = zoneId,
                businessDayStartMinutes = settings.businessDayStartMinutes,
            )
            val timeFormatter = DateTimeFormatter.ofPattern("HH:mm").withZone(zoneId)

            orderRepository.observeAcceptedByBusinessDate(businessDate)
                .catch {
                    _uiState.value = TodayOrdersUiState.Error(
                        message = "Impossibile caricare gli ordini di oggi.",
                    )
                }
                .collect { summaries ->
                    _uiState.value = if (summaries.isEmpty()) {
                        TodayOrdersUiState.Empty(businessDate = businessDate)
                    } else {
                        TodayOrdersUiState.Content(
                            businessDate = businessDate,
                            rows = summaries.map { summary ->
                                summary.toRowUi(timeFormatter)
                            },
                        )
                    }
                }
        }
    }

    private fun AcceptedOrderSummary.toRowUi(
        timeFormatter: DateTimeFormatter,
    ): TodayOrderRowUi = TodayOrderRowUi(
        orderId = id,
        displayNumber = displayNumber,
        acceptedAtLabel = timeFormatter.format(acceptedAt),
        totalLabel = total.formatEur(),
    )
}
