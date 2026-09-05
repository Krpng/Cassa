package it.krpng.cassa.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import it.krpng.cassa.core.datetime.ClockProvider
import it.krpng.cassa.core.datetime.SystemClockProvider

@Module
@InstallIn(SingletonComponent::class)
object ClockModule {
    @Provides
    fun provideClockProvider(): ClockProvider = SystemClockProvider
}
