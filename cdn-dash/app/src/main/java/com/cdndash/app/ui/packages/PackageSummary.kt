package com.cdndash.app.ui.packages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cdndash.app.data.aliyun.ApiFailureKind
import com.cdndash.app.domain.PackageGroup
import com.cdndash.app.domain.formatBeijingHourMinute
import com.cdndash.app.domain.formatBytes
import com.cdndash.app.domain.formatPackageDate
import com.cdndash.app.domain.selectPackageSummaryGroups
import com.cdndash.app.ui.dashboard.label

/** 卡片摘要最多展示的组数（每个区域至少一组，见 [selectPackageSummaryGroups]）。 */
const val PACKAGE_SUMMARY_GROUP_LIMIT: Int = 2

/**
 * 看板账号卡片上的资源包余量摘要（票据 05、User Story 15/18/19）。
 *
 * 每组都带区域名，**区域之间不做合计**（GLOSSARY「区域」：不同区域不可互相抵扣）；
 * 挑组时每个区域至少出现一组，其余折叠成「还有 N 组」，不能因为全局取前 2 组而隐藏另一个区域。
 * 未生效的组标注「未生效」且可用余量按 0 显示。
 *
 * @param groups 余量视图；null 表示本次还没取到（区别于空列表「没有 CDN 流量包」）。
 * @param failure 资源包模块的归一错误；与今日累计的错误互不影响。
 * @param snapshotFetchedAtMillis 非 null 表示 [groups] 来自上次成功快照（离线展示），
 * 值是该快照的抓取时刻，与今日/7 天一样标「数据时间 HH:mm（离线）」。
 */
@Composable
fun PackageSummarySection(
    groups: List<PackageGroup>?,
    failure: ApiFailureKind?,
    isRefreshing: Boolean,
    modifier: Modifier = Modifier,
    snapshotFetchedAtMillis: Long? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "资源包余量",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (failure != null) {
                Text(
                    text = failure.label(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        when {
            groups == null && failure != null -> PackageHint("资源包数据暂时取不到")
            groups == null && isRefreshing -> PackageHint("加载中…")
            groups == null -> PackageHint("暂无数据")
            groups.isEmpty() -> PackageHint("没有 CDN 流量包")
            else -> {
                val shown = selectPackageSummaryGroups(groups, PACKAGE_SUMMARY_GROUP_LIMIT)
                shown.forEach { group ->
                    Text(
                        text = group.summaryLine(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                val hiddenGroups = groups.size - shown.size
                if (hiddenGroups > 0) {
                    PackageHint("还有 $hiddenGroups 组")
                }
            }
        }
        if (groups != null && snapshotFetchedAtMillis != null) {
            PackageHint("数据时间 ${formatBeijingHourMinute(snapshotFetchedAtMillis)}（离线）")
        }
    }
}

@Composable
private fun PackageHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun PackageGroup.summaryLine(): String {
    val parts = buildList {
        add(region)
        add("${formatPackageDate(expiresOn)} 到期")
        add("剩 ${formatBytes(usableRemainingBytes)} / ${formatBytes(totalBytes)}")
        if (packageCount > 1) add("$packageCount 个包")
        if (notYetEffective) add("未生效")
    }
    return parts.joinToString(" · ")
}
