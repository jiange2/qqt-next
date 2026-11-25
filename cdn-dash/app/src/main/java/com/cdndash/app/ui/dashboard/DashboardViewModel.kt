package com.cdndash.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cdndash.app.data.AccountStore
import com.cdndash.app.data.aliyun.AliyunApi
import com.cdndash.app.data.aliyun.ApiFailureKind
import com.cdndash.app.data.aliyun.ApiResult
import com.cdndash.app.data.aliyun.SnapshotStore
import com.cdndash.app.data.aliyun.fold
import com.cdndash.app.domain.Account
import com.cdndash.app.domain.TodayTraffic
import com.cdndash.app.domain.visibleAccounts
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** 单账号的今日累计状态：成功数据、归一后的错误、是否正在取数三者互不覆盖。 */
data class AccountTodayState(
    val account: Account,
    val today: TodayTraffic? = null,
    val failure: ApiFailureKind? = null,
    val isRefreshing: Boolean = false,
    /**
     * 非 null 表示 [today] 来自上次成功快照（离线展示），值是快照的抓取时刻（epoch millis）；
     * 卡片据此标注「数据时间 HH:mm（离线）」。本次实时取数成功时为 null。
     */
    val snapshotFetchedAtMillis: Long? = null,
)

/** 看板页状态；顺序完全来自账号手动排序（User Story 7）。 */
data class DashboardUiState(
    val accounts: List<AccountTodayState> = emptyList(),
    val hasAnyAccount: Boolean = false,
    val isRefreshing: Boolean = false,
)

/**
 * 看板 ViewModel：刷新编排 + 今日累计取数。
 *
 * 刷新编排（spec「刷新编排」、User Story 29/30）：
 * - 打开/回到前台（`ON_START`）→ 立即刷新一次，并启动前台定时器；
 * - 下拉 → [refresh]；
 * - 前台定时器每 N 分钟刷新，N 读 [AccountStore.refreshIntervalMinutes]（默认 5，每轮重读）；
 * - 退到后台（`ON_STOP`，即离开 `Lifecycle.State.STARTED`）→ 取消定时器，不再请求。
 *
 * 错误隔离（User Story 26）：账号间用 `supervisorScope` 并发，单账号的失败只写回该账号；
 * 账号内串行（先读凭据再请求），账号间并发数有上限，避免账号多时打满接口配额。
 *
 * 快照与离线（User Stories 31/34，票据 07）：成功取数经 [SnapshotStore] 写入今日快照（带抓取时间）；
 * 失败时保留已展示的数据，没有则回落到上次快照并把 `snapshotFetchedAtMillis` 交给卡片标「数据时间」，
 * 两者都没有才展示错误态。失败路径不写、不清任何快照。
 */
class DashboardViewModel(
    private val accountStore: AccountStore,
    private val aliyunApi: AliyunApi,
    private val now: () -> Instant = { Instant.now() },
) : ViewModel() {

    private data class AccountFetch(
        val today: TodayTraffic? = null,
        val failure: ApiFailureKind? = null,
        val isRefreshing: Boolean = false,
        val snapshotFetchedAtMillis: Long? = null,
    )

    private val fetches = MutableStateFlow<Map<String, AccountFetch>>(emptyMap())
    private val refreshing = MutableStateFlow(false)
    private val accountConcurrency = Semaphore(MAX_CONCURRENT_ACCOUNT_REQUESTS)
    private val snapshots = SnapshotStore(accountStore)

    val uiState: StateFlow<DashboardUiState> =
        combine(accountStore.accounts, fetches, refreshing) { allAccounts, fetchMap, isRefreshing ->
            DashboardUiState(
                accounts = visibleAccounts(allAccounts).map { account ->
                    val fetch = fetchMap[account.id] ?: AccountFetch()
                    AccountTodayState(
                        account = account,
                        today = fetch.today,
                        failure = fetch.failure,
                        isRefreshing = fetch.isRefreshing,
                        snapshotFetchedAtMillis = fetch.snapshotFetchedAtMillis,
                    )
                },
                hasAnyAccount = allAccounts.isNotEmpty(),
                isRefreshing = isRefreshing,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DashboardUiState(),
        )

    private var refreshJob: Job? = null
    private var autoRefreshJob: Job? = null

    /** 下拉/打开触发的一次全量刷新；进行中时忽略重复触发。 */
    fun refresh() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            refreshing.value = true
            try {
                val accounts = visibleAccounts(accountStore.accounts.first())
                supervisorScope {
                    accounts.forEach { account ->
                        launch {
                            // 账号间有上限；单账号异常在 refreshAccount 内消化。
                            accountConcurrency.withPermit { refreshAccount(account) }
                        }
                    }
                }
            } finally {
                refreshing.value = false
            }
        }
    }

    /** 页面进入前台（`ON_START`）：先刷新一次，再按设置的间隔循环刷新。 */
    fun onForegroundStart() {
        if (autoRefreshJob?.isActive == true) return
        autoRefreshJob = viewModelScope.launch {
            refresh()
            while (isActive) {
                val intervalMinutes = accountStore.refreshIntervalMinutes.first()
                    .coerceAtLeast(MIN_REFRESH_INTERVAL_MINUTES)
                delay(intervalMinutes * MILLIS_PER_MINUTE)
                refresh()
            }
        }
    }

    /** 页面离开前台（`ON_STOP`）：停掉定时器；已在途的请求由 viewModelScope 自然结束。 */
    fun onForegroundStop() {
        autoRefreshJob?.cancel()
        autoRefreshJob = null
    }

    private suspend fun refreshAccount(account: Account) {
        updateFetch(account.id) { it.copy(isRefreshing = true) }
        // 先亮出上次成功快照：弱网/断网时卡片不会从空白开始，后面失败分支再兜一次。
        showSnapshotIfNothingToShow(account.id)
        val requestedAt = now()
        val result: ApiResult<TodayTraffic> = try {
            val credentials = accountStore.credentials(account.id)
            if (credentials == null) {
                // 解密失败或账号被删：按凭证失效降级，不影响其他账号。
                ApiResult.Failure(ApiFailureKind.CREDENTIAL_INVALID)
            } else {
                aliyunApi.todayTraffic(credentials, requestedAt)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            ApiResult.Failure(ApiFailureKind.NETWORK)
        }

        result.fold(
            onSuccess = { today ->
                // 成功才覆盖快照；写盘失败不影响本次展示（下次离线会旧一些）。
                snapshots.saveToday(
                    accountId = account.id,
                    today = today,
                    fetchedAtMillis = now().toEpochMilli(),
                )
                updateFetch(account.id) {
                    it.copy(
                        today = today,
                        failure = null,
                        isRefreshing = false,
                        snapshotFetchedAtMillis = null,
                    )
                }
            },
            onFailure = { failure ->
                // 失败绝不清除旧数据与旧快照：优先保留本次已展示的数据，
                // 没有则回落到快照，两者都没有才是纯错误态（票据 07）。
                val cached = snapshots.loadToday(account.id)
                updateFetch(account.id) { current ->
                    val shown = current.today ?: cached?.value
                    current.copy(
                        today = shown,
                        snapshotFetchedAtMillis = if (shown != null) {
                            cached?.fetchedAtMillis ?: current.snapshotFetchedAtMillis
                        } else {
                            null
                        },
                        failure = failure.kind,
                        isRefreshing = false,
                    )
                }
            },
        )
    }

    /** 尚无任何数据可展示时（首次进入/切换账号），先读一次快照顶上，避免空白。 */
    private suspend fun showSnapshotIfNothingToShow(accountId: String) {
        if (fetches.value[accountId]?.today != null) return
        val cached = snapshots.loadToday(accountId) ?: return
        updateFetch(accountId) {
            it.copy(today = cached.value, snapshotFetchedAtMillis = cached.fetchedAtMillis)
        }
    }

    private fun updateFetch(accountId: String, transform: (AccountFetch) -> AccountFetch) {
        fetches.update { current ->
            current + (accountId to transform(current[accountId] ?: AccountFetch()))
        }
    }

    companion object {
        /** 账号间并发上限：账号多时避免同时打满接口配额。 */
        const val MAX_CONCURRENT_ACCOUNT_REQUESTS: Int = 4

        /** 设置页允许的最小间隔；异常值兜底为 1 分钟，避免 0 间隔热循环。 */
        const val MIN_REFRESH_INTERVAL_MINUTES: Int = 1

        private const val MILLIS_PER_MINUTE: Long = 60_000L

        fun factory(accountStore: AccountStore, aliyunApi: AliyunApi): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { DashboardViewModel(accountStore, aliyunApi) }
            }
    }
}
