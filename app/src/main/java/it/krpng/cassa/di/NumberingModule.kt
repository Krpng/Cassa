package it.krpng.cassa.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import it.krpng.cassa.data.numbering.SecureRandomNumberingSeedProvider
import it.krpng.cassa.domain.numbering.NumberingSeedProvider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class NumberingModule {
    @Binds
    @Singleton
    abstract fun bindNumberingSeedProvider(
        provider: SecureRandomNumberingSeedProvider,
    ): NumberingSeedProvider
}
