package com.cxcboss.glassorb.overlay

import android.graphics.Region
import android.view.View
import android.view.ViewTreeObserver
import java.lang.reflect.Proxy

/**
 * Registers an actual WindowManager input region, rather than rejecting events
 * after they have already blocked the app underneath. These AOSP greylist APIs
 * have no public equivalent. Failure is explicit: never leave a full-canvas
 * touch interceptor behind on a device that does not expose the API.
 */
internal class WindowInputRegion(private val host: View) : AutoCloseable {
    private val region = Region()
    private val observer = host.viewTreeObserver
    private val listenerType = Class.forName("android.view.ViewTreeObserver\$OnComputeInternalInsetsListener")
    private val infoType = Class.forName("android.view.ViewTreeObserver\$InternalInsetsInfo")
    // These members are public in the framework class but hidden from the
    // public SDK. getDeclared* also works on vendor builds that do not expose
    // hidden members through Class.getField/getMethod.
    private val touchableRegion = infoType.getDeclaredField("touchableRegion").apply {
        isAccessible = true
    }
    private val setMode = infoType.getDeclaredMethod("setTouchableInsets", Int::class.javaPrimitiveType).apply {
        isAccessible = true
    }
    private val listener = Proxy.newProxyInstance(listenerType.classLoader, arrayOf(listenerType)) { proxy, method, args ->
        when (method.name) {
            "onComputeInternalInsets" -> {
                // This callback runs from ViewRootImpl during layout/input
                // dispatch. Never allow a vendor reflection failure to escape
                // into the process: this process also hosts the accessibility
                // service, and an uncaught exception can make an OEM disable
                // that service immediately.
                runCatching {
                    val info = requireNotNull(args?.first())
                    setMode.invoke(info, 3) // TOUCHABLE_INSETS_REGION
                    (touchableRegion.get(info) as Region).set(region)
                }
                null
            }
            "hashCode" -> System.identityHashCode(proxy)
            "equals" -> proxy === args?.firstOrNull()
            "toString" -> "GlassOrbInputRegion"
            else -> null
        }
    }

    init {
        ViewTreeObserver::class.java.getDeclaredMethod("addOnComputeInternalInsetsListener", listenerType).apply {
            isAccessible = true
        }.invoke(observer, listener)
    }

    fun update(next: Region) {
        if (region == next) return
        region.set(next)
        host.requestLayout()
    }

    override fun close() {
        if (observer.isAlive) runCatching {
            ViewTreeObserver::class.java.getDeclaredMethod("removeOnComputeInternalInsetsListener", listenerType).apply {
                isAccessible = true
            }.invoke(observer, listener)
        }
    }
}
