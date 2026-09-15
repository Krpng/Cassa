package it.krpng.cassa.app.retention

import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import it.krpng.cassa.di.ApplicationScope
import it.krpng.cassa.domain.usecase.PurgeOldAcceptedOrders
import it.krpng.cassa.domain.usecase.PurgeOldAcceptedOrdersResult
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * RET-001: runs idempotent accepted-order purge when the app enters foreground.
 * Correctness does not depend on exact 05:00 scheduling.
 */
@Singleton
class AcceptedOrdersRetentionInitializer @Inject constructor(
    private val purgeOldAcceptedOrders: PurgeOldAcceptedOrders,
    @param:ApplicationScope private val applicationScope: CoroutineScope,
) : DefaultLifecycleObserver {
    private var inFlight: Job? = null
    private var registered = false

    fun register() {
        if (registered) return
        registered = true
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        onAppStart()
    }

    /** Testable entry point for ON_START without relying on process lifecycle. */
    fun onAppStart() {
        if (inFlight?.isActive == true) return
        inFlight = applicationScope.launch {
            try {
                when (val result = purgeOldAcceptedOrders()) {
                    is PurgeOldAcceptedOrdersResult.Completed -> Unit
                    PurgeOldAcceptedOrdersResult.SettingsFailure,
                    PurgeOldAcceptedOrdersResult.PersistenceFailure,
                    -> warn("RET-001 purge deferred: $result")
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                warn("RET-001 purge failed without crashing app", error)
            }
        }
    }

    /** Visible for tests. */
    internal fun isPurgeInFlight(): Boolean = inFlight?.isActive == true

    private fun warn(message: String, error: Throwable? = null) {
        // Android Log is unavailable in plain JVM unit tests; never crash the app for logging.
        try {
            if (error == null) {
                Log.w(TAG, message)
            } else {
                Log.w(TAG, message, error)
            }
        } catch (_: RuntimeException) {
            // no-op on non-Android runtimes
        }
    }

    private companion object {
        const val TAG = "AcceptedOrdersRetention"
    }
}
