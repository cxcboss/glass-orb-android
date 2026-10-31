package com.cxcboss.glassorb.ui

import android.content.Context

/**
 * Settings copy. Chinese is the source language and is used verbatim; every
 * other locale receives the English mapping (unmapped keys fall back to the
 * source text rather than failing).
 */
internal class SettingsText(private val context: Context) {
    private val chinese: Boolean
        get() = context.resources.configuration.locales[0].language.equals("zh", ignoreCase = true)

    fun tr(source: String): String = if (chinese) source else ENGLISH[source] ?: source

    private companion object {
        val ENGLISH = mapOf(
            "常用" to "Common", "详细" to "Details", "常用设置" to "Common settings", "详细设置" to "All settings",
            "返回" to "Back", "设置页面切换" to "Settings page switch", "当前页面" to "Current page", "切换页面" to "Switch page", "全部设置" to "All settings", "其它" to "Other",
            "灵动岛" to "Dynamic Island", "显示灵动岛" to "Show Dynamic Island", "向下展开" to "Expand downward",
            "尺寸与位置" to "Size & position", "灵动岛大小" to "Dynamic Island size", "宽度" to "Width", "高度" to "Height",
            "位置" to "Position", "顶部偏移" to "Top offset", "水平" to "Horizontal", "玻璃球大小" to "Glass orb size",
            "背景效果" to "Background effects", "位置与触控" to "Position & touch", "位置、尺寸和触控" to "Position, size and touch",
            "颜色与光影" to "Color & lighting", "颜色、高光和阴影" to "Color, highlights and shadows",
            "背景与暗部" to "Background & shading", "三种背景效果与暗部" to "Background effects and shading",
            "波形与光带" to "Waves & light bands", "波形、色散和亮度" to "Waves, dispersion and brightness",
            "圆点与粒子" to "Dots & particles", "圆点、光晕和旋转" to "Dots, glow and rotation",
            "手势与动效" to "Gestures & motion", "展开、收起与拖动" to "Expand, collapse and drag",
            "性能与清晰度" to "Performance & clarity", "渲染比例与刷新率" to "Render scale and refresh rate",
            "运行与权限" to "Run & permissions", "运行权限" to "Run & permissions", "权限与系统兼容" to "Permissions & compatibility",
            "关于" to "About", "版本与来源" to "Version & sources", "状态" to "Status", "运行状态" to "Runtime status",
            "悬浮窗权限" to "Overlay permission", "已允许" to "Allowed", "未允许" to "Not allowed", "错误" to "Error",
            "状态栏触控" to "Status bar touch", "无障碍触控服务" to "Accessibility touch service",
            "已开启 · 用于状态栏触控" to "On · for status bar touch", "未开启 · 点击授权" to "Off · tap to allow",
            "仅用于状态栏触控和识别已知助手窗口；不读取文字、截图、录音或执行手势。" to "Used only for status-bar touch and known assistant windows. It does not read text, capture screens, record audio, or perform gestures.",
            "首页开关可显示或隐藏灵动岛；锁屏、安全页面和系统状态栏不保证显示。" to "The Home switch shows or hides Dynamic Island. It may not appear on lock screens, security pages, or system UI.",
            "重置本组" to "Reset this group", "恢复本组默认值" to "Restore group defaults", "恢复本组默认参数？" to "Restore group defaults?",
            "取消" to "Cancel", "恢复" to "Restore", "已恢复本组默认值" to "Group defaults restored",
            "左侧" to "Left", "居中" to "Center", "右侧" to "Right", "水平微调" to "Horizontal offset",
            "灵动岛尺寸" to "Dynamic Island size", "玻璃球" to "Glass orb", "全屏时隐藏灵动岛" to "Hide in fullscreen",
            "横屏隐藏" to "Hide in landscape", "全屏时隐藏。" to "Hide in fullscreen.", "横屏时隐藏。" to "Hide in landscape.", "球体直径" to "Orb diameter", "玻璃球宽度" to "Orb width", "玻璃球高度" to "Orb height",
            "外部效果余量" to "Effect margin", "效果画布比例" to "Effect canvas scale", "灵动岛触控" to "Dynamic Island touch",
            "扩大灵动岛触摸区域" to "Enlarge touch area", "触摸区域倍率" to "Touch-area scale",
            "玻璃形态与光影" to "Glass form & lighting", "内部深度" to "Inner depth", "曲率" to "Curvature", "高光" to "Highlights",
            "高光亮度" to "Highlight intensity", "高光宽度" to "Highlight width", "高光收束" to "Highlight focus", "动态颜色" to "Dynamic color",
            "内部颜色" to "Inner color", "上半部高光颜色" to "Upper highlight color", "上半部高光强度" to "Upper highlight intensity",
            "阴影与焦散" to "Shadows & caustics", "阴影" to "Shadow", "焦散强度" to "Caustic intensity", "阴影偏移" to "Shadow offset",
            "焦散偏移" to "Caustic offset", "光影柔度" to "Light softness", "球体暗部" to "Orb shading", "强度" to "Intensity",
            "渐隐跨度" to "Fade range", "暗部衰减" to "Shading falloff", "下层模糊" to "Lower-layer blur", "下层模糊度" to "Lower blur amount",
            "下层变暗" to "Lower-layer dim", "变暗程度" to "Dim amount", "玻璃球模糊" to "Glass orb blur", "玻璃球模糊度" to "Glass blur amount",
            "波形与色彩" to "Waves & color", "振幅" to "Amplitude", "尺度" to "Scale", "色散" to "Dispersion", "光带" to "Light band",
            "线宽" to "Line width", "亮度" to "Brightness", "填光" to "Fill light", "填光厚度" to "Fill thickness", "柔化" to "Softness",
            "白色辉光" to "White glow", "色相偏移" to "Hue shift", "粒子外观与运动" to "Particle look & motion",
            "环半径" to "Ring radius", "点半径" to "Dot radius", "辉光" to "Glow", "转速" to "Rotation speed",
            "上滑与拖动" to "Swipe up & drag", "收起跟手距离" to "Collapse distance", "拖拽弹性范围" to "Drag range", "拖拽响应系数" to "Drag response",
            "形变与回弹" to "Deformation & rebound", "形变位移上限" to "Deformation limit", "形变幅度" to "Deformation amount",
            "形变回弹反应" to "Rebound response", "形变回弹阻尼" to "Rebound damping", "展开与收起" to "Expand & collapse",
            "展开响应" to "Expand response", "展开阻尼" to "Expand damping", "收起响应" to "Collapse response", "收起阻尼" to "Collapse damping",
            "收起回弹幅度" to "Collapse rebound", "波形渐入延迟" to "Wave fade-in delay", "呼吸与点击" to "Breathing & press",
            "呼吸幅度" to "Breathing amount", "呼吸速度" to "Breathing speed", "点击放大" to "Press scale", "思考停留" to "Thinking duration",
            "自动收起" to "Auto-collapse", "等待时间" to "Delay", "帧率与清晰度" to "Frame rate & clarity", "展开、收起与玻璃球按 120 fps 请求刷新。" to "Expand, collapse, and Glass Orb request 120 fps refresh.", "渲染比例" to "Render scale",
            "未运行" to "Not running", "正在显示" to "Visible", "已隐藏" to "Hidden", "需要权限" to "Permission required", "运行异常" to "Runtime error",
            "还原" to "Reset", "还原默认值" to "reset to default", "调节" to "adjust", "参数" to "Settings",
            "应用图标" to "App icon", "App 版本" to "App version", "获取 App" to "Get the app", "关注抖音或微信公众号“你好小机”" to "Follow Douyin or the WeChat Official Account “Hello Xiaojī”.",
            "仓库参考" to "Repository reference", "打开参考仓库" to "Open reference repository", "玻璃球视觉与动画参考开源学习项目" to "Glass-orb visuals and motion reference an open-source study project.",
            "水平锚点" to "Horizontal anchor",
            "开机自动启动" to "Launch at boot",
            "启动" to "Startup",
            "已开启" to "On",
            "未开启" to "Off",
            "开机后自动显示灵动岛。需要悬浮窗权限；Android 12 及以上还需要开启无障碍触控服务。" to "Shows the Dynamic Island after boot. Requires the overlay permission; Android 12 and later also need the accessibility touch service.",
            "开源许可与声明" to "Open-source licenses & notices",
            "声明与来源" to "Notices & sources",
            "仅恢复“%s”中的参数。" to "Restore only the %s settings.",
            "灵动岛宽度" to "Dynamic Island width", "灵动岛高度" to "Dynamic Island height",
            "设置" to "Settings",
            "搜索" to "Search",
            "恢复默认值" to "Restore Default",
            "默认值：%s" to "Default: %s",
            "灵动岛与液态玻璃球视觉体验，非商业学习演示。" to "A Dynamic Island and liquid-glass orb experience. Non-commercial study demo.",
            "版本、来源与开源许可" to "Version, sources and open-source licenses",
            "调整灵动岛与玻璃球的尺寸、位置、显示时机和触控范围。" to "Size, position, visibility and touch area of the island and the orb.",
            "调整玻璃的深度、曲率、高光、动态颜色、阴影与焦散。" to "Depth, curvature, highlights, dynamic color, shadows and caustics of the glass.",
            "设置展开时的下层模糊、下层变暗、玻璃球模糊和球体暗部。" to "Lower-layer blur, lower-layer dim, orb blur and orb shading while expanded.",
            "调整玻璃球内部波形的振幅、色散、线宽与辉光。" to "Amplitude, dispersion, line width and glow of the waves inside the orb.",
            "调整思考状态下环形圆点的大小、辉光与转速。" to "Size, glow and speed of the ring of dots in the thinking state.",
            "调整展开、收起、拖动形变、呼吸和自动收起的手感。" to "Feel of expanding, collapsing, drag deformation, breathing and auto-collapse.",
            "调整渲染清晰度。展开、收起与玻璃球始终按 120 fps 请求刷新。" to "Rendering clarity. Expanding, collapsing and the orb always request 120 fps.",
            "管理悬浮窗权限、无障碍触控服务与开机自启。" to "Overlay permission, accessibility touch service and launch at boot.",
            "本应用参考与使用的开源项目、商标声明及完整许可证。" to "Open-source references, trademark notices and full license texts.",
            "输入设置名称，例如“高光”或“模糊”。" to "Type a setting name, for example “highlight” or “blur”.",
            "没有与“%s”相关的设置。" to "No settings match “%s”.",
        )
    }
}
