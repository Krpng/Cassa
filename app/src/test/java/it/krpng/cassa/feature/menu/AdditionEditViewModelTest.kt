package it.krpng.cassa.feature.menu

import androidx.lifecycle.SavedStateHandle
import it.krpng.cassa.core.datetime.ClockProvider
import it.krpng.cassa.core.money.Money
import it.krpng.cassa.core.normalization.TextNormalizer
import it.krpng.cassa.domain.model.Addition
import it.krpng.cassa.domain.repository.AdditionRepository
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AdditionEditViewModelTest {
    private val mainDispatcher = StandardTestDispatcher()
    private val now = Instant.parse("2026-09-05T12:30:00Z")

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `create persists normalized addition including zero price and inactive state`() =
        runTest(mainDispatcher) {
            val repository = FakeAdditionRepository(createdId = 51)
            val viewModel = viewModel(repository = repository)

            assertFalse(viewModel.uiState.value.isLoading)
            viewModel.updateName("  Olìve   Nere ")
            viewModel.updatePrintedName(" OLIVE ")
            viewModel.updatePrice("0")
            viewModel.updateActive(false)
            viewModel.save()
            advanceUntilIdle()

            val saved = checkNotNull(repository.createdAddition)
            assertEquals(51L, viewModel.uiState.value.savedAdditionId)
            assertEquals("Olìve   Nere", saved.name)
            assertEquals("olive nere", saved.normalizedName)
            assertEquals("OLIVE", saved.printedName)
            assertEquals(Money.ZERO, saved.price)
            assertFalse(saved.active)
            assertEquals(now, saved.createdAt)
            assertEquals(now, saved.updatedAt)
        }

    @Test
    fun `edit loads addition then preserves identity and creation time`() = runTest(mainDispatcher) {
        val createdAt = Instant.parse("2026-01-02T08:00:00Z")
        val existing = addition(id = 7, createdAt = createdAt)
        val repository = FakeAdditionRepository(existing = existing)
        val viewModel = viewModel(
            savedState = mapOf(AdditionEditViewModel.ADDITION_ID_ARGUMENT to 7L),
            repository = repository,
        )
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals("Prosciutto", viewModel.uiState.value.name)
        assertEquals("2,00", viewModel.uiState.value.priceInput)

        viewModel.updateName("Prosciutto cotto")
        viewModel.updatePrice("2.5")
        viewModel.updateActive(false)
        viewModel.save()
        advanceUntilIdle()

        val updated = checkNotNull(repository.updatedAddition)
        assertEquals(7L, updated.id)
        assertEquals(createdAt, updated.createdAt)
        assertEquals(now, updated.updatedAt)
        assertEquals("prosciutto cotto", updated.normalizedName)
        assertEquals(Money.ofCents(250), updated.price)
        assertFalse(updated.active)
        assertEquals(7L, viewModel.uiState.value.savedAdditionId)
    }

    @Test
    fun `invalid form does not write and exposes field errors`() = runTest(mainDispatcher) {
        val repository = FakeAdditionRepository()
        val viewModel = viewModel(repository = repository)

        viewModel.updateName(" ")
        viewModel.updatePrice("7,123")
        viewModel.save()
        advanceUntilIdle()

        assertNull(repository.createdAddition)
        assertTrue(viewModel.uiState.value.validationErrors.hasErrors)
        assertFalse(viewModel.uiState.value.isSaving)
    }

    @Test
    fun `repository failure becomes a safe duplicate name message`() = runTest(mainDispatcher) {
        val repository = FakeAdditionRepository(failOnCreate = true)
        val viewModel = viewModel(repository = repository)

        viewModel.updateName("Olive")
        viewModel.updatePrice("1,00")
        viewModel.save()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.savedAdditionId)
        assertFalse(viewModel.uiState.value.isSaving)
        assertEquals(
            "Impossibile salvare l'aggiunta. Verifica che il nome non sia già usato.",
            viewModel.uiState.value.errorMessage,
        )
    }

    @Test
    fun `missing addition is reported without creating a replacement`() = runTest(mainDispatcher) {
        val repository = FakeAdditionRepository()
        val viewModel = viewModel(
            savedState = mapOf(AdditionEditViewModel.ADDITION_ID_ARGUMENT to 404L),
            repository = repository,
        )
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertFalse(viewModel.uiState.value.canSave)
        assertEquals("Aggiunta non trovata.", viewModel.uiState.value.errorMessage)

        viewModel.updateName("Non creare")
        viewModel.updatePrice("1,00")
        viewModel.save()
        advanceUntilIdle()

        assertNull(repository.createdAddition)
    }

    private fun viewModel(
        savedState: Map<String, Any?> = emptyMap(),
        repository: FakeAdditionRepository = FakeAdditionRepository(),
    ): AdditionEditViewModel = AdditionEditViewModel(
        savedStateHandle = SavedStateHandle(savedState),
        additionRepository = repository,
        clockProvider = object : ClockProvider {
            override fun now(): Instant = now
        },
    )

    private class FakeAdditionRepository(
        private val existing: Addition? = null,
        private val createdId: Long = 1,
        private val failOnCreate: Boolean = false,
    ) : AdditionRepository {
        var createdAddition: Addition? = null
        var updatedAddition: Addition? = null

        override fun observeAll(): Flow<List<Addition>> = MutableStateFlow(emptyList())

        override fun observeActive(): Flow<List<Addition>> = MutableStateFlow(emptyList())

        override suspend fun getById(additionId: Long): Addition? =
            existing?.takeIf { it.id == additionId }

        override suspend fun create(addition: Addition): Long {
            if (failOnCreate) error("duplicate")
            createdAddition = addition
            return createdId
        }

        override suspend fun update(addition: Addition): Boolean {
            updatedAddition = addition
            return existing != null
        }

        override suspend fun activate(additionId: Long, updatedAt: Instant): Boolean =
            error("Not used")

        override suspend fun deactivate(additionId: Long, updatedAt: Instant): Boolean =
            error("Not used")
    }

    private fun addition(id: Long, createdAt: Instant): Addition = Addition(
        id = id,
        name = "Prosciutto",
        normalizedName = TextNormalizer.normalize("Prosciutto"),
        printedName = "PROSCIUTTO",
        price = Money.ofCents(200),
        active = true,
        createdAt = createdAt,
        updatedAt = createdAt,
    )
}
