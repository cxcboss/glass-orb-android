package com.cxcboss.glassorb.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.cxcboss.glassorb.R
import com.cxcboss.glassorb.data.OrbConfigRepository
import com.cxcboss.glassorb.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class OrbOverlayService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var controller: OverlayWindowController
    private lateinit var notificationManager: NotificationManager
    private var overlayVisible = false
    private var receiverRegistered = false
    private var preserveTerminalStatus = false

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> controller.setScreenOn(false)
                Intent.ACTION_SCREEN_ON -> controller.setScreenOn(true)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NotificationManager::class.java)
        createNotificationChannel()
        controller = OverlayWindowController(this, ::handleFatalError)
        AccessibilityOverlayBridge.attachController(
            handler = controller::dispatchAccessibilityTouch,
            provider = controller::currentAccessibilityTouchBounds,
            onConnectionChanged = controller::refreshTouchBoundsForAccessibility,
        )
        registerScreenReceiver()
        val powerManager = getSystemService(PowerManager::class.java)
        controller.setScreenOn(powerManager.isInteractive)
        serviceScope.launch {
            OrbConfigRepository.getInstance(this@OrbOverlayService).config.collectLatest(controller::updateConfig)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        if (action == ACTION_STOP) {
            stopOverlayService()
            return START_NOT_STICKY
        }

        startInForeground()
        when (action) {
            ACTION_HIDE -> {
                controller.hide()
                overlayVisible = false
                OverlayRuntime.update(OverlayRuntimeStatus.Hidden)
                refreshNotification()
            }

            ACTION_START, ACTION_SHOW -> {
                if (controller.show()) {
                    overlayVisible = true
                    OverlayRuntime.update(OverlayRuntimeStatus.Visible)
                    refreshNotification()
                }
            }

            ACTION_APP_APPEARANCE -> controller.setAppAppearance(
                active = intent?.getBooleanExtra(EXTRA_APP_ACTIVE, false) == true,
                dark = intent?.getBooleanExtra(EXTRA_DARK_MODE, false) == true,
            )
            ACTION_TOUCH_PREVIEW -> controller.setTouchPreview(intent?.getBooleanExtra(EXTRA_VISIBLE, false) == true)
        }
        return START_NOT_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        controller.onConfigurationChanged()
    }

    override fun onDestroy() {
        AccessibilityOverlayBridge.detachController()
        controller.destroy()
        if (receiverRegistered) {
            try {
                unregisterReceiver(screenReceiver)
            } catch (_: IllegalArgumentException) {
                // The framework may have already detached the receiver while tearing down the process.
            }
        }
        serviceScope.cancel()
        if (!preserveTerminalStatus) OverlayRuntime.update(OverlayRuntimeStatus.Stopped)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startInForeground() {
        val foregroundType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(),
            foregroundType,
        )
    }

    private fun refreshNotification() {
        notificationManager.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val openSettings = PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val visibilityAction = if (overlayVisible) ACTION_HIDE else ACTION_SHOW
        val visibilityLabel = if (overlayVisible) "隐藏" else "显示"
        val visibilityIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, OrbOverlayService::class.java).setAction(visibilityAction),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            this,
            3,
            Intent(this, OrbOverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val statusText = if (overlayVisible) "点击胶囊展开，上滑小球收起" else "悬浮层已隐藏"
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_orb)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(statusText)
            .setContentIntent(openSettings)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, visibilityLabel, visibilityIntent)
            .addAction(0, "打开设置", openSettings)
            .addAction(0, "停止", stopIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notification_channel_description)
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun registerScreenReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(screenReceiver, filter)
        }
        receiverRegistered = true
    }

    private fun handleFatalError(message: String) {
        preserveTerminalStatus = true
        overlayVisible = false
        controller.destroy()
        OverlayRuntime.update(
            if (message.contains("权限")) OverlayRuntimeStatus.PermissionRequired else OverlayRuntimeStatus.Error(message),
        )
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun stopOverlayService() {
        preserveTerminalStatus = false
        controller.destroy()
        overlayVisible = false
        OverlayRuntime.update(OverlayRuntimeStatus.Stopped)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    companion object {
        const val ACTION_START = "com.cxcboss.glassorb.action.START"
        const val ACTION_SHOW = "com.cxcboss.glassorb.action.SHOW"
        const val ACTION_HIDE = "com.cxcboss.glassorb.action.HIDE"
        const val ACTION_STOP = "com.cxcboss.glassorb.action.STOP"
        const val ACTION_APP_APPEARANCE = "com.cxcboss.glassorb.action.APP_APPEARANCE"
        const val ACTION_TOUCH_PREVIEW = "com.cxcboss.glassorb.action.TOUCH_PREVIEW"
        private const val EXTRA_APP_ACTIVE = "app_active"
        private const val EXTRA_DARK_MODE = "dark_mode"
        private const val EXTRA_VISIBLE = "visible"

        private const val CHANNEL_ID = "glass_orb_overlay"
        private const val NOTIFICATION_ID = 27

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, OrbOverlayService::class.java).setAction(ACTION_START),
            )
        }

        fun show(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, OrbOverlayService::class.java).setAction(ACTION_SHOW),
            )
        }

        fun hide(context: Context) {
            context.startService(Intent(context, OrbOverlayService::class.java).setAction(ACTION_HIDE))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, OrbOverlayService::class.java).setAction(ACTION_STOP))
        }

        fun setAppAppearance(context: Context, active: Boolean, dark: Boolean) {
            context.startService(
                Intent(context, OrbOverlayService::class.java)
                    .setAction(ACTION_APP_APPEARANCE)
                    .putExtra(EXTRA_APP_ACTIVE, active)
                    .putExtra(EXTRA_DARK_MODE, dark),
            )
        }

        fun setTouchPreview(context: Context, visible: Boolean) {
            context.startService(
                Intent(context, OrbOverlayService::class.java)
                    .setAction(ACTION_TOUCH_PREVIEW)
                    .putExtra(EXTRA_VISIBLE, visible),
            )
        }
    }
}
