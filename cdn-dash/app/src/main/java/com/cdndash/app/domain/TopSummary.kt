package com.cdndash.app.domain

/**
 * 顶部汇总的单账号输入（spec「纯函数」、User Story 8/10/27）。
 *
 * @property enabled 停用账号完全不计入：既不累加，也不计入缺失数。
 * @property todayBytes 今日累计（未结算）字节数；尚未取到时为 null，按 0 处理。
 * @property weekBytes 最近 7 个已结算日合计字节数；尚未取到时为 null，按 0 处理。
 * @property failed 该账号取数是否失败。**账号级**失败：本仓 ViewModel 把今日与 7 天
 * 两路的失败合并成一个状态（先到先记、不互相覆盖），因此这里也按整体失败处理——
 * 任一路失败就两路合计都不含该账号，且只计一次缺失。失败时即使还留着上次成功的旧值，
 * 也不并入合计，避免合计被半新半旧的数据误导。
 */
data class TopSummaryAccount(
    val enabled: Boolean,
    val todayBytes: Long?,
    val weekBytes: Long?,
    val failed: Boolean,
)

/**
 * 看板顶部汇总：所有账号今日累计与最近 7 天总量，以及缺失账号数。
 *
 * 部分失败时合计只含成功账号，缺失数让使用者知道合计不是全量（User Story 27）；
 * 全部失败或无账号时两数为 0，不崩溃。
 *
 * @property missingAccountCount 失败且启用的账号数；停用账号的失败不算缺失。
 * 界面按「大于 0」决定是否显示「N 个账号数据缺失」。
 */
data class TopSummary(
    val todayTotalBytes: Long = 0L,
    val weekTotalBytes: Long = 0L,
    val missingAccountCount: Int = 0,
)

/**
 * 汇总所有账号的今日累计与 7 天总量（无状态纯函数，不读时钟、不读设备时区）。
 *
 * - 停用账号直接跳过（不累加、不计缺失）；
 * - 启用但失败的账号计入缺失数，且今日与 7 天两路都不累加；
 * - 尚未取到（null）但未失败的账号按 0 累加，不计缺失；
 * - 空列表返回全零，不崩溃。
 */
fun summarizeTop(accounts: List<TopSummaryAccount>): TopSummary {
    var todayTotal = 0L
    var weekTotal = 0L
    var missingCount = 0
    for (account in accounts) {
        if (!account.enabled) continue
        if (account.failed) {
            missingCount++
            continue
        }
        todayTotal += account.todayBytes ?: 0L
        weekTotal += account.weekBytes ?: 0L
    }
    return TopSummary(
        todayTotalBytes = todayTotal,
        weekTotalBytes = weekTotal,
        missingAccountCount = missingCount,
    )
}
