package com.cxcboss.glassorb.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.cxcboss.glassorb.overlay.OrbOverlayService
import com.cxcboss.glassorb.overlay.OverlayRuntime

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GlassOrbTheme {
                val dark = isSystemInDarkTheme()
                SideEffect {
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = !dark
                        isAppearanceLightNavigationBars = !dark
                    }
                }
                val config by viewModel.config.collectAsStateWithLifecycle()
                val runtimeStatus by OverlayRuntime.status.collectAsStateWithLifecycle()
                LaunchedEffect(runtimeStatus, dark) {
                    if (runtimeStatus == com.cxcboss.glassorb.overlay.OverlayRuntimeStatus.Visible) {
                        OrbOverlayService.setAppAppearance(this@MainActivity, active = true, dark = dark)
                    }
                }
                var overlayPermission by remember { mutableStateOf(Settings.canDrawOverlays(this)) }
                val lifecycleOwner = LocalLifecycleOwner.current
                val overlayPermissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.StartActivityForResult(),
                ) {
                    overlayPermission = Settings.canDrawOverlays(this)
                }
                val notificationPermissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission(),
                ) { granted ->
                    if (!granted) {
                        Toast.makeText(this, "通知权限未开启，悬浮服务仍可运行", Toast.LENGTH_SHORT).show()
                    }
                }

                DisposableEffect(lifecycleOwner, dark) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            overlayPermission = Settings.canDrawOverlays(this@MainActivity)
                            if (OverlayRuntime.status.value == com.cxcboss.glassorb.overlay.OverlayRuntimeStatus.Visible) {
                                OrbOverlayService.setAppAppearance(this@MainActivity, active = true, dark = dark)
                            }
                        } else if (event == Lifecycle.Event.ON_PAUSE &&
                            OverlayRuntime.status.value == com.cxcboss.glassorb.overlay.OverlayRuntimeStatus.Visible
                        ) {
                            OrbOverlayService.setAppAppearance(this@MainActivity, active = false, dark = dark)
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }

                SettingsScreen(
                    config = config,
                    runtimeStatus = runtimeStatus,
                    overlayPermission = overlayPermission,
                    onRequestOverlayPermission = {
                        overlayPermissionLauncher.launch(
                            Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:$packageName"),
                            ),
                        )
                    },
                    onStartOverlay = {
                        if (!Settings.canDrawOverlays(this)) {
                            overlayPermissionLauncher.launch(
                                Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:$packageName"),
                                ),
                            )
                        } else {
                            if (
                                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                                PackageManager.PERMISSION_GRANTED
                            ) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            runCatching {
                                OrbOverlayService.start(this)
                                OrbOverlayService.setAppAppearance(this, active = true, dark = dark)
                            }
                                .onFailure { Toast.makeText(this, it.message ?: "无法启动悬浮层", Toast.LENGTH_LONG).show() }
                        }
                    },
                    onShowOverlay = { runCatching { OrbOverlayService.show(this) } },
                    onHideOverlay = { runCatching { OrbOverlayService.hide(this) } },
                    onStopOverlay = { runCatching { OrbOverlayService.stop(this) } },
                    onConfigChange = viewModel::update,
                    onPreset = viewModel::applyPreset,
                    onResetGroup = viewModel::reset,
                    onResetAll = viewModel::resetAll,
                    onExportJson = viewModel::exportJson,
                    onImportJson = viewModel::importJson,
                )
            }
        }
    }
}
