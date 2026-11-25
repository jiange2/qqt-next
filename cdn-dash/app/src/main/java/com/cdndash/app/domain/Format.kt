package com.cdndash.app.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

private val BYTE_UNITS = arrayOf("B", "KB", "MB", "GB", "TB")

/**
 * 字节数按 1024 进制格式化（spec「工程与依赖」：字节一律按 1024 进制）。
 * B 不保留小数；KB 及以上保留一位小数，超出 TB 仍以 TB 计数。
 */
fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    var value = bytes.toDouble()
    var unitIndex = 0
    while (value >= 1024 && unitIndex < BYTE_UNITS.lastIndex) {
        value /= 1024
        unitIndex++
    }
    return String.format(Locale.US, "%.1f %s", value, BYTE_UNITS[unitIndex])
}

/**
 * 金额保留两位小数（spec「工程与依赖」），四舍五入，不带货币符号与千分位，
 * 由界面决定前缀（如「¥」）。
 *
 * 领域金额一律是 [BigDecimal]（spec：金额不用 Double）；Double 重载只为兼容既有调用。
 */
fun formatMoney(amount: BigDecimal): String =
    amount.setScale(2, RoundingMode.HALF_UP).toPlainString()

/** Double 入参的兼容入口，转 BigDecimal 后再格式化，避免二进制浮点误差。 */
fun formatMoney(amount: Double): String = formatMoney(BigDecimal.valueOf(amount))
