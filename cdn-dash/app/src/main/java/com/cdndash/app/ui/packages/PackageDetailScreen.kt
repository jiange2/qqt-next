package com.cdndash.app.ui.packages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cdndash.app.data.AccountStore
import com.cdndash.app.data.aliyun.AliyunApi
import com.cdndash.app.data.aliyun.ApiFailureKind
import com.cdndash.app.domain.PackageGroup
import com.cdndash.app.domain.formatBeijingHourMinute
import com.cdndash.app.domain.formatBytes
import com.cdndash.app.domain.formatPackageDate
import com.cdndash.app.domain.packageGroupsByRegion
import com.cdndash.app.ui.components.AppCard
import com.cdndash.app.ui.components.FailureBanner
import com.cdndash.app.ui.components.MetricText
import com.cdndash.app.ui.dashboard.actionHint
import com.cdndash.app.ui.dashboard.label
import com.cdndash.app.ui.navigation.PillNavContentPadding
import com.cdndash.app.ui.navigation.TopBarContentPadding
import com.cdndash.app.ui.theme.LocalExtendedColors

/**
 * 资源包明细页（票据 05、票据 07）：按区域分区、区域内按到期日升序，每行显示到期日、包数、
 * 剩余/总量与「未生效」标注（User Story 16–20）。
 *
 * 数据只从 [AliyunApi] 取；区域之间只分区展示，不合计余量（GLOSSARY「区域」）。
 * 取数失败时展示上次成功的包快照并标出抓取时间（「数据时间 HH:mm（离线）」）。
 */
@Composable
fun PackageDetailScreen(
    accountStore: AccountStore,
    aliyunApi: AliyunApi,
    accountId: String,
    modifier: Modifier = Modifier,
    viewModel: PackageDetailViewModel = viewModel(
        key = accountId,
        factory = PackageDetailViewModel.factory(accountStore, aliyunApi, accountId),
    ),
) {
    val state by viewModel.uiState.collectAsState()
    PackageDetailContent(
        state = state,
        onRetry = viewModel::refresh,
        modifier = modifier,
    )
}

@Composable
private fun PackageDetailContent(
    state: PackageDetailUiState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val groups = state.groups
    when {
        // 一次都没取到且失败：整页错误 + 重试。
        groups == null && state.failure != null -> PackageDetailError(
            failure = requireNotNull(state.failure),
            onRetry = onRetry,
            modifier = modifier,
        )

        groups == null -> Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }

        else -> PackageDetailList(
            accountName = state.accountName,
            groups = groups,
            failure = state.failure,
            snapshotFetchedAtMillis = state.snapshotFetchedAtMillis,
            onRetry = onRetry,
            modifier = modifier,
        )
    }
}

@Composable
private fun PackageDetailList(
    accountName: String,
    groups: List<PackageGroup>,
    failure: ApiFailureKind?,
    snapshotFetchedAtMillis: Long?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            top = TopBarContentPadding,
            end = 16.dp,
            bottom = PillNavContentPadding,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header") {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = accountName.ifBlank { "该账号" },
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                )
                Text(
                    text = "只统计 CDN 流量包（cdnflowbag）；不同区域的余量不可相加。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (snapshotFetchedAtMillis != null) {
                    Text(
                        text = "数据时间 ${formatBeijingHourMinute(snapshotFetchedAtMillis)}（离线）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (failure != null) {
            item(key = "refresh-failure") {
                FailureBanner(
                    label = "刷新失败：${failure.label()}，以下为上次结果",
                    hint = failure.actionHint(),
                ) {
                    Button(onClick = onRetry, modifier = Modifier.padding(top = 8.dp)) {
                        Text("重试")
                    }
                }
            }
        }

        if (groups.isEmpty()) {
            item(key = "empty") {
                Text(
                    text = "该账号没有 CDN 流量包",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        packageGroupsByRegion(groups).forEach { regionSection ->
            item(key = "region-${regionSection.region}") {
                RegionHeader(
                    region = regionSection.region,
                    packageCount = regionSection.groups.sumOf { it.packageCount },
                )
            }
            items(
                items = regionSection.groups,
                key = { group -> "${group.region}-${group.expiresOn}" },
            ) { group ->
                PackageGroupRow(group = group)
            }
        }
    }
}

@Composable
private fun RegionHeader(region: String, packageCount: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 8.dp),
    ) {
        Text(
            text = region,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier.weight(1f),
        )
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
        ) {
            Text(
                text = "$packageCount 个包",
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

/** 一行 = 同一区域、同一到期日的合并结果（User Story 17）；剩余量给大数字与比例条。 */
@Composable
private fun PackageGroupRow(group: PackageGroup) {
    val extended = LocalExtendedColors.current
    val fraction = if (group.totalBytes > 0L) {
        (group.remainingBytes.toFloat() / group.totalBytes.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val barColor = when {
        group.notYetEffective -> extended.warning
        fraction <= 0.2f -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }
    AppCard(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "到期 ${formatPackageDate(group.expiresOn)}",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                when {
                    group.notYetEffective -> StatusTag(
                        text = "未生效",
                        color = extended.warning,
                    )

                    group.remainingBytes == 0L -> StatusTag(
                        text = "已用尽",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(verticalAlignment = Alignment.Bottom) {
                MetricText(text = formatBytes(group.remainingBytes))
                Text(
                    text = "  / 总量 ${formatBytes(group.totalBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 3.dp),
                )
            }
            PackageRemainBar(fraction = fraction, color = barColor)
            Text(
                text = buildString {
                    append("${group.packageCount} 个包")
                    if (group.notYetEffective) {
                        append(" · 生效于 ${formatPackageDate(group.effectiveAt)}，暂不可抵扣")
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatusTag(text: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.12f),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
}

@Composable
private fun PackageDetailError(
    failure: ApiFailureKind,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "资源包数据取不到：${failure.label()}",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = failure.actionHint(),
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "其他账号不受影响；可稍后重试。",
            modifier = Modifier.padding(top = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(
            onClick = onRetry,
            modifier = Modifier.padding(top = 24.dp),
        ) {
            Text("重试")
        }
    }
}
