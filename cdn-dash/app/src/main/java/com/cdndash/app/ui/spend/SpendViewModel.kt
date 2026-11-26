package com.cdndash.app.ui.spend

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cdndash.app.data.AccountStore
import com.cdndash.app.data.aliyun.AliyunApi
import com.cdndash.app.data.aliyun.ApiFailureKind
import com.cdndash.app.data.aliyun.ApiResult
import com.cdndash.app.data.aliyun.MonthlyBillSnapshotCodec
import com.cdndash.app.data.aliyun.SnapshotStore
import com.cdndash.app.data.aliyun.fold
import com.cdndash.app.domain.Account
import com.cdndash.app.domain.MonthlyBill
import com.cdndash.app.domain.SnapshotModule
import com.cdndash.app.domain.isBillCacheUsable
import com.cdndash.app.domain.recentBillingMonths
import com.cdndash.app.domain.visibleAccounts
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * 花费页状态。只有启用账号出现在切换列表（与看板一致）；柱状图与全年合计只来自当前选中的账号。
 *
 * @property bills 当前账号的 12 个账期（最旧在前），来自本次请求或 24 小时内的快照。
 * @property failure 归一后的错误；失败时 [bills] 保留上次成功数据（票据 07 的快照语义）。
 * @property fetchedAtMillis 当前 [bills] 的抓取时间，用于「数据时间」提示。
 */
data class SpendUiState(
    val accounts: List<Account> = emptyList(),
    val selectedAccountId: String? = null,
    val bills: List<MonthlyBill> = emptyList(),
    val failure: ApiFailureKind? = null,
    val isRefreshing: Boolean = false,
    val fetchedAtMillis: Long? = null,
    val hasAnyAccount: Boolean = false,
)

/**
 * 花费页 ViewModel：按需加载 + 24 小时快照缓存（ADR 0017、User Story 32）。
 *
 * - 只有进入花费页（实例化本类）才可能请求网络，不参与首屏请求路径；
 * - 选中账号先读 [SnapshotModule.MONTHLY_BILL] 快照（经 [SnapshotStore]，与看板同一套读写），
 *   账期窗口未变且 24 小时内直接展示，不重复请求；过期、跨月或手动刷新才重取；
 * - 账号间并发受 [MAX_CONCURRENT_ACCOUNT_BILL_REQUESTS] 限制，账号内由
 *   `BillEndpoint` 逐月串行（每账号 12 次），避免多账号同时打满配额；
 * - 单账号失败只写回该账号状态，切换其他账号照常展示（User Story 28）。
 */
class SpendViewModel(
    private val accountStore: AccountStore,
    private val aliyunApi: AliyunApi,
    private val now: () -> Instant = { Instant.now() },
) : ViewModel() {

    private data class AccountBills(
        val bills: List<MonthlyBill> = emptyList(),
        val failure: ApiFailureKind? = null,
        val isRefreshing: Boolean = false,
        val fetchedAtMillis: Long? = null,
    )

    private val selectedAccountId = MutableStateFlow<String?>(null)
    private val accountStates = MutableStateFlow<Map<String, AccountBills>>(emptyMap())
    private val fetchJobs = mutableMapOf<String, Job>()
    private val accountConcurrency = Semaphore(MAX_CONCURRENT_ACCOUNT_BILL_REQUESTS)
    private val snapshots = SnapshotStore(accountStore)

    val uiState: StateFlow<SpendUiState> =
        combine(accountStore.accounts, selectedAccountId, accountStates) { all, selectedId, states ->
            val visible = visibleAccounts(all)
            val current = selectedId?.let { states[it] } ?: AccountBills()
            SpendUiState(
                accounts = visible,
                selectedAccountId = selectedId,
                bills = current.bills,
                failure = current.failure,
                isRefreshing = current.isRefreshing,
                fetchedAtMillis = current.fetchedAtMillis,
                hasAnyAccount = all.isNotEmpty(),
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SpendUiState(),
        )

    init {
        // 选中第一个启用账号；账号列表变化时保证选中项仍然有效。
        viewModelScope.launch {
            accountStore.accounts.collect { all ->
                val visible = visibleAccounts(all)
                if (visible.none { it.id == selectedAccountId.value }) {
                    selectedAccountId.value = visible.firstOrNull()?.id
                }
                val selected = visible.firstOrNull { it.id == selectedAccountId.value }
                if (selected != null) loadIfNeeded(selected)
            }
        }
    }

    /** 切换账号：只加载选中账号（命中快照则零请求）。 */
    fun selectAccount(accountId: String) {
        selectedAccountId.value = accountId
        viewModelScope.launch {
            val account = visibleAccounts(accountStore.accounts.first())
                .firstOrNull { it.id == accountId } ?: return@launch
            loadIfNeeded(account)
        }
    }

    /** 手动刷新：跳过快照新鲜度判断，强制重取当前账号。 */
    fun refresh() {
        val accountId = selectedAccountId.value ?: return
        viewModelScope.launch {
            val account = visibleAccounts(accountStore.accounts.first())
                .firstOrNull { it.id == accountId } ?: return@launch
            fetch(account)
        }
    }

    /**
     * 已加载过的账号不再自动请求：命中快照或已成功取数后状态已存在；
     * 失败也保留状态（显示错误 + 上次数据），由使用者点「刷新」重试。
     */
    private suspend fun loadIfNeeded(account: Account) {
        if (accountStates.value.containsKey(account.id)) return
        if (fetchJobs[account.id]?.isActive == true) return

        val expectedMonths = recentBillingMonths(now())
        val cachedSnapshot = snapshots.load(
            accountId = account.id,
            module = SnapshotModule.MONTHLY_BILL,
            decode = MonthlyBillSnapshotCodec::decode,
        )
        val cachedBills = cachedSnapshot?.value
        val cachedFetchedAt = cachedSnapshot?.fetchedAtMillis
        if (cachedBills != null && isBillCacheUsable(
                cachedMonths = cachedBills.map { it.month },
                expectedMonths = expectedMonths,
                fetchedAtMillis = cachedFetchedAt,
                nowMillis = now().toEpochMilli(),
            )
        ) {
            updateState(account.id) {
                it.copy(bills = cachedBills, failure = null, fetchedAtMillis = cachedFetchedAt)
            }
            return
        }
        // 快照过期时先把旧数据摆出来（若可解码），再在后台重取；失败也不清空。
        if (cachedBills != null) {
            updateState(account.id) {
                it.copy(bills = cachedBills, fetchedAtMillis = cachedFetchedAt)
            }
        }
        fetch(account)
    }

    private fun fetch(account: Account) {
        if (fetchJobs[account.id]?.isActive == true) return
        fetchJobs[account.id] = viewModelScope.launch {
            updateState(account.id) { it.copy(isRefreshing = true) }
            val requestedAt = now()
            val result: ApiResult<List<MonthlyBill>> = try {
                val credentials = accountStore.credentials(account.id)
                if (credentials == null) {
                    // 解密失败或账号被删：按凭证失效降级，不影响其他账号。
                    ApiResult.Failure(ApiFailureKind.CREDENTIAL_INVALID)
                } else {
                    accountConcurrency.withPermit {
                        aliyunApi.monthlyBills(credentials, recentBillingMonths(requestedAt))
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                ApiResult.Failure(ApiFailureKind.NETWORK)
            }

            result.fold(
                onSuccess = { bills ->
                    val fetchedAtMillis = now().toEpochMilli()
                    // 快照读写统一走 SnapshotStore（写盘失败不影响本次展示）。
                    snapshots.save(
                        accountId = account.id,
                        module = SnapshotModule.MONTHLY_BILL,
                        payloadJson = MonthlyBillSnapshotCodec.encode(bills),
                        fetchedAtMillis = fetchedAtMillis,
                    )
                    updateState(account.id) {
                        it.copy(
                            bills = bills,
                            failure = null,
                            isRefreshing = false,
                            fetchedAtMillis = fetchedAtMillis,
                        )
                    }
                },
                onFailure = { failure ->
                    // 失败不清除旧账单与旧抓取时间，界面据此显示「数据时间」。
                    updateState(account.id) { it.copy(failure = failure.kind, isRefreshing = false) }
                },
            )
        }
    }

    private fun updateState(accountId: String, transform: (AccountBills) -> AccountBills) {
        accountStates.update { current ->
            current + (accountId to transform(current[accountId] ?: AccountBills()))
        }
    }

    companion object {
        /**
         * 账号间并发上限：单个账号已串行 12 次，两个账号同时取数已足够，
         * 账号再多也不会瞬间打满 BSS 配额（spec「刷新编排」）。
         */
        const val MAX_CONCURRENT_ACCOUNT_BILL_REQUESTS: Int = 2

        fun factory(accountStore: AccountStore, aliyunApi: AliyunApi): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { SpendViewModel(accountStore, aliyunApi) }
            }
    }
}
