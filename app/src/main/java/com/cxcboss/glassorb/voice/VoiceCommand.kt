package com.cxcboss.glassorb.voice

sealed interface VoiceCommand {
    data class Torch(val enabled: Boolean) : VoiceCommand

    enum class VolumeAction {
        Raise,
        Lower,
        Mute,
        Restore,
    }

    data class MediaVolume(val action: VolumeAction) : VoiceCommand

    data class LaunchApp(val packageName: String, val label: String) : VoiceCommand
}

data class VoiceResolution(
    val command: VoiceCommand?,
    val confidence: Int,
    val explanation: String,
) {
    val isExecutable: Boolean get() = command != null && confidence >= HIGH_CONFIDENCE

    companion object {
        const val HIGH_CONFIDENCE = 78
    }
}

/**
 * Small deterministic command resolver. The ASR model only supplies text;
 * this layer owns the safety boundary and never executes partial recognition.
 */
object VoiceIntentResolver {
    private data class AppAlias(val label: String, val packageName: String, val aliases: List<String>)

    private val knownApps = listOf(
        AppAlias("微信", "com.tencent.mm", listOf("微信", "微讯")),
        AppAlias("抖音", "com.ss.android.ugc.aweme", listOf("抖音", "短视频")),
        AppAlias("支付宝", "com.eg.android.AlipayGphone", listOf("支付宝", "支富宝")),
        AppAlias("淘宝", "com.taobao.taobao", listOf("淘宝", "淘包")),
        AppAlias("设置", "com.android.settings", listOf("设置", "系统设置")),
    )

    private val fillerWords = listOf("请", "帮我", "一下", "好吗", "可以吗", "给我", "现在")

    fun resolve(rawText: String): VoiceResolution {
        val text = normalize(rawText)
        if (text.isEmpty()) return VoiceResolution(null, 0, "没有识别到指令")

        val torchObject = listOf("手电筒", "手电", "闪光灯", "照明", "灯").firstOrNull(text::contains)
        if (torchObject != null) {
            val turnOn = listOf("打开", "开启", "开一下", "点亮", "亮起来").any(text::contains)
            val turnOff = listOf("关闭", "关掉", "关上", "熄灭", "关一下").any(text::contains)
            if (turnOn == turnOff) {
                return VoiceResolution(null, 42, "请明确说打开或关闭手电筒")
            }
            return VoiceResolution(
                VoiceCommand.Torch(turnOn),
                if (text.contains(torchObject) && (turnOn || turnOff)) 96 else 80,
                if (turnOn) "打开手电筒" else "关闭手电筒",
            )
        }

        val volumeObject = text.contains("音量") || text.contains("声音") || text.contains("大声")
        if (volumeObject) {
            val raise = listOf("调大", "大一点", "大些", "增大", "加大", "调高", "高一点", "提高").any(text::contains)
            val lower = listOf("调小", "小一点", "小些", "减小", "调低", "低一点", "降低").any(text::contains)
            val mute = listOf("静音", "没声音", "关掉声音", "关闭声音").any(text::contains)
            val restore = listOf("恢复声音", "取消静音", "打开声音").any(text::contains)
            val matches = listOf(raise, lower, mute, restore).count { it }
            if (matches != 1) return VoiceResolution(null, 45, "请明确说调大、调小、静音或恢复声音")
            val action = when {
                raise -> VoiceCommand.VolumeAction.Raise
                lower -> VoiceCommand.VolumeAction.Lower
                mute -> VoiceCommand.VolumeAction.Mute
                else -> VoiceCommand.VolumeAction.Restore
            }
            return VoiceResolution(VoiceCommand.MediaVolume(action), 92, "已识别媒体音量操作")
        }

        val launch = listOf("打开", "启动", "进入", "运行").any(text::contains)
        if (launch) {
            val app = knownApps.firstOrNull { candidate -> candidate.aliases.any(text::contains) }
            if (app != null) {
                return VoiceResolution(
                    VoiceCommand.LaunchApp(app.packageName, app.label),
                    94,
                    "打开${app.label}",
                )
            }
            return VoiceResolution(null, 38, "没有找到已支持的应用，请说出微信、抖音等明确名称")
        }

        return VoiceResolution(null, 18, "暂不支持这条指令")
    }

    fun normalize(rawText: String): String {
        var normalized = rawText.lowercase()
            .replace(Regex("[，。！？,.!?、：:；;（）()「」【】\\s]"), "")
        fillerWords.forEach { normalized = normalized.replace(it, "") }
        normalized = normalized
            .replace("手电灯", "手电筒")
            .replace("闪电灯", "闪光灯")
            .replace("声量", "音量")
            .replace("声音量", "音量")
        return normalized
    }
}
