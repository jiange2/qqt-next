package com.cdndash.app.domain

import java.time.Instant
import java.time.LocalDate

/**
 * 今日累计（GLOSSARY「今日累计」）：北京自然日 00:00 至查询时刻的边缘出流量，未结算。
 *
 * @property bytes 时间窗内各数据点字节数之和；接口无数据点时为 0。
 * @property dataThrough 数据截止时刻。**口径统一取「最后一个数据点的 TimeStamp」**；
 * 接口没有返回任何数据点时退化为发起请求的时刻（[now]）。
 * 注意这不是请求完成时刻——它表示「数据覆盖到哪里」，卡片上显示为「截至 HH:mm」。
 */
data class TodayTraffic(
    val bytes: Long,
    val dataThrough: Instant,
)

/**
 * 一个原始流量数据点（接口的 Interval 粒度点）。
 * 票据 04 会用小时粒度点按北京自然日聚合为 [DayTrafficPoint]。
 *
 * @property bytes 该点的总边缘出流量（`Value`）。
 * @property domesticBytes 中国内地部分（`DomesticValue`）；旧快照/接口未返回时为 0。
 * @property overseasBytes 海外部分（`OverseasValue`，不含中国内地）；未返回时为 0。
 */
data class TrafficPoint(
    val at: Instant,
    val bytes: Long,
    val domesticBytes: Long = 0L,
    val overseasBytes: Long = 0L,
)

/**
 * 北京自然日 [date] 的边缘出流量，用于「最近 7 天」的已结算日（GLOSSARY「已结算日」）。
 *
 * @property bytes 当日总量；[domesticBytes]/[overseasBytes] 是区域拆分，三者同源。
 * 旧快照没有区域字段时两者为 0，估算层据此识别「区域数据缺失」而不当作零流量。
 */
data class DayTrafficPoint(
    val date: LocalDate,
    val bytes: Long,
    val domesticBytes: Long = 0L,
    val overseasBytes: Long = 0L,
)
