package com.cxcboss.glassorb.overlay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.Parcel
import android.os.RemoteException
import android.util.Log

/**
 * Adapter for Xiaomi HyperOS' fair-running-memory callbacks.
 *
 * The protocol is intentionally isolated here: the overlay itself does not
 * need Xiaomi classes or reflection. On a non-Xiaomi ROM this receiver simply
 * never receives anything. The receiver does not sample memory; Xiaomi sends
 * the PSS/heap values in the broadcast, and the app only performs the small
 * amount of work it can safely complete before the three-second deadline.
 */
internal class XiaomiMemoryAdvisor(
    context: Context,
    private val onMemoryPressure: () -> Unit,
) {
    private val appContext = context.applicationContext
    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != ACTION_TRIM) return
            handle(intent)
        }
    }

    fun register() {
        if (registered) return
        val filter = IntentFilter(ACTION_TRIM)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                appContext.registerReceiver(receiver, filter, null, null, Context.RECEIVER_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                appContext.registerReceiver(receiver, filter)
            }
            registered = true
        } catch (error: Throwable) {
            // This is an OEM optimization hook. A ROM that rejects the
            // optional registration must not affect the overlay service.
            Log.w(TAG, "Unable to register HyperOS memory advisor", error)
        }
    }

    fun unregister() {
        if (!registered) return
        runCatching { appContext.unregisterReceiver(receiver) }
        registered = false
    }

    private fun handle(intent: Intent) {
        val common = intent.extras?.getBundle(KEY_COMMON) ?: return
        val notifyType = common.getInt(KEY_NOTIFY_TYPE, INVALID_INT)
        val notifyId = common.getInt(KEY_NOTIFY_ID, INVALID_INT)
        val callback = common.get(KEY_CALLBACK) as? IBinder

        // The app has no image cache, WebView, or unbounded Java cache. Its
        // only reclaimable runtime work is the submit-driven GL loop while the
        // orb is idle; that hook is deliberately bounded and synchronous.
        runCatching { onMemoryPressure() }
            .onFailure { Log.w(TAG, "Memory pressure handling failed", it) }

        // KILL/TRIM are both acknowledged with the documented Binder shape.
        // A missing or already-dead callback is harmless and must not crash a
        // foreground service on a vendor ROM.
        if (callback != null && notifyType != INVALID_INT && notifyId != INVALID_INT) {
            reply(callback, notifyType, notifyId)
        }
    }

    private fun reply(callback: IBinder, notifyType: Int, notifyId: Int) {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInt(notifyType)
            data.writeInt(notifyId)
            data.writeInt(RESULT_SUCCESS)
            data.writeBundle(Bundle())
            callback.transact(IBinder.FIRST_CALL_TRANSACTION, data, reply, IBinder.FLAG_ONEWAY)
        } catch (_: RemoteException) {
            // The system-side callback may have died during a kill decision.
        } catch (error: Throwable) {
            Log.w(TAG, "Unable to acknowledge HyperOS memory advisor", error)
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    private companion object {
        const val TAG = "XiaomiMemoryAdvisor"
        const val ACTION_TRIM = "itgsa.intent.action.TRIM"
        const val KEY_COMMON = "common"
        const val KEY_CALLBACK = "callback"
        const val KEY_NOTIFY_TYPE = "notifyType"
        const val KEY_NOTIFY_ID = "notifyId"
        const val RESULT_SUCCESS = 0
        const val INVALID_INT = Int.MIN_VALUE
    }
}
