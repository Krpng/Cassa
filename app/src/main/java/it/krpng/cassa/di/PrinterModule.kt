package it.krpng.cassa.di

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import it.krpng.cassa.data.printer.DataStorePrinterProfileProvider
import it.krpng.cassa.domain.printer.DefaultEscPosEncoder
import it.krpng.cassa.domain.printer.DefaultPrinterService
import it.krpng.cassa.domain.printer.DefaultReceiptComposer
import it.krpng.cassa.domain.printer.EscPosEncoder
import it.krpng.cassa.domain.printer.PrinterDriver
import it.krpng.cassa.domain.printer.PrinterProfileProvider
import it.krpng.cassa.domain.printer.PrinterService
import it.krpng.cassa.domain.printer.ReceiptComposer
import it.krpng.cassa.domain.repository.OrderRepository
import it.krpng.cassa.platform.bluetooth.AndroidBluetoothPrinterDriver
import it.krpng.cassa.platform.bluetooth.AndroidBluetoothRfcommGateway
import it.krpng.cassa.platform.bluetooth.BluetoothPermissionManager
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PrinterModule {
    @Binds
    @Singleton
    abstract fun bindPrinterProfileProvider(
        provider: DataStorePrinterProfileProvider,
    ): PrinterProfileProvider

    companion object {
        @Provides
        @Singleton
        fun provideEscPosEncoder(): EscPosEncoder = DefaultEscPosEncoder()

        @Provides
        @Singleton
        fun provideReceiptComposer(): ReceiptComposer = DefaultReceiptComposer()

        @Provides
        @Singleton
        fun providePrinterDriver(
            @ApplicationContext context: Context,
            permissionManager: BluetoothPermissionManager,
        ): PrinterDriver {
            val appContext = context.applicationContext
            return AndroidBluetoothPrinterDriver(
                permissionManager = permissionManager,
                gateway =
                    AndroidBluetoothRfcommGateway(
                        adapterProvider = {
                            val manager = appContext.getSystemService(BluetoothManager::class.java)
                            manager?.adapter
                                ?: @Suppress("DEPRECATION") BluetoothAdapter.getDefaultAdapter()
                        },
                    ),
            )
        }

        @Provides
        @Singleton
        fun providePrinterService(
            orderRepository: OrderRepository,
            profileProvider: PrinterProfileProvider,
            receiptComposer: ReceiptComposer,
            escPosEncoder: EscPosEncoder,
            printerDriver: PrinterDriver,
        ): PrinterService =
            DefaultPrinterService(
                orderRepository = orderRepository,
                profileProvider = profileProvider,
                receiptComposer = receiptComposer,
                escPosEncoder = escPosEncoder,
                printerDriver = printerDriver,
            )
    }
}
