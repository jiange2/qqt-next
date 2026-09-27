package com.qqt.music.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qqt.music.ui.theme.DangerRed
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** 同列行的左滑展开互斥状态：同一时刻只允许一行处于展开态，滑开新的一行会收起上一行 */
class RowSwipeRevealState {
    internal var openKey by mutableStateOf<Any?>(null)
        private set

    internal fun open(key: Any) {
        openKey = key
    }

    /** 收起当前展开的行；列表滚动时由所在页面调用 */
    fun close() {
        openKey = null
    }
}

/** 行内左滑配置：指向按钮文案与行为；同一 [RowSwipeRevealState] 下的行互斥展开 */
class RowSwipeAction(
    internal val state: RowSwipeRevealState,
    val label: String,
    val onAction: () -> Unit,
)

/**
 * 行内左滑露出操作按钮（ADR 0018）：手指从右向左拖动，行内容左移露出行尾按钮，松手过半即停住，
 * 点按钮执行操作；点行内其它区域收起。无 [swipe] 时内容原样渲染，左滑无反应。
 */
@Composable
internal fun SwipeRevealRow(
    rowKey: Any,
    swipe: RowSwipeAction?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (swipe == null) {
        Box(modifier = modifier.fillMaxWidth()) { content() }
        return
    }
    val actionWidth = 88.dp
    val actionWidthPx = with(LocalDensity.current) { actionWidth.toPx() }
    val offsetX = remember(rowKey) { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val isOpen = swipe.state.openKey == rowKey

    LaunchedEffect(isOpen, actionWidthPx) {
        offsetX.animateTo(if (isOpen) -actionWidthPx else 0f, tween(durationMillis = 180))
    }

    Box(modifier = modifier.fillMaxWidth()) {
        // 贴行尾的操作按钮：平时被行内容整幅盖住，左滑才露出
        Box(modifier = Modifier.matchParentSize(), contentAlignment = Alignment.CenterEnd) {
            Box(
                modifier = Modifier
                    .width(actionWidth)
                    .fillMaxHeight()
                    .background(DangerRed)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        swipe.state.close()
                        swipe.onAction()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(text = swipe.label, color = Color.White, fontSize = 14.sp)
            }
        }

        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                // 行内容须不透明，否则露出的按钮会从其下方透出
                .background(Color.White)
                .pointerInput(rowKey, actionWidthPx) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val shouldOpen = offsetX.value <= -actionWidthPx / 2f
                            if (shouldOpen) {
                                swipe.state.open(rowKey)
                            } else if (swipe.state.openKey == rowKey) {
                                swipe.state.close()
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            scope.launch { offsetX.snapTo((offsetX.value + dragAmount).coerceIn(-actionWidthPx, 0f)) }
                        },
                    )
                },
        ) {
            content()
            if (isOpen) {
                // 展开态下点行内其它区域只收起，不触发点歌
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .pointerInput(rowKey) { detectTapGestures { swipe.state.close() } }
                )
            }
        }
    }
}
