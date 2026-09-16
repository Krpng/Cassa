package it.krpng.cassa.di

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import it.krpng.cassa.platform.bluetooth.AndroidBluetoothAdapterStateProvider
import it.krpng.cassa.platform.bluetooth.AndroidBluetoothPermissionManagerFactory
import it.krpng.cassa.platform.bluetooth.AndroidBondedBluetoothAdapterGateway
import it.krpng.cassa.platform.bluetooth.BluetoothAdapterStateProvider
import it.krpng.cassa.platform.bluetooth.BluetoothPermissionManager
import it.krpng.cassa.platform.bluetooth.BondedBluetoothDevicesProvider
import it.krpng.cassa.platform.bluetooth.DefaultBondedBluetoothDevicesProvider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object BluetoothModule {
    @Provides
    @Singleton
    fun provideBluetoothPermissionManager(
        @ApplicationContext context: Context,
    ): BluetoothPermissionManager = AndroidBluetoothPermissionManagerFactory.create(context)

    @Provides
    @Singleton
    fun provideBondedBluetoothDevicesProvider(
        @ApplicationContext context: Context,
        permissionManager: BluetoothPermissionManager,
    ): BondedBluetoothDevicesProvider =
        DefaultBondedBluetoothDevicesProvider(
            permissionManager = permissionManager,
            adapterGateway = AndroidBondedBluetoothAdapterGateway(
                adapterProvider = { bluetoothAdapterOrNull(context) },
            ),
        )

    @Provides
    @Singleton
    fun provideBluetoothAdapterStateProvider(
        @ApplicationContext context: Context,
    ): BluetoothAdapterStateProvider =
        AndroidBluetoothAdapterStateProvider(
            adapterProvider = { bluetoothAdapterOrNull(context) },
        )

    private fun bluetoothAdapterOrNull(context: Context): BluetoothAdapter? {
        val manager = context.getSystemService(BluetoothManager::class.java)
        return manager?.adapter ?: @Suppress("DEPRECATION") BluetoothAdapter.getDefaultAdapter()
    }
}
