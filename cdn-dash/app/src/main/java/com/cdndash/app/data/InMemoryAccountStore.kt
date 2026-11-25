package com.cdndash.app.data

import com.cdndash.app.domain.Account
import com.cdndash.app.domain.AccountCredentials
import com.cdndash.app.domain.AccountSnapshot
import com.cdndash.app.domain.SnapshotModule
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * AccountStore 的内存实现，供 JVM 测试与预览使用。
 *
 * 语义与生产实现一致：新增后按顺序可读回、删除同时清掉凭据与快照、重排持久在内存状态里。
 * 它没有 Keystore：密文相关行为（不可导出密钥、设备重启后解密）属于设备级验收，
 * 不放进 JVM 测试。
 */
class InMemoryAccountStore(
    private val idFactory: () -> String = { UUID.randomUUID().toString() },
) : AccountStore {

    private val lock = Any()
    private val accountState = MutableStateFlow<List<Account>>(emptyList())
    private val refreshIntervalState =
        MutableStateFlow(AccountStore.DEFAULT_REFRESH_INTERVAL_MINUTES)
    private val credentialsById = mutableMapOf<String, AccountCredentials>()
    private val snapshotsByAccountAndModule =
        mutableMapOf<Pair<String, SnapshotModule>, AccountSnapshot>()

    override val accounts: Flow<List<Account>> = accountState.asStateFlow()

    override val refreshIntervalMinutes: Flow<Int> = refreshIntervalState.asStateFlow()

    override suspend fun credentials(accountId: String): AccountCredentials? =
        synchronized(lock) { credentialsById[accountId] }

    override suspend fun addAccount(
        name: String,
        accessKeyId: String,
        accessKeySecret: String,
    ): Account = synchronized(lock) {
        val account = Account(
            id = idFactory(),
            name = name,
            enabled = true,
            sortOrder = (accountState.value.maxOfOrNull { it.sortOrder } ?: -1) + 1,
        )
        accountState.value = (accountState.value + account).sortedBy { it.sortOrder }
        credentialsById[account.id] = AccountCredentials(accessKeyId, accessKeySecret)
        account
    }

    override suspend fun deleteAccount(accountId: String) {
        synchronized(lock) {
            accountState.value = accountState.value.filterNot { it.id == accountId }
            credentialsById.remove(accountId)
            SnapshotModule.entries.forEach { module ->
                snapshotsByAccountAndModule.remove(accountId to module)
            }
        }
    }

    override suspend fun setEnabled(accountId: String, enabled: Boolean) {
        synchronized(lock) {
            accountState.value = accountState.value
                .map { if (it.id == accountId) it.copy(enabled = enabled) else it }
                .sortedBy { it.sortOrder }
        }
    }

    override suspend fun reorderAccounts(orderedIds: List<String>) {
        synchronized(lock) {
            val current = accountState.value
            val byId = current.associateBy { it.id }
            val requested = orderedIds.distinct().mapNotNull { byId[it] }
            val remaining = current.filterNot { it.id in orderedIds }
            accountState.value = (requested + remaining)
                .mapIndexed { index, account -> account.copy(sortOrder = index) }
                .sortedBy { it.sortOrder }
        }
    }

    override suspend fun saveSnapshot(
        accountId: String,
        module: SnapshotModule,
        payloadJson: String,
        fetchedAtMillis: Long,
    ) {
        synchronized(lock) {
            snapshotsByAccountAndModule[accountId to module] = AccountSnapshot(
                accountId = accountId,
                module = module,
                payloadJson = payloadJson,
                fetchedAtMillis = fetchedAtMillis,
            )
        }
    }

    override suspend fun snapshot(accountId: String, module: SnapshotModule): AccountSnapshot? =
        synchronized(lock) { snapshotsByAccountAndModule[accountId to module] }

    override suspend fun clearSnapshots(accountId: String) {
        synchronized(lock) {
            SnapshotModule.entries.forEach { module ->
                snapshotsByAccountAndModule.remove(accountId to module)
            }
        }
    }

    override suspend fun setRefreshIntervalMinutes(minutes: Int) {
        refreshIntervalState.value = minutes
    }
}
