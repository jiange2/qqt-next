package com.cdndash.app.data

import com.cdndash.app.domain.Account
import com.cdndash.app.domain.AccountCredentials
import com.cdndash.app.domain.AccountSnapshot
import com.cdndash.app.domain.SnapshotModule
import kotlinx.coroutines.flow.Flow

/**
 * 唯一持久化接缝（spec「唯一持久化接缝 AccountStore」）。
 *
 * 职责：账号元数据（名称、启用状态、手动顺序）、加密后的凭据、各模块快照缓存，
 * 以及设置页的刷新间隔偏好。密钥的加解密藏在实现内部：调用方新增时只传明文，
 * 读取时只拿回 [AccountCredentials]，永远不会接触密文、IV 或 Keystore 细节。
 *
 * [accounts] 按 [Account.sortOrder] 升序发出；删除账号必须同时删除该账号的凭据密文
 * 与全部模块快照，不能只删列表条目。
 */
interface AccountStore {

    /** 账号元数据（不含明文凭据），按手动顺序升序。 */
    val accounts: Flow<List<Account>>

    /** 设置页的刷新间隔（分钟），默认 [DEFAULT_REFRESH_INTERVAL_MINUTES]。 */
    val refreshIntervalMinutes: Flow<Int>

    /** 按 id 解密并返回明文凭据；账号不存在或解密失败返回 null。 */
    suspend fun credentials(accountId: String): AccountCredentials?

    /** 新增账号：入参是明文，实现负责加密落盘；新账号默认启用并排在末尾。 */
    suspend fun addAccount(name: String, accessKeyId: String, accessKeySecret: String): Account

    /** 删除账号及其凭据密文、全部模块快照。 */
    suspend fun deleteAccount(accountId: String)

    /** 启用/停用账号；停用只影响看板过滤，不删配置。 */
    suspend fun setEnabled(accountId: String, enabled: Boolean)

    /** 按传入的 id 顺序整体重排；未出现的既有账号保持相对顺序追加在末尾。 */
    suspend fun reorderAccounts(orderedIds: List<String>)

    /** 保存某账号某模块的领域结果 JSON 与抓取时间（epoch millis）。 */
    suspend fun saveSnapshot(
        accountId: String,
        module: SnapshotModule,
        payloadJson: String,
        fetchedAtMillis: Long,
    )

    /** 读取某账号某模块的快照；没有成功记录时返回 null。 */
    suspend fun snapshot(accountId: String, module: SnapshotModule): AccountSnapshot?

    /** 清除某账号的全部模块快照（删除账号时也会调用）。 */
    suspend fun clearSnapshots(accountId: String)

    /** 保存刷新间隔；只会由设置页传入允许的选项。 */
    suspend fun setRefreshIntervalMinutes(minutes: Int)

    companion object {
        const val DEFAULT_REFRESH_INTERVAL_MINUTES: Int = 5
        val REFRESH_INTERVAL_OPTIONS_MINUTES: List<Int> = listOf(5, 10, 15, 30)
    }
}
