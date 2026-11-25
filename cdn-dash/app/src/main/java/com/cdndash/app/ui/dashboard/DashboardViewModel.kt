package com.cdndash.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cdndash.app.data.AccountStore
import com.cdndash.app.domain.Account
import com.cdndash.app.domain.visibleAccounts
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** 看板只关心启用账号与「是否存在任何账号」两类信息；卡片数据由后续票据填充。 */
data class DashboardUiState(
    val accounts: List<Account> = emptyList(),
    val hasAnyAccount: Boolean = false,
)

class DashboardViewModel(accountStore: AccountStore) : ViewModel() {

    val uiState: StateFlow<DashboardUiState> = accountStore.accounts
        .map { allAccounts ->
            DashboardUiState(
                accounts = visibleAccounts(allAccounts),
                hasAnyAccount = allAccounts.isNotEmpty(),
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DashboardUiState(),
        )

    companion object {
        fun factory(accountStore: AccountStore): ViewModelProvider.Factory = viewModelFactory {
            initializer { DashboardViewModel(accountStore) }
        }
    }
}
