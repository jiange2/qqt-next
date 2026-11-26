package com.cdndash.app.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 全应用唯一时区：北京自然日。不读取设备时区（spec「数据口径与接口契约」），
 * 出差换时区、设备时区被改都不影响日界与「最近 7 天」的切分。
 */
val BEIJING_ZONE: ZoneId = ZoneId.of("Asia/Shanghai")

/** 北京自然日 [date] 的 00:00。 */
fun beijingDayStart(date: LocalDate): Instant =
    date.atStartOfDay(BEIJING_ZONE).toInstant()

/**
 * [now] 所在北京自然日的 00:00，即「今日累计」时间窗的起点。
 * 入参允许注入，方便后续票据以固定时刻跑纯函数测试。
 */
fun beijingTodayStart(now: Instant = Instant.now()): Instant =
    beijingDayStart(now.atZone(BEIJING_ZONE).toLocalDate())

/** 半开查询窗口 `[start, end]`；今日累计的 end 是请求时刻（未结算）。 */
data class TimeWindow(
    val start: Instant,
    val end: Instant,
)

/**
 * 「今日累计」的时间窗：北京时间今天 00:00 至 [now]。
 * 只按 [BEIJING_ZONE] 计算，设备时区怎么改都得到同一结果。
 */
fun todayTrafficWindow(now: Instant): TimeWindow =
    TimeWindow(start = beijingTodayStart(now), end = now)

private val BEIJING_HOUR_MINUTE: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** 北京时间 `HH:mm`，用于卡片上的「截至 HH:mm」；不随设备时区变化。 */
fun formatBeijingHourMinute(instant: Instant): String =
    BEIJING_HOUR_MINUTE.format(instant.atZone(BEIJING_ZONE))

/**
 * 快照抓取时刻（epoch millis）的北京时间 `HH:mm`，用于离线标注「数据时间 HH:mm（离线）」。
 * 只格式化快照自己记录的抓取时刻，不读取当前时间。
 */
fun formatBeijingHourMinute(epochMillis: Long): String =
    formatBeijingHourMinute(Instant.ofEpochMilli(epochMillis))

/**
 * 已结算 7 天的刷新节流：前台定时每 5 分钟只更新今日累计，7 天最多每小时取一次
 * （spec「冷启动请求量」：每账号首屏 2 次 = 今日 5 分钟 + 7 天 1 小时）。
 */
const val WEEK_REFRESH_INTERVAL_MILLIS: Long = 3_600_000L

/**
 * 定时刷新时是否该重取 7 天（纯函数，spec「纯函数」）。
 *
 * @param lastFetchedAtMillis 该账号上次成功取到 7 天的时刻；从未成功时为 null（视为到期）。
 * @param nowMillis 本次定时刷新的时刻。
 * @return 距上次成功已满 [intervalMillis] 或从未成功过时返回 true。
 */
fun isWeekRefreshDue(
    lastFetchedAtMillis: Long?,
    nowMillis: Long,
    intervalMillis: Long = WEEK_REFRESH_INTERVAL_MILLIS,
): Boolean = lastFetchedAtMillis == null || nowMillis - lastFetchedAtMillis >= intervalMillis
