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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cdndash.app.data.AccountStore
import com.cdndash.app.data.aliyun.AliyunApi
import com.cdndash.app.data.aliyun.ApiFailureKind
import com.cdndash.app.domain.TopSummary
import com.cdndash.app.domain.formatBeijingHourMinute
import com.cdndash.app.domain.formatBytes
import com.cdndash.app.domain.weekTotal

/**
 * 看板页。顶部两数（所有账号今日累计 / 最近 7 天总量，票据 06）后接账号卡片列表；
 * 卡片并列显示今日累计（「截至 HH:mm」、未结算）与最近 7 天合计 + 7 点折线
 * （已结算日、不含今天）；并接通打开 / 下拉 / 前台定时的刷新编排。
 * 资源包余量仍为占位，由票据 05 填充。
 *
 * 生命周期观察只做一件事：`ON_START` 启动定时刷新、`ON_STOP` 取消（User Story 30）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    accountStore: AccountStore,
    aliyunApi: AliyunApi,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = viewModel(
        factory = DashboardViewModel.factory(accountStore, aliyunApi),
    ),
) {
    val uiState by viewModel.uiState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewModel.onForegroundStart()
                Lifecycle.Event.ON_STOP -> viewModel.onForegroundStop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            // 切到其它标签/离开页面时组件先被销毁，兜底停掉定时器（ON_STOP 未必来得及派发）。
            viewModel.onForegroundStop()
        }
    }

    if (uiState.accounts.isEmpty()) {
        DashboardEmptyState(
            hasAnyAccount = uiState.hasAnyAccount,
            onOpenSettings = onOpenSettings,
            modifier = modifier,
        )
    } else {
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = modifier.fillMaxSize(),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "top-summary") {
                    TopSummaryHeader(summary = uiState.topSummary)
                }
                items(uiState.accounts, key = { it.account.id }) { state ->
                    AccountCard(state = state)
                }
            }
        }
    }
}

/**
 * 顶部两数（票据 06）：所有账号今日累计与最近 7 天总量，各带口径小字。
 *
 * 有缺失时两张卡都标注「N 个账号数据缺失」：ViewModel 把今日与 7 天两路的失败合并成
 * 一个账号级状态，缺失数是按账号的并集，无法拆成「今日缺几个、7 天缺几个」而不改刷新编排。
 * 放在 LazyColumn 的第一个 item 而不是覆盖层，因此不遮挡下拉刷新，并随列表滚动。
 */
@Composable
private fun TopSummaryHeader(summary: TopSummary, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SummaryMetric(
            label = "所有账号今日累计",
            value = formatBytes(summary.todayTotalBytes),
            caption = "未结算",
            missingAccountCount = summary.missingAccountCount,
            modifier = Modifier.weight(1f),
        )
        SummaryMetric(
            label = "所有账号最近 7 天总量",
            value = formatBytes(summary.weekTotalBytes),
            caption = "已结算日 · 不含今天",
            missingAccountCount = summary.missingAccountCount,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SummaryMetric(
    label: String,
    value: String,
    caption: String,
    missingAccountCount: Int,
    modifier: Modifier = Modifier,
) {
    Card(modifier) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
            )
            if (missingAccountCount > 0) {
                Text(
                    text = "$missingAccountCount 个账号数据缺失",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Text(
                text = caption,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AccountCard(state: AccountTodayState) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = state.account.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                state.failure?.let { failure ->
                    Text(
                        text = failure.label(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                TodayMetric(state = state, modifier = Modifier.weight(1f))
                WeekMetric(state = state, modifier = Modifier.weight(1f))
            }
            MetricPlaceholder(
                label = "资源包余量",
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** 今日累计：数值 + 「截至 HH:mm」（北京时间，取最后一个数据点时刻；未结算）。 */
@Composable
private fun TodayMetric(state: AccountTodayState, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            text = "今日累计",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = state.today?.let { formatBytes(it.bytes) } ?: "—",
            style = MaterialTheme.typography.headlineSmall,
            color = if (state.today != null) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        val caption = when {
            state.today != null ->
                "截至 ${formatBeijingHourMinute(state.today.dataThrough)} · 未结算"
            state.isRefreshing -> "加载中…"
            else -> "暂无数据"
        }
        Text(
            text = caption,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 最近 7 天：合计 + 7 点折线 + 口径小字（已结算日、不含今天）。
 *
 * 今日累计的「截至 HH:mm · 未结算」保持独立一列，两者并列而不是互相替换。
 */
@Composable
private fun WeekMetric(state: AccountTodayState, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            text = "最近 7 天",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = state.week?.let { formatBytes(weekTotal(it)) } ?: "—",
            style = MaterialTheme.typography.headlineSmall,
            color = if (state.week != null) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        val caption = when {
            // GLOSSARY「已结算日」：只统计已结束的北京自然日，今天未结算不参与。
            state.week != null -> "7 天合计 · 已结算，不含今天"
            state.isRefreshing -> "加载中…"
            else -> "暂无数据"
        }
        Text(
            text = caption,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        WeekSparkline(
            days = state.week.orEmpty(),
            modifier = Modifier.padding(top = 4.dp),
        )
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

/** 三类错误文案可区分（User Story 34）：凭证失效 / 权限不足 / 网络错误。 */
private fun ApiFailureKind.label(): String = when (this) {
    ApiFailureKind.CREDENTIAL_INVALID -> "凭证失效"
    ApiFailureKind.PERMISSION_DENIED -> "权限不足"
    ApiFailureKind.NETWORK -> "网络错误"
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
