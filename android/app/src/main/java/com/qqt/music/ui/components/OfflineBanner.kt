package com.qqt.music.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.qqt.music.data.local.NetworkMonitor
import com.qqt.music.ui.theme.BrandOrangeSoft
import com.qqt.music.ui.theme.InkPrimary

/**
 * 离线/故障横幅（ADR 0012、0017）：断网或服务器不可达时在内容区顶部展开常显，
 * 告知当前展示的是响应快照的历史内容；恢复后自动收起。两因并存显示断网文案
 * （硬信号优先）；文案不出现「缓存/快照」字样（UI 标签与域内术语分离）。
 */
@Composable
fun OfflineBanner(modifier: Modifier = Modifier) {
    val offline by NetworkMonitor.offline.collectAsState()
    val serverUnreachable by NetworkMonitor.serverUnreachable.collectAsState()
    AnimatedVisibility(
        visible = offline || serverUnreachable,
        enter = expandVertically(),
        exit = shrinkVertically(),
        modifier = modifier,
    ) {
        Surface(color = BrandOrangeSoft) {
            Text(
                text = if (offline) "当前离线，展示最近一次内容"
                else "服务暂不可用，展示最近一次内容",
                style = MaterialTheme.typography.bodySmall,
                color = InkPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
            )
        }
    }
}
