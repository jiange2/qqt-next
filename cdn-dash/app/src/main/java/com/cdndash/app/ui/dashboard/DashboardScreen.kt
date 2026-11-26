package com.cdndash.app.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cdndash.app.data.AccountStore
import com.cdndash.app.data.aliyun.AliyunApi
import com.cdndash.app.domain.Account
import com.cdndash.app.domain.TopSummary
import com.cdndash.app.domain.formatBeijingHourMinute
import com.cdndash.app.domain.formatBytes
import com.cdndash.app.domain.weekTotal
import com.cdndash.app.ui.components.AppCard
import com.cdndash.app.ui.components.FailureBanner
import com.cdndash.app.ui.components.MetricText
import com.cdndash.app.ui.navigation.PillNavContentPadding
import com.cdndash.app.ui.navigation.TopBarContentPadding
import com.cdndash.app.ui.packages.PackageSummarySection
import com.cdndash.app.ui.theme.LocalExtendedColors

/**
 * 看板页。顶部品牌渐变 Hero 卡（所有账号今日累计 / 最近 7 天总量，票据 06）后接账号卡片列表；
 * 卡片并列显示今日累计（「截至 HH:mm」、未结算）与最近 7 天合计 + 7 点折线
 * （已结算日、不含今天），下方是按区域展示的资源包余量摘要（票据 05），点卡片进入明细页。
 * 并接通打开 / 下拉 / 前台定时的刷新编排。
 *
 * 票据 07：卡片错误分「凭证失效 / 权限不足 / 网络错误」并各给一句可操作提示；
 * 请求失败且回落到快照时，今日累计下方标「数据时间 HH:mm（离线）」；
 * 资源包模块失败回落快照时，摘要同样标出快照抓取时间。
 *
 * 生命周期观察只做一件事：`ON_START` 启动定时刷新、`ON_STOP` 取消（User Story 30）；
 * 定时只更新今日累计（7 天 1 小时节流、资源包不随定时取，见 ViewModel）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    accountStore: AccountStore,
    aliyunApi: AliyunApi,
    onOpenSettings: () -> Unit,
    onOpenPackages: (Account) -> Unit,
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
                contentPadding = PaddingValues(
                    start = 16.dp,
                    top = TopBarContentPadding,
                    end = 16.dp,
                    bottom = PillNavContentPadding,
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item(key = "top-summary") {
                    TopSummaryHeader(summary = uiState.topSummary)
                }
                items(uiState.accounts, key = { it.account.id }) { state ->
                    AccountCard(
                        state = state,
                        onClick = { onOpenPackages(state.account) },
                    )
                }
            }
        }
    }
}

/**
 * 顶部 Hero 卡（票据 06）：品牌渐变底，今日累计为主大数，7 天总量为次指标。
 *
 * 有缺失时标注「N 个账号数据缺失」：ViewModel 把今日与 7 天两路的失败合并成
 * 一个账号级状态，缺失数是按账号的并集，无法拆成「今日缺几个、7 天缺几个」而不改刷新编排。
 * 放在 LazyColumn 的第一个 item 而不是覆盖层，因此不遮挡下拉刷新，并随列表滚动。
 */
@Composable
private fun TopSummaryHeader(summary: TopSummary, modifier: Modifier = Modifier) {
    val extended = LocalExtendedColors.current
    val gradient = Brush.linearGradient(
        colors = listOf(extended.heroStart, extended.heroEnd),
    )
    val onHero = Color.White
    val onHeroMuted = Color.White.copy(alpha = 0.75f)
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = Color.Transparent,
    ) {
        Box(
            modifier = Modifier
                .clip(MaterialTheme.shapes.extraLarge)
                .background(gradient),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "所有账号今日累计",
                    style = MaterialTheme.typography.labelLarge,
                    color = onHeroMuted,
                )
                Text(
                    text = formatBytes(summary.todayTotalBytes),
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontFeatureSettings = "tnum",
                    ),
                    color = onHero,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(
                    text = "未结算",
                    style = MaterialTheme.typography.bodySmall,
                    color = onHeroMuted,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Spacer(Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color.White.copy(alpha = 0.2f)),
                )
                Row(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "所有账号最近 7 天总量",
                            style = MaterialTheme.typography.labelMedium,
                            color = onHeroMuted,
                        )
                        Text(
                            text = formatBytes(summary.weekTotalBytes),
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontFeatureSettings = "tnum",
                            ),
                            color = onHero,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                        Text(
                            text = "已结算日 · 不含今天",
                            style = MaterialTheme.typography.bodySmall,
                            color = onHeroMuted,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    if (summary.missingAccountCount > 0) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.White.copy(alpha = 0.16f),
                        ) {
                            Text(
                                text = "${summary.missingAccountCount} 个账号数据缺失",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = onHero,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountCard(state: AccountTodayState, onClick: () -> Unit) {
    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = state.account.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                state.failure?.let { failure ->
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                    ) {
                        Text(
                            text = failure.label(),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = "查看资源包明细",
                    modifier = Modifier.padding(start = 4.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                YesterdayMetric(state = state, modifier = Modifier.weight(1f))
                TodayMetric(state = state, modifier = Modifier.weight(1f))
            }
            WeekMetric(state = state, modifier = Modifier.fillMaxWidth())
            // 三类错误各给一句可操作提示，不合并成「出错了」（User Story 34）。
            state.failure?.let { failure ->
                FailureBanner(label = failure.label(), hint = failure.actionHint())
            }
            PackageSummarySection(
                groups = state.packageGroups,
                failure = state.packageFailure,
                isRefreshing = state.isRefreshing,
                modifier = Modifier.fillMaxWidth(),
                snapshotFetchedAtMillis = state.packageSnapshotFetchedAtMillis,
            )
        }
    }
}

/** 昨日流量：最近 7 天里最后一天（昨天，北京自然日）的已结算流量。 */
@Composable
private fun YesterdayMetric(state: AccountTodayState, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = "昨日流量",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        MetricText(
            text = state.week?.lastOrNull()?.let { formatBytes(it.bytes) } ?: "—",
            large = true,
            color = if (state.week != null) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        val caption = when {
            state.weekSnapshotFetchedAtMillis != null ->
                "数据时间 ${formatBeijingHourMinute(state.weekSnapshotFetchedAtMillis)}（离线）"

            state.week != null -> "已结算"

            state.failure != null -> "本次未取到数据"

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

/** 今日累计：数值 + 「截至 HH:mm」（北京时间，取最后一个数据点时刻；未结算）。 */
@Composable
private fun TodayMetric(state: AccountTodayState, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = "今日累计",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        MetricText(
            text = state.today?.let { formatBytes(it.bytes) } ?: "—",
            large = true,
            color = if (state.today != null) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        val caption = when {
            // 失败回落的旧数据：标快照自己的抓取时间，不能拿「现在」冒充新数（User Story 31）。
            state.snapshotFetchedAtMillis != null ->
                "数据时间 ${formatBeijingHourMinute(state.snapshotFetchedAtMillis)}（离线）"

            state.today != null ->
                "截至 ${formatBeijingHourMinute(state.today.dataThrough)} · 未结算"

            // 无数据又失败：错误态而不是一句「暂无数据」，具体原因见卡片下方提示。
            state.failure != null -> "本次未取到数据"

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
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = "最近 7 天",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        MetricText(
            text = state.week?.let { formatBytes(weekTotal(it)) } ?: "—",
            large = true,
            color = if (state.week != null) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        val caption = when {
            // 7 天请求失败回落到快照：同样只标快照自己的抓取时间。
            state.weekSnapshotFetchedAtMillis != null ->
                "数据时间 ${formatBeijingHourMinute(state.weekSnapshotFetchedAtMillis)}（离线）"

            // GLOSSARY「已结算日」：只统计已结束的北京自然日，今天未结算不参与。
            state.week != null -> "7 天合计 · 已结算，不含今天"

            state.failure != null -> "本次未取到数据"

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
            modifier = Modifier.padding(top = 6.dp),
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
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Icon(
                imageVector = Icons.Outlined.CloudQueue,
                contentDescription = null,
                modifier = Modifier
                    .padding(20.dp)
                    .size(48.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Text(
            text = if (hasAnyAccount) "没有启用的账号" else "还没有账号，去设置添加",
            modifier = Modifier.padding(top = 24.dp),
            style = MaterialTheme.typography.titleLarge,
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
