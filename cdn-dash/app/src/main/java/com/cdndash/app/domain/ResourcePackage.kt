package com.cdndash.app.domain

import java.time.Instant

/**
 * 预付费 CDN 流量包（GLOSSARY「资源包」），字段与 GLOSSARY「区域」「资源包余量视图」口径对齐。
 *
 * 只保留 CDN 流量包（`cdnflowbag`）条目由票据 05 的端点负责过滤；本对象只表达一个包的事实，
 * 分组、求和与「未生效」标注都是纯函数（票据 05），不在数据类里做。
 *
 * @property instanceId 资源包实例 id，用于稳定 key 与去重。
 * @property commodityCode 商品码；CDN 流量包为 `cdnflowbag`。
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
        /** CDN 流量包的商品码（spec「资源包余量」）；全站加速、OSS 等其他商品码一律排除。 */
        const val CDN_FLOW_PACKAGE_COMMODITY_CODE: String = "cdnflowbag"

        /** 接口没有返回区域时的兜底文案：宁可显示未知，也不把不同区域混成一组。 */
        const val UNKNOWN_REGION: String = "未知区域"
    }
}
