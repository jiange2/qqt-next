package com.cdndash.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cdndash.app.data.AccountStore
import com.cdndash.app.domain.Account
import com.cdndash.app.domain.validateNewAccount
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val accountStore: AccountStore) : ViewModel() {

    /** 全部账号（含停用），按手动顺序。 */
    val accounts: StateFlow<List<Account>> = accountStore.accounts.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    val refreshIntervalMinutes: StateFlow<Int> = accountStore.refreshIntervalMinutes.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AccountStore.DEFAULT_REFRESH_INTERVAL_MINUTES,
    )

    private val _formError = MutableStateFlow<String?>(null)
    val formError: StateFlow<String?> = _formError.asStateFlow()

    /**
     * 校验并新增账号。校验以存储里的最新账号名为准（不依赖可能滞后的 UI 状态）；
     * 成功返回 true 并清空错误，失败把提示留在 [formError] 供表单展示。
     */
    suspend fun addAccount(
        name: String,
        accessKeyId: String,
        accessKeySecret: String,
    ): Boolean {
        val existingNames = accountStore.accounts.first().map { it.name }
        val error = validateNewAccount(name, accessKeyId, accessKeySecret, existingNames)
        if (error != null) {
            _formError.value = error.message
            return false
        }
        accountStore.addAccount(
            name = name.trim(),
            accessKeyId = accessKeyId.trim(),
            accessKeySecret = accessKeySecret.trim(),
        )
        _formError.value = null
        return true
    }

    fun clearFormError() {
        _formError.value = null
    }

    fun deleteAccount(accountId: String) {
        viewModelScope.launch { accountStore.deleteAccount(accountId) }
    }

    fun setEnabled(accountId: String, enabled: Boolean) {
        viewModelScope.launch { accountStore.setEnabled(accountId, enabled) }
    }

    /** 拖拽排序落到存储；只接受完整列表内的相邻/跨项移动。 */
    fun moveAccount(fromIndex: Int, toIndex: Int) {
        val current = accounts.value
        if (fromIndex == toIndex) return
        if (fromIndex !in current.indices || toIndex !in current.indices) return
        val reordered = current.toMutableList().apply {
            add(toIndex, removeAt(fromIndex))
        }
        viewModelScope.launch {
            accountStore.reorderAccounts(reordered.map { it.id })
        }
    }

    fun setRefreshInterval(minutes: Int) {
        viewModelScope.launch { accountStore.setRefreshIntervalMinutes(minutes) }
    }

    companion object {
        fun factory(accountStore: AccountStore): ViewModelProvider.Factory = viewModelFactory {
            initializer { SettingsViewModel(accountStore) }
        }
    }
}
