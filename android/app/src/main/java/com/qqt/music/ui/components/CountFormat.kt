package com.qqt.music.ui.components

/** 播放量万化展示：12345 -> 1.2w，123456789 -> 1.2亿，不足一万原样 */
fun formatPlayCount(raw: String): String {
    val n = raw.toLongOrNull() ?: return ""
    val compact = when {
        n >= 100_000_000 -> String.format("%.1f", n / 100_000_000.0) + "亿"
        n >= 10_000 -> String.format("%.1f", n / 10_000.0) + "w"
        else -> n.toString()
    }
    return compact.removeSuffix(".0")
}
