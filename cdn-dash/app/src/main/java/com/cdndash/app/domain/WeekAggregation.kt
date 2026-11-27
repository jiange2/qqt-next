package com.cdndash.app.domain

import java.time.Instant
import java.time.LocalDate

/** 「最近 7 天」固定 7 个已结算日（GLOSSARY「已结算日」），恒缺点数而不是「有多少数据画多少点」。 */
const val SETTLED_WEEK_DAY_COUNT: Int = 7

/**
 * 已结算 7 天的时间窗：北京时间「今天-7」00:00 至「今天」00:00（半开区间，不含今天）。
 *
 * 用小时粒度（`Interval=3600`）而不是日粒度：日粒度数据有 4–24 小时延迟，
 * 昨日可能只返回半截；小时点回来后再在端上按北京自然日归桶（spec「数据口径与接口契约」）。
 * 只按 [BEIJING_ZONE] 计算，设备时区怎么改都得到同一结果。
 */
fun settledWeekWindow(now: Instant): TimeWindow {
    val today = now.atZone(BEIJING_ZONE).toLocalDate()
    return TimeWindow(
        start = beijingDayStart(today.minusDays(SETTLED_WEEK_DAY_COUNT.toLong())),
        end = beijingDayStart(today),
    )
}

/**
 * 把小时点按北京自然日聚合为「最近 7 个已结算日」的日点。
 *
 * - 每点先换算到 [BEIJING_ZONE] 再取日期，23:55（北京）与次日 00:05 落在不同天；
 * - 落在今天（或窗口之外）的点一律丢弃：今天未结算，半日数据不污染 7 天走势；
 * - 输出恒定 [SETTLED_WEEK_DAY_COUNT] 个点，按日期升序，缺数据的日补 0；
 * - 区域拆分（内地/海外）随总量一起按日求和，供资源包预计可用天数使用；
 * - 空输入返回 7 个全零点，不崩溃。
 *
 * 无状态纯函数：不读系统时钟、不读设备时区（spec「纯函数」）。
 */
fun aggregateSettledDays(
    points: List<TrafficPoint>,
    now: Instant,
): List<DayTrafficPoint> {
    val today = now.atZone(BEIJING_ZONE).toLocalDate()
    val firstDay = today.minusDays(SETTLED_WEEK_DAY_COUNT.toLong())
    // LinkedHashMap 的插入顺序就是日期升序；先铺满 7 个 0 再累加，缺日自然补 0。
    val totalsByDate = linkedMapOf<LocalDate, DailyTotals>()
    repeat(SETTLED_WEEK_DAY_COUNT) { offset ->
        totalsByDate[firstDay.plusDays(offset.toLong())] = DailyTotals()
    }
    for (point in points) {
        val date = point.at.atZone(BEIJING_ZONE).toLocalDate()
        // 今天以及窗口之外的日期不在键里，直接跳过（「落在今天的点一律丢弃」）。
        val totals = totalsByDate[date] ?: continue
        totals.add(point)
    }
    return totalsByDate.map { (date, totals) ->
        DayTrafficPoint(
            date = date,
            bytes = totals.bytes,
            domesticBytes = totals.domesticBytes,
            overseasBytes = totals.overseasBytes,
        )
    }
}

/** 一天的三个计数器；区域拆分独立累加，不参与「今天丢弃」之外的任何筛选。 */
private class DailyTotals {
    var bytes: Long = 0L
    var domesticBytes: Long = 0L
    var overseasBytes: Long = 0L

    fun add(point: TrafficPoint) {
        bytes += point.bytes
        domesticBytes += point.domesticBytes
        overseasBytes += point.overseasBytes
    }
}

/** 7 天合计（字节）；空列表返回 0，与「空数据展示全零」一致。 */
fun weekTotal(days: List<DayTrafficPoint>): Long = days.sumOf { it.bytes }
