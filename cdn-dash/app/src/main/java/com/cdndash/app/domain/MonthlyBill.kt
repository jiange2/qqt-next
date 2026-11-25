package com.cdndash.app.domain

import java.math.BigDecimal
import java.time.YearMonth

/**
 * 一个账期的账号花费（GLOSSARY「账号花费」「账期口径」）。
 *
 * 金额一律用 [BigDecimal]，不经过 Double；展示时由 `formatMoney` 保留两位小数。
 * 只包含已出账的完整自然月，当月由票据 08 的端点剔除。
 *
 * @property month 账期（自然月）。
 * @property pretaxAmount 应付金额（`PretaxAmount`），柱状图主数。
 * @property cashAmount 现金支付部分（`CashAmount`）。
 * @property voucherAmount 代金券抵扣部分。
 * @property currency 币种代码，默认人民币。
 */
data class MonthlyBill(
    val month: YearMonth,
    val pretaxAmount: BigDecimal,
    val cashAmount: BigDecimal,
    val voucherAmount: BigDecimal,
    val currency: String = "CNY",
)
