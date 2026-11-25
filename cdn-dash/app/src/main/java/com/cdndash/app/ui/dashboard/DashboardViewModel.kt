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
import com.cdndash.app.data.aliyun.SnapshotStore
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
import com.cdndash.app.domain.isWeekRefreshDue
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
    /**
     * 非 null 表示 [today] 来自上次成功快照（离线展示），值是快照的抓取时刻（epoch millis）；
     * 卡片据此标注「数据时间 HH:mm（离线）」。本次实时取数成功时为 null。
     */
    val snapshotFetchedAtMillis: Long? = null,
    /** 同 [snapshotFetchedAtMillis]，对应 [week] 模块；7 天请求失败回落到快照时非 null。 */
    val weekSnapshotFetchedAtMillis: Long? = null,
    /** 同 [snapshotFetchedAtMillis]，对应 [packageGroups] 模块；资源包失败回落到快照时非 null。 */
    val packageSnapshotFetchedAtMillis: Long? = null,
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
 * 刷新编排（spec「刷新编排」「冷启动请求量」、User Story 29/30）：
 * - 打开/回到前台（`ON_START`）→ 立即全量刷新一次（今日 + 7 天 + 资源包），并启动前台定时器；
 * - 下拉 → [refresh]（全量）；
 * - 前台定时器每 N 分钟只取今日累计（N 读 [AccountStore.refreshIntervalMinutes]，默认 5、每轮重读）；
 *   7 天按 [isWeekRefreshDue] 每小时最多一次；资源包不进定时器，只在冷启动/下拉时取；
 * - 退到后台（`ON_STOP`，即离开 `Lifecycle.State.STARTED`）→ 取消定时器，不再请求。
 *
 * 错误隔离（User Story 26）：账号间用 `supervisorScope` 并发，单账号的失败只写回该账号；
 * 账号内串行（今日累计 → 已结算 7 天 → 资源包），账号间并发数有上限，避免账号多时打满接口配额。
 *
 * 快照与离线（User Stories 31/34，票据 07）：成功取数经 [SnapshotStore] 写今日/7 天/资源包快照（带抓取时间）；
 * 失败时保留已展示的数据，没有则回落到该模块上次快照，并把抓取时间交给卡片标「数据时间」；
 * 两者都没有才展示错误态。失败路径不写、不清任何快照。
 */
class DashboardViewModel(
    private val accountStore: AccountStore,
    private val aliyunApi: AliyunApi,
    private val now: () -> Instant = { Instant.now() },
) : ViewModel() {

    /**
     * 一次刷新的取数范围（spec「冷启动请求量」：冷启动每账号 2 次 + 1 次资源包）。
     */
    private enum class RefreshScope {
        /** 打开应用 / 下拉刷新：今日 + 7 天 + 资源包全部重取。 */
        FULL,

        /** 前台定时到点：只取今日；7 天按 1 小时节流，资源包不进定时。 */
        TIMED,
    }

    private data class AccountFetch(
        val today: TodayTraffic? = null,
        val week: List<DayTrafficPoint>? = null,
        val failure: ApiFailureKind? = null,
        val packageGroups: List<PackageGroup>? = null,
        val packageFailure: ApiFailureKind? = null,
        val isRefreshing: Boolean = false,
        val snapshotFetchedAtMillis: Long? = null,
        val weekSnapshotFetchedAtMillis: Long? = null,
        val packageSnapshotFetchedAtMillis: Long? = null,
    )

    private val fetches = MutableStateFlow<Map<String, AccountFetch>>(emptyMap())
    private val refreshing = MutableStateFlow(false)

    /** 各账号上次成功取到 7 天的时刻（epoch millis），供定时刷新做 1 小时节流。 */
    private val weekFetchedAtMillis = MutableStateFlow<Map<String, Long>>(emptyMap())
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
                        week = fetch.week,
                        failure = fetch.failure,
                        packageGroups = fetch.packageGroups,
                        packageFailure = fetch.packageFailure,
                        isRefreshing = fetch.isRefreshing,
                        snapshotFetchedAtMillis = fetch.snapshotFetchedAtMillis,
                        weekSnapshotFetchedAtMillis = fetch.weekSnapshotFetchedAtMillis,
                        packageSnapshotFetchedAtMillis = fetch.packageSnapshotFetchedAtMillis,
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

    /** 下拉/打开触发的一次全量刷新（今日 + 7 天 + 资源包）；进行中时忽略重复触发。 */
    fun refresh() {
        launchRefresh(RefreshScope.FULL)
    }

    /** 页面进入前台（`ON_START`）：先全量刷新一次，再按设置的间隔定时只更新今日。 */
    fun onForegroundStart() {
        if (autoRefreshJob?.isActive == true) return
        autoRefreshJob = viewModelScope.launch {
            refresh()
            while (isActive) {
                val intervalMinutes = accountStore.refreshIntervalMinutes.first()
                    .coerceAtLeast(MIN_REFRESH_INTERVAL_MINUTES)
                delay(intervalMinutes * MILLIS_PER_MINUTE)
                launchRefresh(RefreshScope.TIMED)
            }
        }
    }

    /** 页面离开前台（`ON_STOP`）：停掉定时器；已在途的请求由 viewModelScope 自然结束。 */
    fun onForegroundStop() {
        autoRefreshJob?.cancel()
        autoRefreshJob = null
    }

    private fun launchRefresh(scope: RefreshScope) {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            refreshing.value = true
            try {
                val requestedAt = now()
                val accounts = visibleAccounts(accountStore.accounts.first())
                supervisorScope {
                    accounts.forEach { account ->
                        launch {
                            // 账号间有上限；单账号异常在 refreshAccount 内消化。
                            accountConcurrency.withPermit { refreshAccount(account, requestedAt, scope) }
                        }
                    }
                }
            } finally {
                refreshing.value = false
            }
        }
    }

    private suspend fun refreshAccount(
        account: Account,
        requestedAt: Instant,
        scope: RefreshScope,
    ) {
        updateFetch(account.id) { it.copy(isRefreshing = true) }
        // 先亮出上次成功快照：弱网/断网时卡片不会从空白开始，后面失败分支再兜一次。
        showSnapshotIfNothingToShow(account.id, requestedAt)
        val credentials = try {
            accountStore.credentials(account.id)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            null
        }
        if (credentials == null) {
            // 解密失败或账号被删：按凭证失效降级，旧数据/快照照留，不影响其他账号。
            showTodayFailure(account.id, ApiFailureKind.CREDENTIAL_INVALID)
            // 没有后续步骤了：资源包模块同样标凭证失效，并由这里结束该账号本轮刷新。
            updateFetch(account.id) {
                it.copy(
                    packageFailure = ApiFailureKind.CREDENTIAL_INVALID,
                    isRefreshing = false,
                )
            }
            return
        }
        // 账号内串行：今日累计 → 已结算 7 天 → 资源包，避免单个账号同时打多条接口。
        refreshToday(account, credentials, requestedAt)
        // 定时刷新只取今日；7 天满 1 小时才重取（spec「冷启动请求量」）。
        if (scope == RefreshScope.FULL ||
            isWeekRefreshDue(
                lastFetchedAtMillis = weekFetchedAtMillis.value[account.id],
                nowMillis = requestedAt.toEpochMilli(),
            )
        ) {
            refreshWeek(account, credentials, requestedAt)
        }
        // 资源包只在冷启动/下拉取：包余量变化慢，定时重取只会白耗配额。
        if (scope == RefreshScope.FULL) {
            // 资源包单独落状态：包失败不覆盖今日/7 天，反之亦然（User Story 26）；刷新收尾也在这里。
            refreshPackages(account, credentials, requestedAt)
        } else {
            finishRefresh(account.id)
        }
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
                // 成功才覆盖快照；写盘失败不影响本次展示（下次离线会旧一些）。
                snapshots.saveToday(
                    accountId = account.id,
                    today = today,
                    fetchedAtMillis = now().toEpochMilli(),
                )
                updateFetch(account.id) {
                    // 不清 isRefreshing：后面的步骤（或 finishRefresh）统一收尾。
                    it.copy(today = today, failure = null, snapshotFetchedAtMillis = null)
                }
            },
            onFailure = { failure ->
                // 失败绝不清除旧数据与旧快照：优先保留本次已展示的数据，
                // 没有则回落到快照，两者都没有才是纯错误态（票据 07）。
                showTodayFailure(account.id, failure.kind)
            },
        )
    }

    /**
     * 已结算 7 天（票据 04）：成功后写 `WEEK_TREND` 快照（带抓取时间）并记下本次成功时刻，
     * 供定时刷新做 1 小时节流。失败保留上次成功的 7 天数据与旧快照，不抹掉已经取回的今日累计。
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
                // 成功才覆盖快照；失败分支不写也不清，旧快照留给离线展示。
                snapshots.save(
                    accountId = account.id,
                    module = SnapshotModule.WEEK_TREND,
                    payloadJson = WeekTrafficSnapshotCodec.encode(days),
                    fetchedAtMillis = now().toEpochMilli(),
                )
                weekFetchedAtMillis.update { it + (account.id to now().toEpochMilli()) }
                updateFetch(account.id) {
                    // 不清 isRefreshing：后面的步骤（或 finishRefresh）统一收尾。
                    it.copy(week = days, weekSnapshotFetchedAtMillis = null)
                }
            },
            onFailure = { failure ->
                showWeekFailure(account.id, failure.kind)
            },
        )
    }

    /**
     * 取资源包并写成余量视图（票据 05）；成功写 [SnapshotModule.PACKAGES] 快照。
     * 分组用本次请求时刻判定「未生效」，因此每次刷新都会重新分组；账号级 `isRefreshing` 在这里收尾。
     * 失败时回落到上次成功的包快照（票据 07），并标出快照抓取时间。
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
                // 与今日/7 天共用 SnapshotStore；写盘失败不影响本次展示（下次离线会旧一些）。
                snapshots.save(
                    accountId = account.id,
                    module = SnapshotModule.PACKAGES,
                    payloadJson = PackageSnapshotCodec.encode(packages),
                    fetchedAtMillis = now().toEpochMilli(),
                )
                val groups = groupPackages(packages, requestedAt)
                updateFetch(account.id) {
                    it.copy(
                        packageGroups = groups,
                        packageFailure = null,
                        packageSnapshotFetchedAtMillis = null,
                        isRefreshing = false,
                    )
                }
            },
            onFailure = { failure ->
                // 失败保留上次的余量视图；没有则回落包快照。账号级刷新结束由这里收尾。
                showPackageFailure(account.id, failure.kind, requestedAt)
            },
        )
    }

    /**
     * 今日累计失败的统一收尾：保留已展示数据，没有则回落今日快照；都没有就是错误态。
     * 凭证失效（解密失败/账号被删）也走这里，保证旧数据不被抹掉。
     * 不清 `isRefreshing`：账号内最后一步（资源包或 [finishRefresh]）统一收尾。
     */
    private suspend fun showTodayFailure(accountId: String, kind: ApiFailureKind) {
        val cached = snapshots.loadToday(accountId)
        updateFetch(accountId) { current ->
            val shown = current.today ?: cached?.value
            current.copy(
                today = shown,
                snapshotFetchedAtMillis = if (shown != null) {
                    cached?.fetchedAtMillis ?: current.snapshotFetchedAtMillis
                } else {
                    null
                },
                failure = kind,
            )
        }
    }

    /**
     * 7 天失败的统一收尾：保留已展示的 7 天数据，没有则回落 7 天快照。
     * 同样不清 `isRefreshing`，交给最后一步收尾。
     */
    private suspend fun showWeekFailure(accountId: String, kind: ApiFailureKind) {
        val cached = snapshots.load(
            accountId = accountId,
            module = SnapshotModule.WEEK_TREND,
            decode = WeekTrafficSnapshotCodec::decode,
        )
        updateFetch(accountId) { current ->
            val shown = current.week ?: cached?.value
            current.copy(
                week = shown,
                weekSnapshotFetchedAtMillis = if (shown != null) {
                    cached?.fetchedAtMillis ?: current.weekSnapshotFetchedAtMillis
                } else {
                    null
                },
                // 不覆盖今日累计已有的更具体错误（今日失败时它已落过 kind）。
                failure = current.failure ?: kind,
            )
        }
    }

    /**
     * 资源包失败的统一收尾：保留已展示的余量视图，没有则回落 PACKAGES 快照并按当前时刻重新分组
     * （「未生效」依赖展示时刻，不能用抓取时刻）。两者都没有就是纯错误态。
     * 本方法同时结束该账号本轮刷新。
     */
    private suspend fun showPackageFailure(
        accountId: String,
        kind: ApiFailureKind,
        requestedAt: Instant,
    ) {
        val cached = snapshots.load(
            accountId = accountId,
            module = SnapshotModule.PACKAGES,
            decode = PackageSnapshotCodec::decode,
        )
        updateFetch(accountId) { current ->
            val groups = current.packageGroups
                ?: cached?.value?.let { packages -> groupPackages(packages, requestedAt) }
            current.copy(
                packageGroups = groups,
                packageSnapshotFetchedAtMillis = if (groups != null) {
                    cached?.fetchedAtMillis ?: current.packageSnapshotFetchedAtMillis
                } else {
                    null
                },
                packageFailure = kind,
                isRefreshing = false,
            )
        }
    }

    /** 尚无任何数据可展示时（首次进入/切换账号），先读各模块上次成功快照顶上，避免空白。 */
    private suspend fun showSnapshotIfNothingToShow(accountId: String, requestedAt: Instant) {
        val current = fetches.value[accountId]
        if (current?.today == null) {
            snapshots.loadToday(accountId)?.let { cached ->
                updateFetch(accountId) {
                    it.copy(today = cached.value, snapshotFetchedAtMillis = cached.fetchedAtMillis)
                }
            }
        }
        if (current?.week == null) {
            snapshots.load(
                accountId = accountId,
                module = SnapshotModule.WEEK_TREND,
                decode = WeekTrafficSnapshotCodec::decode,
            )?.let { cached ->
                updateFetch(accountId) {
                    it.copy(week = cached.value, weekSnapshotFetchedAtMillis = cached.fetchedAtMillis)
                }
            }
        }
        if (current?.packageGroups == null) {
            snapshots.load(
                accountId = accountId,
                module = SnapshotModule.PACKAGES,
                decode = PackageSnapshotCodec::decode,
            )?.let { cached ->
                updateFetch(accountId) {
                    it.copy(
                        packageGroups = groupPackages(cached.value, requestedAt),
                        packageSnapshotFetchedAtMillis = cached.fetchedAtMillis,
                    )
                }
            }
        }
    }

    private fun finishRefresh(accountId: String) {
        updateFetch(accountId) { it.copy(isRefreshing = false) }
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
