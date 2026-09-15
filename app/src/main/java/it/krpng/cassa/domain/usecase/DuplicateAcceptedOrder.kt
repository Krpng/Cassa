package it.krpng.cassa.domain.usecase

import it.krpng.cassa.core.datetime.BusinessDateCalculator
import it.krpng.cassa.core.datetime.ClockProvider
import it.krpng.cassa.domain.repository.BusinessDaySettingsLoadResult
import it.krpng.cassa.domain.repository.DuplicateAcceptedOrderResult
import it.krpng.cassa.domain.repository.OrderRepository
import it.krpng.cassa.domain.repository.SettingsRepository
import java.time.DateTimeException
import java.time.ZoneId
import javax.inject.Inject

/**
 * ARCH-006: duplicate a current-day ACCEPTED order into a new independent DRAFT.
 */
sealed interface DuplicateAcceptedOrderOutcome {
    data class Success(
        val draftId: String,
    ) : DuplicateAcceptedOrderOutcome

    data object SourceUnavailable : DuplicateAcceptedOrderOutcome

    data object DraftConflict : DuplicateAcceptedOrderOutcome

    data object SettingsFailure : DuplicateAcceptedOrderOutcome

    data object PersistenceFailure : DuplicateAcceptedOrderOutcome
}

class DuplicateAcceptedOrder @Inject constructor(
    private val orderRepository: OrderRepository,
    private val settingsRepository: SettingsRepository,
    private val clockProvider: ClockProvider,
) {
    suspend operator fun invoke(sourceOrderId: String): DuplicateAcceptedOrderOutcome {
        if (sourceOrderId.isBlank()) {
            return DuplicateAcceptedOrderOutcome.SourceUnavailable
        }
        val settings = when (val loaded = settingsRepository.getBusinessDaySettings()) {
            is BusinessDaySettingsLoadResult.Loaded -> loaded.settings
            BusinessDaySettingsLoadResult.PersistenceFailure ->
                return DuplicateAcceptedOrderOutcome.SettingsFailure
        }
        val zoneId = try {
            ZoneId.of(settings.timezoneId)
        } catch (_: DateTimeException) {
            return DuplicateAcceptedOrderOutcome.SettingsFailure
        }
        val currentBusinessDate = BusinessDateCalculator.calculate(
            instant = clockProvider.now(),
            zoneId = zoneId,
            businessDayStartMinutes = settings.businessDayStartMinutes,
        )
        return when (
            val result = orderRepository.duplicateAcceptedOrder(
                sourceOrderId = sourceOrderId,
                currentBusinessDate = currentBusinessDate,
            )
        ) {
            is DuplicateAcceptedOrderResult.Created ->
                DuplicateAcceptedOrderOutcome.Success(draftId = result.draftId)
            DuplicateAcceptedOrderResult.SourceUnavailable ->
                DuplicateAcceptedOrderOutcome.SourceUnavailable
            DuplicateAcceptedOrderResult.DraftConflict ->
                DuplicateAcceptedOrderOutcome.DraftConflict
            DuplicateAcceptedOrderResult.PersistenceFailure ->
                DuplicateAcceptedOrderOutcome.PersistenceFailure
        }
    }
}
