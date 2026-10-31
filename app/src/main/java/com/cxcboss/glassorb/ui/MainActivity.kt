package com.cxcboss.glassorb.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.graphics.Color
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.BackEventCompat
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.cxcboss.glassorb.R
import com.cxcboss.glassorb.overlay.OrbOverlayService
import com.cxcboss.glassorb.overlay.AccessibilityStatus
import com.cxcboss.glassorb.overlay.OverlayRuntime
import com.cxcboss.glassorb.overlay.OverlayRuntimeStatus
import com.cxcboss.glassorb.data.AutoStartPreferences
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    private lateinit var settingsController: NativeSettingsController
    private var overlayPermission = false
    private var darkMode = false

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        updateOverlayPermission()
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted) Toast.makeText(this, R.string.toast_notification_denied, Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Android 15/16 enforce edge-to-edge for modern target SDKs. Keep the
        // content behind transparent system bars; the settings controller
        // turns the insets into list padding and glass-chrome placement.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        darkMode = isSystemDarkMode()
        updateSystemBars()

        val root = FrameLayout(this).apply {
            id = View.generateViewId()
            clipToPadding = false
        }
        // Content draws under the (transparent) system bars; the settings
        // surface positions its glass chrome and list padding from the insets.
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
            )
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            if (::settingsController.isInitialized) settingsController.applyInsets(bars.top, bars.bottom, ime)
            insets
        }
        settingsController = NativeSettingsController(
            activity = this,
            root = root,
            callbacks = NativeSettingsCallbacks(
                requestOverlayPermission = ::requestOverlayPermission,
                requestAccessibilityPermission = ::requestAccessibilityPermission,
                startOverlay = ::startOverlay,
                showOverlay = ::showOverlay,
                hideOverlay = ::hideOverlay,
                stopOverlay = ::stopOverlay,
                updateConfig = viewModel::update,
                resetGroup = viewModel::reset,
                setAutoStartEnabled = { enabled -> AutoStartPreferences.setEnabled(this, enabled) },
            ),
        )
        setContentView(root)
        ViewCompat.requestApplyInsets(root)
        settingsController.updateAccessibilityEnabled(AccessibilityStatus.isGlassOrbServiceEnabled(this))
        settingsController.updateAutoStartEnabled(AutoStartPreferences.isEnabled(this))
        // ComponentActivity bridges this standard dispatcher callback to the
        // platform predictive-back contract. Internal pages follow gesture
        // progress and cancellation; the root delegates back to the system.
        val backCallback = object : OnBackPressedCallback(settingsController.canGoBack) {
            override fun handleOnBackPressed() {
                settingsController.goBack()
            }
            override fun handleOnBackStarted(backEvent: BackEventCompat) = settingsController.startPredictiveBack(backEvent)
            override fun handleOnBackProgressed(backEvent: BackEventCompat) = settingsController.progressPredictiveBack(backEvent)
            override fun handleOnBackCancelled() = settingsController.cancelPredictiveBack()
        }
        settingsController.onBackAvailabilityChanged = { backCallback.isEnabled = it }
        onBackPressedDispatcher.addCallback(this, backCallback)
        updateOverlayPermission()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.config.collect { settingsController.updateConfig(it) }
                }
                launch {
                    OverlayRuntime.status.collect { settingsController.updateRuntimeStatus(it) }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateOverlayPermission()
        if (::settingsController.isInitialized) {
            settingsController.updateAccessibilityEnabled(AccessibilityStatus.isGlassOrbServiceEnabled(this))
            settingsController.updateAutoStartEnabled(AutoStartPreferences.isEnabled(this))
        }
        darkMode = isSystemDarkMode()
        updateSystemBars()
        if (OverlayRuntime.status.value == OverlayRuntimeStatus.Visible) {
            OrbOverlayService.setAppAppearance(this, active = true, dark = darkMode)
        }
    }

    override fun onPause() {
        if (OverlayRuntime.status.value == OverlayRuntimeStatus.Visible) {
            OrbOverlayService.setAppAppearance(this, active = false, dark = darkMode)
        }
        super.onPause()
    }

    private fun requestOverlayPermission() {
        overlayPermissionLauncher.launch(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName"),
            ),
        )
    }

    private fun requestAccessibilityPermission() {
        // This is the public, OEM-compatible entry point. Android does not
        // expose a stable public per-service detail action, so do not rely on
        // hidden/vendor-only settings intents here.
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun startOverlay() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            !AccessibilityStatus.isGlassOrbServiceEnabled(this)
        ) {
            requestAccessibilityPermission()
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            requestOverlayPermission()
            return
        }
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        runCatching {
            OrbOverlayService.start(this)
            OrbOverlayService.setAppAppearance(this, active = true, dark = darkMode)
        }.onFailure { Toast.makeText(this, it.message ?: getString(R.string.toast_overlay_start_failed), Toast.LENGTH_LONG).show() }
    }

    private fun showOverlay() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            !AccessibilityStatus.isGlassOrbServiceEnabled(this)
        ) {
            requestAccessibilityPermission()
            return
        }
        runCatching {
            OrbOverlayService.show(this)
            OrbOverlayService.setAppAppearance(this, active = true, dark = darkMode)
        }.onFailure { Toast.makeText(this, it.message ?: getString(R.string.toast_overlay_show_failed), Toast.LENGTH_LONG).show() }
    }

    private fun hideOverlay() {
        runCatching { OrbOverlayService.hide(this) }
            .onFailure { Toast.makeText(this, it.message ?: getString(R.string.toast_overlay_hide_failed), Toast.LENGTH_LONG).show() }
    }

    private fun stopOverlay() {
        runCatching { OrbOverlayService.stop(this) }
            .onFailure { Toast.makeText(this, it.message ?: getString(R.string.toast_overlay_stop_failed), Toast.LENGTH_LONG).show() }
    }

    private fun updateOverlayPermission() {
        overlayPermission = Settings.canDrawOverlays(this)
        if (::settingsController.isInitialized) settingsController.updateOverlayPermission(overlayPermission)
    }

    private fun updateSystemBars() {
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !darkMode
            isAppearanceLightNavigationBars = !darkMode
        }
    }

    private fun isSystemDarkMode(): Boolean =
        resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
}
