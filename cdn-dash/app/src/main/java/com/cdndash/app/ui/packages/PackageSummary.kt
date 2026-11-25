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
import com.cdndash.app.domain.formatBytes
import com.cdndash.app.domain.formatPackageDate

/** 卡片摘要最多展示的组数；其余折叠成「还有 N 组」，避免几十个包把卡片撑爆（User Story 17）。 */
const val PACKAGE_SUMMARY_GROUP_LIMIT: Int = 2

/**
 * 看板账号卡片上的资源包余量摘要（票据 05、User Story 15/18/19）。
 *
 * 每组都带区域名，**区域之间不做合计**（GLOSSARY「区域」：不同区域不可互相抵扣）；
 * 只列出前几组，其余给「还有 N 组」。未生效的组标注「未生效」且可用余量按 0 显示。
 *
 * @param groups 余量视图；null 表示本次还没取到（区别于空列表「没有 CDN 流量包」）。
 * @param failure 资源包模块的归一错误；与今日累计的错误互不影响。
 */
@Composable
fun PackageSummarySection(
    groups: List<PackageGroup>?,
    failure: ApiFailureKind?,
    isRefreshing: Boolean,
    modifier: Modifier = Modifier,
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
                    text = failure.packageFailureLabel(),
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
                groups.take(PACKAGE_SUMMARY_GROUP_LIMIT).forEach { group ->
                    Text(
                        text = group.summaryLine(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                val hiddenGroups = groups.size - PACKAGE_SUMMARY_GROUP_LIMIT
                if (hiddenGroups > 0) {
                    PackageHint("还有 $hiddenGroups 组")
                }
            }
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

/** 资源包模块的三类错误文案（与今日累计的文案一致，卡片与明细页共用）。 */
internal fun ApiFailureKind.packageFailureLabel(): String = when (this) {
    ApiFailureKind.CREDENTIAL_INVALID -> "凭证失效"
    ApiFailureKind.PERMISSION_DENIED -> "权限不足"
    ApiFailureKind.NETWORK -> "网络错误"
}
