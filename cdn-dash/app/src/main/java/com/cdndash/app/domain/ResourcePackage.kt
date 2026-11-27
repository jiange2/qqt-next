package com.cdndash.app.domain

import java.time.Instant

/**
 * 预付费 CDN 流量包（GLOSSARY「资源包」），字段与 GLOSSARY「区域」「资源包余量视图」口径对齐。
 *
 * 只保留 CDN 流量包条目由票据 05 的端点负责过滤；本对象只表达一个包的事实，
 * 分组、求和与「未生效」标注都是纯函数（票据 05），不在数据类里做。
 *
 * @property instanceId 资源包实例 id，用于稳定 key 与去重。
 * @property commodityCode 商品码；旧版 CDN 流量包为 `cdnflowbag`，新版 CDN/全站加速共享包为 `dcdnpaybag`。
 * @property region 适用范围（中国内地 / 海外）；不同区域不可互相抵扣。
 * @property totalBytes 包总量（字节）。
 * @property remainingBytes 剩余量（字节）。
 * @property effectiveAt 生效时刻；晚于当前时刻即「未生效」。
 * @property expiresAt 到期时刻。
 */
data class ResourcePackage(
    val instanceId: String,
    val commodityCode: String,
    val region: String,
    val totalBytes: Long,
    val remainingBytes: Long,
    val effectiveAt: Instant,
    val expiresAt: Instant,
) {
    companion object {
        /** 旧版 CDN 流量包商品码（spec「资源包余量」）。 */
        const val CDN_FLOW_PACKAGE_COMMODITY_CODE: String = "cdnflowbag"

        /**
         * 新版 CDN/全站加速共享资源包商品码（官方购买入口与 DCDN 接口返回示例均为该值）。
         * 该商品也涵盖静态 HTTPS 请求数包，端点需再按容量单位（Byte）区分流量包。
         */
        const val SHARED_FLOW_PACKAGE_COMMODITY_CODE: String = "dcdnpaybag"

        /** 可抵扣 CDN 下行流量的商品码集合；全站加速专用、OSS 等其他商品码一律排除。 */
        val FLOW_PACKAGE_COMMODITY_CODES: Set<String> =
            setOf(CDN_FLOW_PACKAGE_COMMODITY_CODE, SHARED_FLOW_PACKAGE_COMMODITY_CODE)

        /** 接口没有返回区域时的兜底文案：宁可显示未知，也不把不同区域混成一组。 */
        const val UNKNOWN_REGION: String = "未知区域"

        /** 中国内地：对应流量数据点的 `DomesticValue`，估算天数时只与本区域包配对。 */
        const val MAINLAND_REGION: String = "中国内地"

        /** 海外：对应流量数据点的 `OverseasValue`（全球不含中国内地）。 */
        const val OVERSEAS_REGION: String = "海外"
    }
}

/**
 * 把资源包接口的 `Region` 归一到 GLOSSARY「区域」的两个已知值。
 *
 * CDN/DCDN 资源包接口实际返回区域码（文档示例 `CN`，海外为 `OVERSEAS`），
 * 直接拿来与「中国内地/海外」比对永远不相等，会让预计可用天数误判成「区域未知」而静默隐藏。
 * 无法识别的值原样返回（保留展示，但不参与天数估算）。
 */
fun canonicalRegion(raw: String): String {
    val trimmed = raw.trim()
    return when (trimmed.uppercase()) {
        "CN", "CHINA", "MAINLAND", "DOMESTIC", "中国内地", "中国大陆", "中国" ->
            ResourcePackage.MAINLAND_REGION

        "OVERSEAS", "OVERSEA", "INTERNATIONAL", "海外", "国际" ->
            ResourcePackage.OVERSEAS_REGION

        else -> trimmed
    }
}
