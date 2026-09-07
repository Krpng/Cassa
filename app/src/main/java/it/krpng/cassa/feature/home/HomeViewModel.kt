package it.krpng.cassa.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.krpng.cassa.core.money.Money
import it.krpng.cassa.domain.model.Order
import it.krpng.cassa.domain.repository.OrderRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class HomeDraftSummary(
    val draftId: String,
    val itemCount: Int,
    val total: Money,
)

data class HomeUiState(
    val isLoading: Boolean = true,
    val activeDraft: HomeDraftSummary? = null,
    val errorMessage: String? = null,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    orderRepository: OrderRepository,
) : ViewModel() {
    val uiState = orderRepository.observeActiveDraft()
        .map { draft ->
            HomeUiState(
                isLoading = false,
                activeDraft = draft?.takeIf { it.items.isNotEmpty() }?.toSummary(),
            )
        }
        .catch {
            emit(
                HomeUiState(
                    isLoading = false,
                    errorMessage = "Impossibile controllare l'ordine in corso.",
                ),
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HomeUiState(),
        )

    private fun Order.toSummary(): HomeDraftSummary = HomeDraftSummary(
        draftId = id,
        itemCount = items.sumOf { it.quantity },
        total = total,
    )
}
