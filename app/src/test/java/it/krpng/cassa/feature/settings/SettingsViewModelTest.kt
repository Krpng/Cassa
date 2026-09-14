package it.krpng.cassa.feature.settings

import it.krpng.cassa.domain.model.NumberingMode
import it.krpng.cassa.domain.repository.BusinessDaySettings
import it.krpng.cassa.domain.repository.BusinessDaySettingsLoadResult
import it.krpng.cassa.domain.repository.NumberingModeLoadResult
import it.krpng.cassa.domain.repository.SettingsRepository
import it.krpng.cassa.domain.repository.UpdateNumberingModeResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val mainDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `NUM-T034 loads persisted mode without inventing selection`() = runTest(mainDispatcher) {
        val repository = FakeSettingsRepository(initial = NumberingMode.RANDOM)
        val viewModel = SettingsViewModel(repository)
        assertEquals(SettingsUiState.Loading, viewModel.uiState.value)

        advanceUntilIdle()

        assertEquals(
            SettingsUiState.Loaded(NumberingMode.RANDOM),
            viewModel.uiState.value,
        )
    }

    @Test
    fun `successful mode change reflects persisted mode after save`() = runTest(mainDispatcher) {
        val repository = FakeSettingsRepository(initial = NumberingMode.SEQUENTIAL)
        val viewModel = SettingsViewModel(repository)
        advanceUntilIdle()

        viewModel.selectNumberingMode(NumberingMode.RANDOM)
        assertEquals(
            SettingsUiState.Saving(
                persistedMode = NumberingMode.SEQUENTIAL,
                requestedMode = NumberingMode.RANDOM,
            ),
            viewModel.uiState.value,
        )
        advanceUntilIdle()

        assertEquals(
            SettingsUiState.Loaded(NumberingMode.RANDOM),
            viewModel.uiState.value,
        )
        assertEquals(NumberingMode.RANDOM, repository.persisted)
    }

    @Test
    fun `NUM-T035 save failure keeps previous persisted selection`() = runTest(mainDispatcher) {
        val repository = FakeSettingsRepository(
            initial = NumberingMode.SEQUENTIAL,
            updateResult = UpdateNumberingModeResult.PersistenceFailure,
        )
        val viewModel = SettingsViewModel(repository)
        advanceUntilIdle()

        viewModel.selectNumberingMode(NumberingMode.RANDOM)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is SettingsUiState.SaveError)
        state as SettingsUiState.SaveError
        assertEquals(NumberingMode.SEQUENTIAL, state.persistedMode)
        assertEquals(NumberingMode.RANDOM, state.attemptedMode)
        assertEquals(NumberingMode.SEQUENTIAL, repository.persisted)
    }

    @Test
    fun `retry repeats attempted mode after save failure`() = runTest(mainDispatcher) {
        val repository = FakeSettingsRepository(
            initial = NumberingMode.SEQUENTIAL,
            updateResult = UpdateNumberingModeResult.PersistenceFailure,
        )
        val viewModel = SettingsViewModel(repository)
        advanceUntilIdle()

        viewModel.selectNumberingMode(NumberingMode.RANDOM)
        advanceUntilIdle()
        repository.updateResult = UpdateNumberingModeResult.Updated

        viewModel.retrySave()
        advanceUntilIdle()

        assertEquals(
            SettingsUiState.Loaded(NumberingMode.RANDOM),
            viewModel.uiState.value,
        )
        assertEquals(NumberingMode.RANDOM, repository.persisted)
        assertEquals(2, repository.updateCalls)
    }

    @Test
    fun `double tap ignored while saving`() = runTest(mainDispatcher) {
        val repository = FakeSettingsRepository(
            initial = NumberingMode.SEQUENTIAL,
            hangUpdates = true,
        )
        val viewModel = SettingsViewModel(repository)
        advanceUntilIdle()

        viewModel.selectNumberingMode(NumberingMode.RANDOM)
        mainDispatcher.scheduler.runCurrent()

        assertTrue(viewModel.uiState.value is SettingsUiState.Saving)
        assertEquals(1, repository.updateCalls)

        viewModel.selectNumberingMode(NumberingMode.RANDOM)
        assertEquals(1, repository.updateCalls)

        repository.releaseHang()
        advanceUntilIdle()
        assertEquals(
            SettingsUiState.Loaded(NumberingMode.RANDOM),
            viewModel.uiState.value,
        )
    }

    private class FakeSettingsRepository(
        initial: NumberingMode? = null,
        var updateResult: UpdateNumberingModeResult = UpdateNumberingModeResult.Updated,
        private val hangUpdates: Boolean = false,
    ) : SettingsRepository {
        private val mode = MutableStateFlow(initial)
        private val hangGate = MutableStateFlow(!hangUpdates)
        var persisted: NumberingMode? = initial
            private set
        var updateCalls: Int = 0
            private set

        override fun observeNumberingMode(): Flow<NumberingModeLoadResult> =
            mode.map { current ->
                if (current == null) {
                    persisted = NumberingMode.SEQUENTIAL
                    mode.value = NumberingMode.SEQUENTIAL
                    NumberingModeLoadResult.Loaded(NumberingMode.SEQUENTIAL)
                } else {
                    NumberingModeLoadResult.Loaded(current)
                }
            }

        override suspend fun getNumberingMode(): NumberingModeLoadResult {
            val current = persisted ?: NumberingMode.SEQUENTIAL.also {
                persisted = it
                mode.value = it
            }
            return NumberingModeLoadResult.Loaded(current)
        }

        override suspend fun updateNumberingMode(mode: NumberingMode): UpdateNumberingModeResult {
            updateCalls += 1
            hangGate.first { it }
            val result = updateResult
            if (result == UpdateNumberingModeResult.Updated) {
                persisted = mode
                this.mode.value = mode
            }
            return result
        }

        override suspend fun getBusinessDaySettings(): BusinessDaySettingsLoadResult =
            BusinessDaySettingsLoadResult.Loaded(
                BusinessDaySettings(
                    timezoneId = "Europe/Rome",
                    businessDayStartMinutes = 300,
                ),
            )

        fun releaseHang() {
            hangGate.value = true
        }
    }
}