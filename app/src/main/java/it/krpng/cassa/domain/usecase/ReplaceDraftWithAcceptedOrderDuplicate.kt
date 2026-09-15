package it.krpng.cassa.domain.usecase

import it.krpng.cassa.core.datetime.BusinessDateCalculator
import it.krpng.cassa.core.datetime.ClockProvider
import it.krpng.cassa.domain.repository.BusinessDaySettingsLoadResult
import it.krpng.cassa.domain.repository.OrderRepository
import it.krpng.cassa.domain.repository.ReplaceDraftWithAcceptedOrderDuplicateResult
import it.krpng.cassa.domain.repository.SettingsRepository
import java.time.DateTimeException
import java.time.ZoneId
import javax.inject.Inject

/**
 * ARCH-007: atomically replace the expected active DRAFT with a faithful duplicate of a
 * current-day ACCEPTED order (ONE Room transaction).
 */
sealed interface ReplaceDraftWithAcceptedOrderDuplicateOutcome {
    data class Success(
        val draftId: String,
    ) : ReplaceDraftWithAcceptedOrderDuplicateOutcome

    data object SourceUnavailable : ReplaceDraftWithAcceptedOrderDuplicateOutcome

    data object DraftMissing : ReplaceDraftWithAcceptedOrderDuplicateOutcome

    data object DraftChanged : ReplaceDraftWithAcceptedOrderDuplicateOutcome

    data object SettingsFailure : ReplaceDraftWithAcceptedOrderDuplicateOutcome

    data object PersistenceFailure : ReplaceDraftWithAcceptedOrderDuplicateOutcome
}

class ReplaceDraftWithAcceptedOrderDuplicate @Inject constructor(
    private val orderRepository: OrderRepository,
    private val settingsRepository: SettingsRepository,
    private val clockProvider: ClockProvider,
) {
    suspend operator fun invoke(
        sourceOrderId: String,
        expectedDraftId: String,
    ): ReplaceDraftWithAcceptedOrderDuplicateOutcome {
        if (sourceOrderId.isBlank()) {
            return ReplaceDraftWithAcceptedOrderDuplicateOutcome.SourceUnavailable
        }
        if (expectedDraftId.isBlank()) {
            return ReplaceDraftWithAcceptedOrderDuplicateOutcome.DraftMissing
        }
        val settings = when (val loaded = settingsRepository.getBusinessDaySettings()) {
            is BusinessDaySettingsLoadResult.Loaded -> loaded.settings
            BusinessDaySettingsLoadResult.PersistenceFailure ->
                return ReplaceDraftWithAcceptedOrderDuplicateOutcome.SettingsFailure
        }
        val zoneId = try {
            ZoneId.of(settings.timezoneId)
        } catch (_: DateTimeException) {
            return ReplaceDraftWithAcceptedOrderDuplicateOutcome.SettingsFailure
        }
        val currentBusinessDate = BusinessDateCalculator.calculate(
            instant = clockProvider.now(),
            zoneId = zoneId,
            businessDayStartMinutes = settings.businessDayStartMinutes,
        )
        return when (
            val result = orderRepository.replaceDraftWithAcceptedOrderDuplicate(
                sourceOrderId = sourceOrderId,
                currentBusinessDate = currentBusinessDate,
                expectedDraftId = expectedDraftId,
            )
        ) {
            is ReplaceDraftWithAcceptedOrderDuplicateResult.Created ->
                ReplaceDraftWithAcceptedOrderDuplicateOutcome.Success(draftId = result.draftId)
            ReplaceDraftWithAcceptedOrderDuplicateResult.SourceUnavailable ->
                ReplaceDraftWithAcceptedOrderDuplicateOutcome.SourceUnavailable
            ReplaceDraftWithAcceptedOrderDuplicateResult.DraftMissing ->
                ReplaceDraftWithAcceptedOrderDuplicateOutcome.DraftMissing
            ReplaceDraftWithAcceptedOrderDuplicateResult.DraftChanged ->
                ReplaceDraftWithAcceptedOrderDuplicateOutcome.DraftChanged
            ReplaceDraftWithAcceptedOrderDuplicateResult.PersistenceFailure ->
                ReplaceDraftWithAcceptedOrderDuplicateOutcome.PersistenceFailure
        }
    }
}
