package com.cdndash.app.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

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
