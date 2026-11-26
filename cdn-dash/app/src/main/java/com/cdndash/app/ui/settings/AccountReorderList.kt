package com.cdndash.app.ui.settings

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.cdndash.app.domain.Account

/**
 * 设置页的账号行列表：启用开关、删除入口与左侧拖拽把手。
 *
 * 排序完全用 Compose 自带手势实现（不引入第三方库）：长按把手开始拖动，
 * 累计位移越过相邻行高的一半时把「上移/下移一格」交给调用方持久化。
 * 这样列表重排由 ViewModel/AccountStore 驱动，重启后顺序仍来自已保存的 sortOrder。
 */
@Composable
fun AccountReorderList(
    accounts: List<Account>,
    onToggleEnabled: (Account, Boolean) -> Unit,
    onDelete: (Account) -> Unit,
    onMove: (fromIndex: Int, toIndex: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentAccounts by rememberUpdatedState(accounts)
    val itemHeights = remember { mutableMapOf<String, Int>() }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        accounts.forEach { account ->
            key(account.id) {
                val dragging = draggingId == account.id
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onSizeChanged { itemHeights[account.id] = it.height }
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer {
                            translationY = if (dragging) dragOffset else 0f
                        },
                ) {
                    Row(
                        modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(48.dp)
                                .semantics { contentDescription = "拖拽排序 ${account.name}" }
                                .pointerInput(account.id) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            draggingId = account.id
                                            dragOffset = 0f
                                        },
                                        onDragCancel = {
                                            draggingId = null
                                            dragOffset = 0f
                                        },
                                        onDragEnd = {
                                            draggingId = null
                                            dragOffset = 0f
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            dragOffset += dragAmount.y
                                            val height = itemHeights[account.id]
                                                ?: return@detectDragGesturesAfterLongPress
                                            if (height <= 0) {
                                                return@detectDragGesturesAfterLongPress
                                            }
                                            val list = currentAccounts
                                            val currentIndex =
                                                list.indexOfFirst { it.id == account.id }
                                            if (currentIndex < 0) {
                                                return@detectDragGesturesAfterLongPress
                                            }
                                            val threshold = height / 2f
                                            when {
                                                dragOffset > threshold &&
                                                    currentIndex < list.lastIndex -> {
                                                    onMove(currentIndex, currentIndex + 1)
                                                    dragOffset -= height
                                                }

                                                dragOffset < -threshold &&
                                                    currentIndex > 0 -> {
                                                    onMove(currentIndex, currentIndex - 1)
                                                    dragOffset += height
                                                }
                                            }
                                        },
                                    )
                                },
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DragHandle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 4.dp),
                        ) {
                            Text(
                                text = account.name,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = if (account.enabled) "已启用" else "已停用",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = account.enabled,
                            onCheckedChange = { enabled -> onToggleEnabled(account, enabled) },
                        )
                        IconButton(onClick = { onDelete(account) }) {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = "删除 ${account.name}",
                            )
                        }
                    }
                }
            }
        }
    }
}
