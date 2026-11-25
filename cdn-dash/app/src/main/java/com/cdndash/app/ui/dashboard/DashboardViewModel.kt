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
import com.cdndash.app.data.aliyun.PackageSnapshotCodec
import com.cdndash.app.data.aliyun.TodayTrafficSnapshotCodec
import com.cdndash.app.data.aliyun.WeekTrafficSnapshotCodec
import com.cdndash.app.data.aliyun.fold
import com.cdndash.app.domain.Account
import com.cdndash.app.domain.AccountCredentials
import com.cdndash.app.domain.DayTrafficPoint
import com.cdndash.app.domain.PackageGroup
import com.cdndash.app.domain.ResourcePackage
import com.cdndash.app.domain.SnapshotModule
import com.cdndash.app.domain.TodayTraffic
import com.cdndash.app.domain.TopSummary
import com.cdndash.app.domain.TopSummaryAccount
import com.cdndash.app.domain.groupPackages
import com.cdndash.app.domain.summarizeTop
import com.cdndash.app.domain.visibleAccounts
import com.cdndash.app.domain.weekTotal
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

/**
 * 单账号状态：今日累计、已结算 7 天、资源包余量、归一后的错误、是否正在取数互不覆盖。
 *
 * @property week 最近 7 个已结算日（不含今天）的日点，恒 7 个、按日升序；尚未取到或从未成功时为 null。
 * @property packageGroups 资源包余量视图；null 表示本次还没取到（区别于「取到了但一个包都没有」的空列表）。
 * @property packageFailure 资源包模块的失败原因；与今日/7 天共用的 [failure] 分开，互不覆盖。
 */
data class AccountTodayState(
    val account: Account,
    val today: TodayTraffic? = null,
    val week: List<DayTrafficPoint>? = null,
    val failure: ApiFailureKind? = null,
    val packageGroups: List<PackageGroup>? = null,
    val packageFailure: ApiFailureKind? = null,
    val isRefreshing: Boolean = false,
)

/**
 * 看板页状态；顺序完全来自账号手动排序（User Story 7）。
 *
 * @property topSummary 顶部两数（票据 06）：按全部账号计算，停用与失败账号的取舍在
 * [summarizeTop] 里完成，不靠 [accounts] 已过滤启用账号这一层。
 */
data class DashboardUiState(
    val accounts: List<AccountTodayState> = emptyList(),
    val hasAnyAccount: Boolean = false,
    val isRefreshing: Boolean = false,
    val topSummary: TopSummary = TopSummary(),
)

/**
 * 看板 ViewModel：刷新编排 + 今日累计、已结算 7 天与资源包取数。
 *
 * 刷新编排（spec「刷新编排」、User Story 29/30）：
 * - 打开/回到前台（`ON_START`）→ 立即刷新一次，并启动前台定时器；
 * - 下拉 → [refresh]；
 * - 前台定时器每 N 分钟刷新，N 读 [AccountStore.refreshIntervalMinutes]（默认 5，每轮重读）；
 * - 退到后台（`ON_STOP`，即离开 `Lifecycle.State.STARTED`）→ 取消定时器，不再请求。
 *
 * 错误隔离（User Story 26）：账号间用 `supervisorScope` 并发，单账号的失败只写回该账号；
 * 账号内串行（今日累计 → 已结算 7 天 → 资源包），账号间并发数有上限，避免账号多时打满接口配额。
 * 成功取数分别写入 [SnapshotModule.TODAY_TOTAL]、[SnapshotModule.WEEK_TREND] 与
 * [SnapshotModule.PACKAGES] 快照（带抓取时间）；展示快照属于票据 07。
 */
class DashboardViewModel(
    private val accountStore: AccountStore,
    private val aliyunApi: AliyunApi,
    private val now: () -> Instant = { Instant.now() },
) : ViewModel() {

    private data class AccountFetch(
        val today: TodayTraffic? = null,
        val week: List<DayTrafficPoint>? = null,
        val failure: ApiFailureKind? = null,
        val packageGroups: List<PackageGroup>? = null,
        val packageFailure: ApiFailureKind? = null,
        val isRefreshing: Boolean = false,
    )

    private val fetches = MutableStateFlow<Map<String, AccountFetch>>(emptyMap())
    private val refreshing = MutableStateFlow(false)
    private val accountConcurrency = Semaphore(MAX_CONCURRENT_ACCOUNT_REQUESTS)

    val uiState: StateFlow<DashboardUiState> =
        combine(accountStore.accounts, fetches, refreshing) { allAccounts, fetchMap, isRefreshing ->
            DashboardUiState(
                accounts = visibleAccounts(allAccounts).map { account ->
                    val fetch = fetchMap[account.id] ?: AccountFetch()
                    AccountTodayState(
                        account = account,
                        today = fetch.today,
                        week = fetch.week,
                        failure = fetch.failure,
                        packageGroups = fetch.packageGroups,
                        packageFailure = fetch.packageFailure,
                        isRefreshing = fetch.isRefreshing,
                    )
                },
                hasAnyAccount = allAccounts.isNotEmpty(),
                isRefreshing = isRefreshing,
                // 顶部汇总走全部账号（含停用），由纯函数排除停用/失败并给出缺失数。
                topSummary = summarizeTop(
                    allAccounts.map { account ->
                        val fetch = fetchMap[account.id] ?: AccountFetch()
                        TopSummaryAccount(
                            enabled = account.enabled,
                            todayBytes = fetch.today?.bytes,
                            weekBytes = fetch.week?.let(::weekTotal),
                            failed = fetch.failure != null,
                        )
                    },
                ),
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
        val requestedAt = now()
        val credentials = try {
            accountStore.credentials(account.id)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            null
        }
        if (credentials == null) {
            // 解密失败或账号被删：按凭证失效降级，不影响其他账号；三个模块一起标记。
            updateFetch(account.id) {
                it.copy(
                    failure = ApiFailureKind.CREDENTIAL_INVALID,
                    packageFailure = ApiFailureKind.CREDENTIAL_INVALID,
                    isRefreshing = false,
                )
            }
            return
        }
        // 账号内串行：今日累计 → 已结算 7 天 → 资源包，避免单个账号同时打多条接口。
        refreshToday(account, credentials, requestedAt)
        refreshWeek(account, credentials, requestedAt)
        // 资源包单独落状态：包失败不覆盖今日/7 天，反之亦然（User Story 26）；刷新收尾也在这里。
        refreshPackages(account, credentials, requestedAt)
    }

    private suspend fun refreshToday(
        account: Account,
        credentials: AccountCredentials,
        requestedAt: Instant,
    ) {
        val result: ApiResult<TodayTraffic> = try {
            aliyunApi.todayTraffic(credentials, requestedAt)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            ApiResult.Failure(ApiFailureKind.NETWORK)
        }

        result.fold(
            onSuccess = { today ->
                runCatching {
                    accountStore.saveSnapshot(
                        accountId = account.id,
                        module = SnapshotModule.TODAY_TOTAL,
                        payloadJson = TodayTrafficSnapshotCodec.encode(today),
                        fetchedAtMillis = now().toEpochMilli(),
                    )
                }
                updateFetch(account.id) {
                    it.copy(today = today, failure = null)
                }
            },
            onFailure = { failure ->
                // 失败不清除旧数据（票据 07 的「失败保留旧值」从这里起步）。
                updateFetch(account.id) {
                    it.copy(failure = failure.kind)
                }
            },
        )
    }

    /**
     * 已结算 7 天（票据 04）：成功后写 `WEEK_TREND` 快照（展示旧快照属票据 07）。
     * 失败只记录错误、保留上次成功的 7 天数据，不抹掉已经取回的今日累计。
     */
    private suspend fun refreshWeek(
        account: Account,
        credentials: AccountCredentials,
        requestedAt: Instant,
    ) {
        val result: ApiResult<List<DayTrafficPoint>> = try {
            aliyunApi.settledDays(credentials, requestedAt)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            ApiResult.Failure(ApiFailureKind.NETWORK)
        }

        result.fold(
            onSuccess = { days ->
                runCatching {
                    accountStore.saveSnapshot(
                        accountId = account.id,
                        module = SnapshotModule.WEEK_TREND,
                        payloadJson = WeekTrafficSnapshotCodec.encode(days),
                        fetchedAtMillis = now().toEpochMilli(),
                    )
                }
                updateFetch(account.id) {
                    it.copy(week = days)
                }
            },
            onFailure = { failure ->
                updateFetch(account.id) {
                    // 不覆盖今日累计已有的更具体错误；两轮都失败时也会在 7 天这步先落一次。
                    it.copy(failure = it.failure ?: failure.kind)
                }
            },
        )
    }

    /**
     * 取资源包并写成余量视图（票据 05）；成功写 [SnapshotModule.PACKAGES] 快照（展示旧快照属票据 07）。
     * 分组用本次请求时刻判定「未生效」，因此每次刷新都会重新分组；账号级 `isRefreshing` 在这里收尾。
     */
    private suspend fun refreshPackages(
        account: Account,
        credentials: AccountCredentials,
        requestedAt: Instant,
    ) {
        val result: ApiResult<List<ResourcePackage>> = try {
            aliyunApi.resourcePackages(credentials, requestedAt)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            ApiResult.Failure(ApiFailureKind.NETWORK)
        }

        result.fold(
            onSuccess = { packages ->
                runCatching {
                    accountStore.saveSnapshot(
                        accountId = account.id,
                        module = SnapshotModule.PACKAGES,
                        payloadJson = PackageSnapshotCodec.encode(packages),
                        fetchedAtMillis = now().toEpochMilli(),
                    )
                }
                val groups = groupPackages(packages, requestedAt)
                updateFetch(account.id) {
                    it.copy(packageGroups = groups, packageFailure = null, isRefreshing = false)
                }
            },
            onFailure = { failure ->
                // 失败保留上次的余量视图，只标记本模块错误；账号级刷新结束由这里收尾。
                updateFetch(account.id) {
                    it.copy(packageFailure = failure.kind, isRefreshing = false)
                }
            },
        )
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
