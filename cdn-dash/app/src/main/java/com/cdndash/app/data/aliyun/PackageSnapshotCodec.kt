package com.cdndash.app.data.aliyun

import com.cdndash.app.domain.ResourcePackage
import java.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 资源包快照的 JSON 编解码：写 AccountStore 的 [com.cdndash.app.domain.SnapshotModule.PACKAGES]
 * 模块时使用（票据 05）。
 *
 * 存的是接口返回的原始包事实（[ResourcePackage]）而不是已经分好组的视图：
 * 「未生效」的判定依赖当前时刻，余量视图应在展示时用当时的 `now` 重新计算
 * （看板与明细页失败回落时都走 [decode] + `groupPackages`）。
 */
object PackageSnapshotCodec {

    private val json = Json { ignoreUnknownKeys = true }

    fun encode(packages: List<ResourcePackage>): String =
        json.encodeToString(
            Payload.serializer(),
            Payload(
                packages = packages.map { packageInfo ->
                    Item(
                        instanceId = packageInfo.instanceId,
                        commodityCode = packageInfo.commodityCode,
                        region = packageInfo.region,
                        totalBytes = packageInfo.totalBytes,
                        remainingBytes = packageInfo.remainingBytes,
                        effectiveAt = packageInfo.effectiveAt.toString(),
                        expiresAt = packageInfo.expiresAt.toString(),
                    )
                },
            ),
        )

    /** 损坏的载荷返回 null 而不是崩溃；调用方按「没有快照」处理。 */
    fun decode(raw: String): List<ResourcePackage>? = runCatching {
        json.decodeFromString(Payload.serializer(), raw).packages.map { item ->
            ResourcePackage(
                instanceId = item.instanceId,
                commodityCode = item.commodityCode,
                region = item.region,
                totalBytes = item.totalBytes,
                remainingBytes = item.remainingBytes,
                effectiveAt = Instant.parse(item.effectiveAt),
                expiresAt = Instant.parse(item.expiresAt),
            )
        }
    }.getOrNull()

    @Serializable
    private data class Payload(val packages: List<Item>)

    @Serializable
    private data class Item(
        val instanceId: String,
        val commodityCode: String,
        val region: String,
        val totalBytes: Long,
        val remainingBytes: Long,
        val effectiveAt: String,
        val expiresAt: String,
    )
}
