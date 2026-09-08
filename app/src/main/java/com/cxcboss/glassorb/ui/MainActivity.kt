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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.cxcboss.glassorb.overlay.OrbOverlayService
import com.cxcboss.glassorb.overlay.AccessibilityStatus
import com.cxcboss.glassorb.overlay.OverlayRuntime
import com.cxcboss.glassorb.overlay.OverlayRuntimeStatus
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
        if (!granted) Toast.makeText(this, "通知权限未开启，悬浮服务仍可运行", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Android 15/16 enforce edge-to-edge for modern target SDKs. Keep the
        // content behind transparent system bars and apply their insets once
        // to the root so every Material control retains its touch target.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        darkMode = isSystemDarkMode()
        updateSystemBars()

        val root = FrameLayout(this).apply {
            id = View.generateViewId()
            clipToPadding = false
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
            )
            view.setPadding(0, bars.top, 0, bars.bottom)
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
                applyPreset = viewModel::applyPreset,
                resetGroup = viewModel::reset,
                resetAll = viewModel::resetAll,
                exportJson = viewModel::exportJson,
                importJson = viewModel::importJson,
            ),
        )
        setContentView(root)
        ViewCompat.requestApplyInsets(root)
        settingsController.updateAccessibilityEnabled(AccessibilityStatus.isGlassOrbServiceEnabled(this))
        // ComponentActivity bridges this standard dispatcher callback to the
        // platform predictive-back contract on Android 13+. Do not move pages
        // ourselves: the system owns the gesture animation and commits the pop.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!settingsController.goBack()) finish()
            }
        })
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
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun startOverlay() {
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
        }.onFailure { Toast.makeText(this, it.message ?: "无法启动悬浮层", Toast.LENGTH_LONG).show() }
    }

    private fun showOverlay() {
        runCatching {
            OrbOverlayService.show(this)
            OrbOverlayService.setAppAppearance(this, active = true, dark = darkMode)
        }.onFailure { Toast.makeText(this, it.message ?: "无法显示悬浮层", Toast.LENGTH_LONG).show() }
    }

    private fun hideOverlay() {
        runCatching { OrbOverlayService.hide(this) }
            .onFailure { Toast.makeText(this, it.message ?: "无法隐藏悬浮层", Toast.LENGTH_LONG).show() }
    }

    private fun stopOverlay() {
        runCatching { OrbOverlayService.stop(this) }
            .onFailure { Toast.makeText(this, it.message ?: "无法停止悬浮层", Toast.LENGTH_LONG).show() }
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
