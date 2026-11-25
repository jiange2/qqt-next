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
import com.cdndash.app.data.aliyun.fold
import com.cdndash.app.domain.PackageGroup
import com.cdndash.app.domain.ResourcePackage
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

/** 资源包明细页状态；[groups] 为 null 表示还没取到（区别于「取到了但没有包」）。 */
data class PackageDetailUiState(
    val accountName: String = "",
    val groups: List<PackageGroup>? = null,
    val failure: ApiFailureKind? = null,
    val isRefreshing: Boolean = false,
)

/**
 * 资源包明细页 ViewModel（票据 05）。
 *
 * 只依赖 [AliyunApi] 与 [AccountStore] 两个接缝；进页即取一次，失败给重试。
 * 展示旧快照属于票据 07，这里不读快照。
 */
class PackageDetailViewModel(
    private val accountStore: AccountStore,
    private val aliyunApi: AliyunApi,
    private val accountId: String,
    private val now: () -> Instant = { Instant.now() },
) : ViewModel() {

    private val mutableUiState = MutableStateFlow(PackageDetailUiState())
    val uiState: StateFlow<PackageDetailUiState> = mutableUiState.asStateFlow()

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

    /** 重新取资源包；进行中时忽略重复触发。 */
    fun refresh() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            mutableUiState.update { it.copy(isRefreshing = true) }
            val requestedAt = now()
            val result: ApiResult<List<ResourcePackage>> = try {
                val credentials = accountStore.credentials(accountId)
                if (credentials == null) {
                    // 解密失败或账号已被删除：按凭证失效降级。
                    ApiResult.Failure(ApiFailureKind.CREDENTIAL_INVALID)
                } else {
                    aliyunApi.resourcePackages(credentials, requestedAt)
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                ApiResult.Failure(ApiFailureKind.NETWORK)
            }

            result.fold(
                onSuccess = { packages ->
                    val groups = groupPackages(packages, requestedAt)
                    mutableUiState.update {
                        it.copy(groups = groups, failure = null, isRefreshing = false)
                    }
                },
                onFailure = { failure ->
                    // 失败保留上次的列表，只标记错误与结束刷新。
                    mutableUiState.update {
                        it.copy(failure = failure.kind, isRefreshing = false)
                    }
                },
            )
        }
    }

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
