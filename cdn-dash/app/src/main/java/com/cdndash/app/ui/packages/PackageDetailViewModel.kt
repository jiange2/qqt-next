package com.cdndash.app.ui.packages

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
import com.cdndash.app.domain.AccountCredentials
import com.cdndash.app.domain.DayTrafficPoint
import com.cdndash.app.domain.PackageGroup
import com.cdndash.app.domain.ResourcePackage
import com.cdndash.app.domain.SnapshotModule
import com.cdndash.app.domain.groupPackages
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 资源包明细页状态；[groups] 为 null 表示还没取到（区别于「取到了但没有包」）。
 *
 * @property week 最近 7 个已结算日（含区域拆分），供预计可用天数使用；null 表示本次没有。
 * @property snapshotFetchedAtMillis 非 null 表示 [groups] 来自上次成功快照（离线展示），
 * 值是该快照的抓取时刻；页面据此标「数据时间 HH:mm（离线）」。
 * @property weekSnapshotFetchedAtMillis 非 null 表示 [week] 来自快照：天数仍可展示，
 * 但页面不再推算预计用完日，避免用旧数据给出精确日期。
 */
data class PackageDetailUiState(
    val accountName: String = "",
    val groups: List<PackageGroup>? = null,
    val week: List<DayTrafficPoint>? = null,
    val failure: ApiFailureKind? = null,
    val isRefreshing: Boolean = false,
    val snapshotFetchedAtMillis: Long? = null,
    val weekSnapshotFetchedAtMillis: Long? = null,
)

/**
 * 资源包明细页 ViewModel（票据 05、票据 07）。
 *
 * 只依赖 [AliyunApi] 与 [AccountStore] 两个接缝；进页即取一次，失败给重试。
 * 快照经 [SnapshotStore] 走 `PACKAGES`/`WEEK_TREND` 模块：进页先亮上次成功的包与 7 天，
 * 取数失败时保留并标出快照抓取时间（与看板卡片同一套离线语义）；成功才覆盖快照。
 *
 * 7 天只服务于预计可用天数，独立于资源包：它失败不改变包的错误态，页面仍显示包列表；
 * 进页会多一次 `DescribeDomainTrafficData` 请求（ADR 0018），从看板点进来时与看板预热的数据无关。
 */
class PackageDetailViewModel(
    private val accountStore: AccountStore,
    private val aliyunApi: AliyunApi,
    private val accountId: String,
    private val now: () -> Instant = { Instant.now() },
) : ViewModel() {

    private val mutableUiState = MutableStateFlow(PackageDetailUiState())
    val uiState: StateFlow<PackageDetailUiState> = mutableUiState.asStateFlow()

    private val snapshots = SnapshotStore(accountStore)
    private var refreshJob: Job? = null

    init {
        viewModelScope.launch {
            val account = accountStore.accounts.first().firstOrNull { it.id == accountId }
            if (account != null) {
                mutableUiState.update { it.copy(accountName = account.name) }
            }
        }
        refresh()
    }

    /** 重新取资源包与 7 天；进行中时忽略重复触发。 */
    fun refresh() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            mutableUiState.update { it.copy(isRefreshing = true) }
            val requestedAt = now()
            // 先亮出上次成功的包与 7 天（弱网/断网时列表不从空白开始），失败分支再兜一次。
            showSnapshotsIfNothingToShow(requestedAt)
            val credentials = try {
                accountStore.credentials(accountId)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                null
            }
            if (credentials == null) {
                // 解密失败或账号已被删除：按凭证失效降级；7 天同样没有可用凭据。
                showPackageFailure(ApiFailureKind.CREDENTIAL_INVALID, requestedAt)
                showWeekFallback()
                return@launch
            }
            refreshPackages(credentials, requestedAt)
            // 资源包先展示；7 天只用于估算，失败不改变包列表与错误态。
            refreshWeek(credentials, requestedAt)
        }
    }

    private suspend fun refreshPackages(credentials: AccountCredentials, requestedAt: Instant) {
        val result: ApiResult<List<ResourcePackage>> = try {
            aliyunApi.resourcePackages(credentials, requestedAt)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            ApiResult.Failure(ApiFailureKind.NETWORK)
        }

        result.fold(
            onSuccess = { packages ->
                // 成功才覆盖快照；写盘失败不影响本次展示。
                snapshots.save(
                    accountId = accountId,
                    module = SnapshotModule.PACKAGES,
                    payloadJson = PackageSnapshotCodec.encode(packages),
                    fetchedAtMillis = now().toEpochMilli(),
                )
                val groups = groupPackages(packages, requestedAt)
                mutableUiState.update {
                    it.copy(
                        groups = groups,
                        failure = null,
                        isRefreshing = false,
                        snapshotFetchedAtMillis = null,
                    )
                }
            },
            onFailure = { failure ->
                // 失败保留上次的列表（或回落快照），只标记错误与结束刷新。
                showPackageFailure(failure.kind, requestedAt)
            },
        )
    }

    private suspend fun refreshWeek(credentials: AccountCredentials, requestedAt: Instant) {
        val result: ApiResult<List<DayTrafficPoint>> = try {
            aliyunApi.settledDays(credentials, requestedAt)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            ApiResult.Failure(ApiFailureKind.NETWORK)
        }

        result.fold(
            onSuccess = { days ->
                // 与看板共用 WEEK_TREND 快照；下次断网进页仍能按旧 7 天给天数（不打日期）。
                snapshots.save(
                    accountId = accountId,
                    module = SnapshotModule.WEEK_TREND,
                    payloadJson = WeekTrafficSnapshotCodec.encode(days),
                    fetchedAtMillis = now().toEpochMilli(),
                )
                mutableUiState.update {
                    it.copy(week = days, weekSnapshotFetchedAtMillis = null)
                }
            },
            onFailure = { showWeekFallback() },
        )
    }

    /** 进页/重试开始时若还没有数据，先展示上次成功的包与 7 天快照。 */
    private suspend fun showSnapshotsIfNothingToShow(requestedAt: Instant) {
        if (mutableUiState.value.groups == null) {
            loadSnapshot(requestedAt)?.let { (groups, fetchedAtMillis) ->
                mutableUiState.update {
                    it.copy(groups = groups, snapshotFetchedAtMillis = fetchedAtMillis)
                }
            }
        }
        if (mutableUiState.value.week == null) {
            loadWeekSnapshot()?.let { (week, fetchedAtMillis) ->
                mutableUiState.update {
                    it.copy(week = week, weekSnapshotFetchedAtMillis = fetchedAtMillis)
                }
            }
        }
    }

    /** 资源包失败收尾：保留已展示的列表，没有则回落快照；两者都没有才是纯错误态。 */
    private suspend fun showPackageFailure(kind: ApiFailureKind, requestedAt: Instant) {
        val current = mutableUiState.value
        val cached = if (current.groups == null) loadSnapshot(requestedAt) else null
        val groups = current.groups ?: cached?.first
        mutableUiState.update {
            it.copy(
                groups = groups,
                snapshotFetchedAtMillis = if (groups != null) {
                    cached?.second ?: current.snapshotFetchedAtMillis
                } else {
                    null
                },
                failure = kind,
                isRefreshing = false,
            )
        }
    }

    /** 7 天失败收尾：保留已展示的周数据，没有则回落快照并标记离线（天数照给、日期不给）。 */
    private suspend fun showWeekFallback() {
        val current = mutableUiState.value
        val cached = if (current.week == null) loadWeekSnapshot() else null
        val week = current.week ?: cached?.first
        mutableUiState.update {
            it.copy(
                week = week,
                weekSnapshotFetchedAtMillis = if (week != null) {
                    cached?.second ?: current.weekSnapshotFetchedAtMillis
                } else {
                    null
                },
            )
        }
    }

    /** 读快照并按当前时刻重新分组（「未生效」依赖展示时刻，不能用抓取时刻）。 */
    private suspend fun loadSnapshot(requestedAt: Instant): Pair<List<PackageGroup>, Long>? =
        snapshots.load(
            accountId = accountId,
            module = SnapshotModule.PACKAGES,
            decode = PackageSnapshotCodec::decode,
        )?.let { cached ->
            groupPackages(cached.value, requestedAt) to cached.fetchedAtMillis
        }

    private suspend fun loadWeekSnapshot(): Pair<List<DayTrafficPoint>, Long>? =
        snapshots.load(
            accountId = accountId,
            module = SnapshotModule.WEEK_TREND,
            decode = WeekTrafficSnapshotCodec::decode,
        )?.let { cached -> cached.value to cached.fetchedAtMillis }

    companion object {
        fun factory(
            accountStore: AccountStore,
            aliyunApi: AliyunApi,
            accountId: String,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { PackageDetailViewModel(accountStore, aliyunApi, accountId) }
        }
    }
}
