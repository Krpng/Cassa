package it.krpng.cassa.domain.usecase

import it.krpng.cassa.core.datetime.BusinessDateCalculator
import it.krpng.cassa.core.datetime.ClockProvider
import it.krpng.cassa.domain.model.Order
import it.krpng.cassa.domain.model.OrderStatus
import it.krpng.cassa.domain.repository.BusinessDaySettingsLoadResult
import it.krpng.cassa.domain.repository.OrderRepository
import it.krpng.cassa.domain.repository.SettingsRepository
import java.time.DateTimeException
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * ARCH-004: observe an order and expose it only when ACCEPTED for currentBusinessDate.
 */
sealed interface GetCurrentDayAcceptedOrderResult {
    data class Available(
        val order: Order,
        val currentBusinessDate: LocalDate,
        val zoneId: ZoneId,
    ) : GetCurrentDayAcceptedOrderResult

    data object Unavailable : GetCurrentDayAcceptedOrderResult

    data object SettingsFailure : GetCurrentDayAcceptedOrderResult
}

class GetCurrentDayAcceptedOrder @Inject constructor(
    private val orderRepository: OrderRepository,
    private val settingsRepository: SettingsRepository,
    private val clockProvider: ClockProvider,
) {
    fun observe(orderId: String): Flow<GetCurrentDayAcceptedOrderResult> = flow {
        if (orderId.isBlank()) {
            emit(GetCurrentDayAcceptedOrderResult.Unavailable)
            return@flow
        }
        val settings = when (val loaded = settingsRepository.getBusinessDaySettings()) {
            is BusinessDaySettingsLoadResult.Loaded -> loaded.settings
            BusinessDaySettingsLoadResult.PersistenceFailure -> {
                emit(GetCurrentDayAcceptedOrderResult.SettingsFailure)
                return@flow
            }
        }
        val zoneId = try {
            ZoneId.of(settings.timezoneId)
        } catch (_: DateTimeException) {
            emit(GetCurrentDayAcceptedOrderResult.SettingsFailure)
            return@flow
        }
        val currentBusinessDate = BusinessDateCalculator.calculate(
            instant = clockProvider.now(),
            zoneId = zoneId,
            businessDayStartMinutes = settings.businessDayStartMinutes,
        )
        orderRepository.observeById(orderId)
            .map { order -> order.toResult(currentBusinessDate, zoneId) }
            .collect { emit(it) }
    }

    private fun Order?.toResult(
        currentBusinessDate: LocalDate,
        zoneId: ZoneId,
    ): GetCurrentDayAcceptedOrderResult {
        if (this == null) return GetCurrentDayAcceptedOrderResult.Unavailable
        if (status != OrderStatus.ACCEPTED) return GetCurrentDayAcceptedOrderResult.Unavailable
        if (businessDate != currentBusinessDate) return GetCurrentDayAcceptedOrderResult.Unavailable
        return GetCurrentDayAcceptedOrderResult.Available(
            order = this,
            currentBusinessDate = currentBusinessDate,
            zoneId = zoneId,
        )
    }
}
