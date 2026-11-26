package com.cdndash.app.ui.spend

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cdndash.app.data.AccountStore
import com.cdndash.app.data.aliyun.AliyunApi
import com.cdndash.app.data.aliyun.ApiFailureKind
import com.cdndash.app.domain.MonthlyBill
import com.cdndash.app.domain.formatBeijingHourMinute
import com.cdndash.app.domain.formatMoney
import com.cdndash.app.domain.totalBillAmount
import com.cdndash.app.ui.components.AppCard
import com.cdndash.app.ui.components.FailureBanner
import com.cdndash.app.ui.dashboard.spendActionHint
import com.cdndash.app.ui.dashboard.spendLabel
import com.cdndash.app.ui.navigation.PillNavContentPadding
import com.cdndash.app.ui.navigation.TopBarContentPadding
import java.time.YearMonth

/**
 * 花费页（票据 08）：账号切换 + 近 12 个已出账完整月的柱状图 + 全年合计。
 *
 * - 柱图是 Canvas 自绘（spec「不引入图表库」），预付费月份在柱顶有圆点、列表行有文字标注；
 * - 每月点开可看现金与代金券拆分；主数与明细都只写「应付金额」，不做任何税额表述；
 * - 无 `bss` 读权限的账号显示「费用权限未开启」并给出可操作提示，其他账号照常；
 * - 页面首次组合才创建 ViewModel 并触发按需加载，24 小时内重进走 AccountStore 快照。
 */
@Composable
fun SpendScreen(
    accountStore: AccountStore,
    aliyunApi: AliyunApi,
    modifier: Modifier = Modifier,
    viewModel: SpendViewModel = viewModel(
        factory = SpendViewModel.factory(accountStore, aliyunApi),
    ),
) {
    val uiState by viewModel.uiState.collectAsState()
    SpendContent(
        uiState = uiState,
        onSelectAccount = viewModel::selectAccount,
        onRefresh = viewModel::refresh,
        modifier = modifier,
    )
}

@Composable
private fun SpendContent(
    uiState: SpendUiState,
    onSelectAccount: (String) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, top = TopBarContentPadding, end = 16.dp, bottom = PillNavContentPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (uiState.accounts.isEmpty()) {
            SpendEmptyState(hasAnyAccount = uiState.hasAnyAccount)
            return@Column
        }

        AccountSwitcher(
            accounts = uiState.accounts.map { it.id to it.name },
            selectedAccountId = uiState.selectedAccountId,
            onSelectAccount = onSelectAccount,
        )

        SpendSummary(uiState = uiState, onRefresh = onRefresh)

        uiState.failure?.let { failure ->
            SpendFailureNotice(
                failure = failure,
                hasBills = uiState.bills.isNotEmpty(),
                fetchedAtMillis = uiState.fetchedAtMillis,
                onRefresh = onRefresh,
            )
        }

        if (uiState.bills.isNotEmpty()) {
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    MonthlyBarChart(bills = uiState.bills)
                    if (uiState.bills.any { it.includesPrepaidPackage }) {
                        Text(
                            text = "柱顶圆点的月份含预付费资源包",
                            modifier = Modifier.padding(top = 8.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            MonthBillList(bills = uiState.bills)
        } else if (uiState.failure == null) {
            Text(
                text = if (uiState.isRefreshing) "正在加载近 12 个月账单…" else "正在读取账单…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AccountSwitcher(
    accounts: List<Pair<String, String>>,
    selectedAccountId: String?,
    onSelectAccount: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        accounts.forEach { (id, name) ->
            FilterChip(
                selected = id == selectedAccountId,
                onClick = { onSelectAccount(id) },
                label = { Text(name) },
            )
        }
    }
}

@Composable
private fun SpendSummary(uiState: SpendUiState, onRefresh: () -> Unit) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(start = 20.dp, top = 16.dp, end = 8.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "全年合计",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = if (uiState.bills.isEmpty()) {
                        "—"
                    } else {
                        "¥${formatMoney(totalBillAmount(uiState.bills))}"
                    },
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontFeatureSettings = "tnum",
                    ),
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(
                    text = "应付金额 · 最近 12 个已出账完整月（不含当月）",
                    modifier = Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                uiState.fetchedAtMillis?.let { fetchedAt ->
                    Text(
                        text = "数据时间 ${formatBeijingHourMinute(fetchedAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = onRefresh, enabled = !uiState.isRefreshing) {
                Icon(
                    imageVector = Icons.Outlined.Refresh,
                    contentDescription = if (uiState.isRefreshing) "刷新中" else "刷新",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/**
 * 错误归一后的提示（文案表在 `ui/dashboard/FailureText.kt`，与看板/资源包共用一套）：
 * 无权限时明确是「费用权限未开启」并说明去哪里补权限；
 * 网络类失败若已有旧账单，只提示数据时间，图表照常展示。
 */
@Composable
private fun SpendFailureNotice(
    failure: ApiFailureKind,
    hasBills: Boolean,
    fetchedAtMillis: Long?,
    onRefresh: () -> Unit,
) {
    val cachedAtHourMinute = if (hasBills) {
        fetchedAtMillis?.let { formatBeijingHourMinute(it) }
    } else {
        null
    }
    FailureBanner(
        label = failure.spendLabel(),
        hint = failure.spendActionHint(cachedAtHourMinute),
    ) {
        if (failure != ApiFailureKind.NETWORK || !hasBills) {
            TextButton(onClick = onRefresh) { Text("刷新") }
        }
    }
}

/** 12 根月柱（Canvas 自绘）+ 月份标签；最新月实色高亮，预付费月份在柱顶画一个圆点。 */
@Composable
private fun MonthlyBarChart(bills: List<MonthlyBill>) {
    val barColor = MaterialTheme.colorScheme.primary
    val mutedBarColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
    val prepaidColor = MaterialTheme.colorScheme.tertiary
    val maxAmount = bills.maxOfOrNull { it.pretaxAmount }?.toFloat()?.takeIf { it > 0f } ?: 1f

    Column(modifier = Modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp),
        ) {
            val slotWidth = size.width / bills.size.coerceAtLeast(1)
            val barWidth = slotWidth * 0.52f
            val markerSpace = 12.dp.toPx()
            val usableHeight = size.height - markerSpace
            val corner = CornerRadius(5.dp.toPx(), 5.dp.toPx())
            bills.forEachIndexed { index, bill ->
                val value = bill.pretaxAmount.toFloat().coerceAtLeast(0f)
                val barHeight = usableHeight * (value / maxAmount)
                val left = slotWidth * index + (slotWidth - barWidth) / 2f
                val top = size.height - barHeight
                drawRoundRect(
                    color = if (index == bills.lastIndex) barColor else mutedBarColor,
                    topLeft = Offset(left, top),
                    size = Size(barWidth, barHeight),
                    cornerRadius = corner,
                )
                if (bill.includesPrepaidPackage) {
                    drawCircle(
                        color = prepaidColor,
                        radius = 3.dp.toPx(),
                        center = Offset(left + barWidth / 2f, (top - markerSpace / 2f).coerceAtLeast(0f)),
                    )
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            bills.forEachIndexed { index, bill ->
                val latest = index == bills.lastIndex
                Text(
                    text = bill.month.monthValue.toString(),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = if (latest) FontWeight.Bold else FontWeight.Normal,
                    ),
                    color = if (latest) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** 逐月明细：默认收起，点一行展开现金与代金券；预付费月份在行内直接标注。 */
@Composable
private fun MonthBillList(bills: List<MonthlyBill>) {
    var expandedMonth by remember { mutableStateOf<YearMonth?>(null) }
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            bills.asReversed().forEachIndexed { index, bill ->
                val expanded = expandedMonth == bill.month
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expandedMonth = if (expanded) null else bill.month }
                        .padding(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${bill.month.year}年${bill.month.monthValue}月",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (bill.includesPrepaidPackage) {
                            Spacer(Modifier.width(8.dp))
                            PrepaidTag()
                        }
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = "¥${formatMoney(bill.pretaxAmount)}",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Medium,
                                fontFeatureSettings = "tnum",
                            ),
                        )
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                            contentDescription = if (expanded) "收起明细" else "展开明细",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (expanded) {
                        Row(modifier = Modifier.padding(top = 4.dp)) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "现金",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = "¥${formatMoney(bill.cashAmount)}",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "代金券",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = "¥${formatMoney(bill.voucherAmount)}",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                                )
                            }
                        }
                    }
                }
                if (index < bills.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun PrepaidTag() {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = RoundedCornerShape(6.dp),
    ) {
        Text(
            text = "含预付费资源包",
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
        )
    }
}

@Composable
private fun SpendEmptyState(hasAnyAccount: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = if (hasAnyAccount) "没有启用的账号" else "还没有账号，去设置添加",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = if (hasAnyAccount) {
                "花费页只展示启用账号，去设置启用后即可查看。"
            } else {
                "录入阿里云账号的只读 AccessKey 后，这里会显示近 12 个月账号花费。"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
