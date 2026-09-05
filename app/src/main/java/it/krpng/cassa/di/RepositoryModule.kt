package it.krpng.cassa.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import it.krpng.cassa.data.repository.RoomAdditionRepository
import it.krpng.cassa.data.repository.RoomIngredientRepository
import it.krpng.cassa.data.repository.RoomProductRepository
import it.krpng.cassa.domain.repository.AdditionRepository
import it.krpng.cassa.domain.repository.IngredientRepository
import it.krpng.cassa.domain.repository.ProductRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindProductRepository(
        repository: RoomProductRepository,
    ): ProductRepository

    @Binds
    @Singleton
    abstract fun bindAdditionRepository(
        repository: RoomAdditionRepository,
    ): AdditionRepository

    @Binds
    @Singleton
    abstract fun bindIngredientRepository(
        repository: RoomIngredientRepository,
    ): IngredientRepository
}
