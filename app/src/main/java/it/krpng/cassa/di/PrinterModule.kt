package it.krpng.cassa.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import it.krpng.cassa.data.printer.DataStorePrinterProfileProvider
import it.krpng.cassa.domain.printer.PrinterProfileProvider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PrinterModule {
    @Binds
    @Singleton
    abstract fun bindPrinterProfileProvider(
        provider: DataStorePrinterProfileProvider,
    ): PrinterProfileProvider
}
