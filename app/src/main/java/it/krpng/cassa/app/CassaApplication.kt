package it.krpng.cassa.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import it.krpng.cassa.app.retention.AcceptedOrdersRetentionInitializer
import javax.inject.Inject

@HiltAndroidApp
class CassaApplication : Application() {
    @Inject
    lateinit var acceptedOrdersRetentionInitializer: AcceptedOrdersRetentionInitializer

    override fun onCreate() {
        super.onCreate()
        acceptedOrdersRetentionInitializer.register()
    }
}
