package com.cdndash.app.data.aliyun

import com.cdndash.app.data.AccountStore
import com.cdndash.app.domain.SnapshotModule
import com.cdndash.app.domain.TodayTraffic

/**
 * 带抓取时间的快照值：领域结果 + 该结果的抓取时刻（epoch millis）。
 *
 * 展示离线数据时只用 [fetchedAtMillis] 标「数据时间 HH:mm（离线）」，
 * 不能用「现在」冒充抓取时间，否则用户会以为刚取到的是新数。
 */
data class SnapshotValue<T>(
    val value: T,
    val fetchedAtMillis: Long,
)

/**
 * 「账号 + 模块 → 快照」的读写收口（票据 07）。
 *
 * 持久化仍走唯一持久化接缝 [AccountStore]（spec「唯一持久化接缝 AccountStore」），
 * 这里只做三件事：按模块选择编解码、把抓取时间与领域值一起返回、吞掉读写异常。
 *
 * 约定：
 * - **只在成功取数后写**。失败分支永远不调用 [save]/[saveToday]，因此旧快照不会被清掉；
 *   读取失败或载荷损坏只返回 null，也不删除任何东西。
 * - 读失败/损坏返回 null 而不是抛出，调用方据此展示错误态。
 * - 各模块互相独立：[SnapshotModule] 决定键，一个模块取数失败不影响其他模块的快照。
 *
 * 后续票据（7 天走势、资源包、账单）复用 [load] + 各自的编解码函数即可，不必再加接口。
 */
class SnapshotStore(private val accountStore: AccountStore) {

    /**
     * 保存某模块的领域结果 JSON 与抓取时间；写盘异常返回 false。
     * 写快照失败只影响「下次离线能否看到旧数据」，不得影响本次展示。
     */
    suspend fun save(
        accountId: String,
        module: SnapshotModule,
        payloadJson: String,
        fetchedAtMillis: Long,
    ): Boolean = runCatching {
        accountStore.saveSnapshot(
            accountId = accountId,
            module = module,
            payloadJson = payloadJson,
            fetchedAtMillis = fetchedAtMillis,
        )
    }.isSuccess

    /**
     * 读取某模块的快照并解码为领域值；没有快照、读取失败或载荷损坏时返回 null。
     * [decode] 由调用方按模块传入（如 `TodayTrafficSnapshotCodec::decode`）。
     */
    suspend fun <T> load(
        accountId: String,
        module: SnapshotModule,
        decode: (String) -> T?,
    ): SnapshotValue<T>? {
        val snapshot = runCatching { accountStore.snapshot(accountId, module) }.getOrNull()
            ?: return null
        val value = decode(snapshot.payloadJson) ?: return null
        return SnapshotValue(value = value, fetchedAtMillis = snapshot.fetchedAtMillis)
    }

    /** 今日累计快照的写入；成功取数后调用，失败路径不要调用。 */
    suspend fun saveToday(
        accountId: String,
        today: TodayTraffic,
        fetchedAtMillis: Long,
    ): Boolean = save(
        accountId = accountId,
        module = SnapshotModule.TODAY_TOTAL,
        payloadJson = TodayTrafficSnapshotCodec.encode(today),
        fetchedAtMillis = fetchedAtMillis,
    )

    /** 今日累计快照的读取；损坏或不存在时返回 null，由调用方展示错误态。 */
    suspend fun loadToday(accountId: String): SnapshotValue<TodayTraffic>? =
        load(accountId, SnapshotModule.TODAY_TOTAL, TodayTrafficSnapshotCodec::decode)
}
