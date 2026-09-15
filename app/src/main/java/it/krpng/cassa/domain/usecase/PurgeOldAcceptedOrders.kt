package it.krpng.cassa.domain.usecase

import it.krpng.cassa.core.datetime.BusinessDateCalculator
import it.krpng.cassa.core.datetime.ClockProvider
import it.krpng.cassa.domain.repository.BusinessDaySettingsLoadResult
import it.krpng.cassa.domain.repository.OrderRepository
import it.krpng.cassa.domain.repository.PurgeAcceptedBeforeResult
import it.krpng.cassa.domain.repository.SettingsRepository
import java.time.DateTimeException
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

sealed interface PurgeOldAcceptedOrdersResult {
    data class Completed(
        val currentBusinessDate: LocalDate,
        val deletedOrderCount: Int,
    ) : PurgeOldAcceptedOrdersResult

    data object SettingsFailure : PurgeOldAcceptedOrdersResult

    data object PersistenceFailure : PurgeOldAcceptedOrdersResult
}

open class PurgeOldAcceptedOrders @Inject constructor(
    private val orderRepository: OrderRepository,
    private val settingsRepository: SettingsRepository,
    private val clockProvider: ClockProvider,
) {
    open suspend operator fun invoke(): PurgeOldAcceptedOrdersResult {
        val settings = when (val loaded = settingsRepository.getBusinessDaySettings()) {
            is BusinessDaySettingsLoadResult.Loaded -> loaded.settings
            BusinessDaySettingsLoadResult.PersistenceFailure ->
                return PurgeOldAcceptedOrdersResult.SettingsFailure
        }
        val zoneId = try {
            ZoneId.of(settings.timezoneId)
        } catch (_: DateTimeException) {
            return PurgeOldAcceptedOrdersResult.SettingsFailure
        }
        val currentBusinessDate = BusinessDateCalculator.calculate(
            instant = clockProvider.now(),
            zoneId = zoneId,
            businessDayStartMinutes = settings.businessDayStartMinutes,
        )
        return when (val purged = orderRepository.purgeAcceptedBefore(currentBusinessDate)) {
            is PurgeAcceptedBeforeResult.Purged -> PurgeOldAcceptedOrdersResult.Completed(
                currentBusinessDate = currentBusinessDate,
                deletedOrderCount = purged.deletedOrderCount,
            )
            PurgeAcceptedBeforeResult.PersistenceFailure ->
                PurgeOldAcceptedOrdersResult.PersistenceFailure
        }
    }
}
