package com.cdndash.app.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cdndash.app.data.AccountStore
import com.cdndash.app.domain.Account

/**
 * 看板页。票据 02 只渲染启用账号的名称卡片壳，卡片内数据字段留「—」占位，
 * 具体数字由票据 03–06 填充。顺序完全来自手动排序（User Story 7）。
 */
@Composable
fun DashboardScreen(
    accountStore: AccountStore,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = viewModel(factory = DashboardViewModel.factory(accountStore)),
) {
    val uiState by viewModel.uiState.collectAsState()

    if (uiState.accounts.isEmpty()) {
        DashboardEmptyState(
            hasAnyAccount = uiState.hasAnyAccount,
            onOpenSettings = onOpenSettings,
            modifier = modifier,
        )
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(uiState.accounts, key = { it.id }) { account ->
                AccountCardShell(account = account)
            }
        }
    }
}

@Composable
private fun AccountCardShell(account: Account) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = account.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                MetricPlaceholder(
                    label = "今日累计",
                    modifier = Modifier.weight(1f),
                )
                MetricPlaceholder(
                    label = "最近 7 天",
                    modifier = Modifier.weight(1f),
                )
            }
            MetricPlaceholder(
                label = "资源包余量",
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun MetricPlaceholder(label: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "—",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DashboardEmptyState(
    hasAnyAccount: Boolean,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Outlined.Dashboard,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = if (hasAnyAccount) "没有启用的账号" else "还没有账号，去设置添加",
            modifier = Modifier.padding(top = 16.dp),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = if (hasAnyAccount) {
                "账号都处于停用状态，去设置启用后即可在看板查看。"
            } else {
                "录入阿里云账号的只读 AccessKey 后，这里会显示今日累计、最近 7 天走势与资源包余量。"
            },
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(
            onClick = onOpenSettings,
            modifier = Modifier.padding(top = 24.dp),
        ) {
            Text(if (hasAnyAccount) "去设置启用账号" else "去设置添加账号")
        }
    }
}
