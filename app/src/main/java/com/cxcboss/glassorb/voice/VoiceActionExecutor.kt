package com.cxcboss.glassorb.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.Build
import androidx.core.content.ContextCompat

sealed interface VoiceActionResult {
    data class Success(val message: String) : VoiceActionResult
    data class Failure(val message: String) : VoiceActionResult
    data class PermissionRequired(val permission: VoicePermission) : VoiceActionResult
}

class VoiceActionExecutor(private val context: Context) {
    private var lastNonMutedVolume: Int? = null

    fun execute(command: VoiceCommand): VoiceActionResult = runCatching {
        when (command) {
            is VoiceCommand.Torch -> setTorch(command.enabled)
            is VoiceCommand.MediaVolume -> changeMediaVolume(command.action)
            is VoiceCommand.LaunchApp -> launchApp(command)
        }
    }.getOrElse { error ->
        VoiceActionResult.Failure(error.message ?: "执行指令失败")
    }

    private fun setTorch(enabled: Boolean): VoiceActionResult {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return VoiceActionResult.Failure("当前 Android 版本不支持手电筒控制")
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            return VoiceActionResult.PermissionRequired(VoicePermission.Camera)
        }
        val manager = context.getSystemService(CameraManager::class.java)
        val cameraId = manager.cameraIdList.firstOrNull { id ->
            manager.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } ?: return VoiceActionResult.Failure("设备没有可用的手电筒")
        return try {
            manager.setTorchMode(cameraId, enabled)
            VoiceActionResult.Success(if (enabled) "手电筒已打开" else "手电筒已关闭")
        } catch (_: SecurityException) {
            VoiceActionResult.PermissionRequired(VoicePermission.Camera)
        } catch (_: Throwable) {
            VoiceActionResult.Failure("手电筒暂时不可用，可能被其他应用占用")
        }
    }

    private fun changeMediaVolume(action: VoiceCommand.VolumeAction): VoiceActionResult {
        val manager = context.getSystemService(AudioManager::class.java)
        if (manager.isVolumeFixed) return VoiceActionResult.Failure("当前设备的媒体音量由系统固定")
        val stream = AudioManager.STREAM_MUSIC
        val current = manager.getStreamVolume(stream)
        if (current > 0) lastNonMutedVolume = current
        when (action) {
            VoiceCommand.VolumeAction.Raise -> manager.adjustStreamVolume(stream, AudioManager.ADJUST_RAISE, 0)
            VoiceCommand.VolumeAction.Lower -> manager.adjustStreamVolume(stream, AudioManager.ADJUST_LOWER, 0)
            VoiceCommand.VolumeAction.Mute -> manager.setStreamVolume(stream, 0, 0)
            VoiceCommand.VolumeAction.Restore -> manager.setStreamVolume(
                stream,
                (lastNonMutedVolume ?: (manager.getStreamMaxVolume(stream) / 2)).coerceAtLeast(1),
                0,
            )
        }
        val message = when (action) {
            VoiceCommand.VolumeAction.Raise -> "媒体音量已调高"
            VoiceCommand.VolumeAction.Lower -> "媒体音量已调低"
            VoiceCommand.VolumeAction.Mute -> "媒体音量已静音"
            VoiceCommand.VolumeAction.Restore -> "媒体音量已恢复"
        }
        return VoiceActionResult.Success(message)
    }

    private fun launchApp(command: VoiceCommand.LaunchApp): VoiceActionResult {
        val packageManager = context.packageManager
        val launchIntent = packageManager.getLaunchIntentForPackage(command.packageName)
        if (launchIntent != null) {
            context.startActivity(launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return VoiceActionResult.Success("正在打开${command.label}")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return runCatching {
                val sender = packageManager.getLaunchIntentSenderForPackage(command.packageName)
                context.startIntentSender(sender, null, Intent.FLAG_ACTIVITY_NEW_TASK, Intent.FLAG_ACTIVITY_NEW_TASK, 0)
                VoiceActionResult.Success("正在打开${command.label}")
            }.getOrElse { VoiceActionResult.Failure("没有找到${command.label}，可能尚未安装") }
        }
        return VoiceActionResult.Failure("没有找到${command.label}，可能尚未安装")
    }
}
