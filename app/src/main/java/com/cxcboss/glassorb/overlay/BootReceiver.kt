package com.cxcboss.glassorb.overlay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import com.cxcboss.glassorb.data.AutoStartPreferences

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val appContext = context.applicationContext
        val accessibilityRequired = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        val shouldStart = AutoStartPolicy.shouldStartOnBoot(
            action = intent?.action,
            enabled = AutoStartPreferences.isEnabled(appContext),
            overlayPermission = Settings.canDrawOverlays(appContext),
            accessibilityEnabled = AccessibilityStatus.isGlassOrbServiceEnabled(appContext),
            accessibilityRequired = accessibilityRequired,
        )
        if (!shouldStart) return

        runCatching { OrbOverlayService.start(appContext) }
    }
}
