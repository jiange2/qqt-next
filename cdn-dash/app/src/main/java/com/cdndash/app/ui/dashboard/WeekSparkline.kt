package com.cdndash.app.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.cdndash.app.domain.DayTrafficPoint
import com.cdndash.app.domain.SETTLED_WEEK_DAY_COUNT

private val SPARKLINE_HEIGHT = 36.dp
private val SPARKLINE_STROKE = 2.dp
private val SPARKLINE_DOT_RADIUS = 2.5.dp

/**
 * 最近 7 天走势折线（Canvas 自绘，不引入图表库）：一天一个点，固定 7 个点位。
 *
 * - [days] 应是 [SETTLED_WEEK_DAY_COUNT] 个按日升序的已结算日点
 *   （[com.cdndash.app.domain.aggregateSettledDays] 的输出）；
 * - 空数据（或点数不是 7）仍按 7 个零点画一条平线，保持「恒 7 点」结构，不崩溃；
 * - 线条按最大值归一化；全零时贴底画平线，不出现除零。
 */
@Composable
fun WeekSparkline(
    days: List<DayTrafficPoint>,
    modifier: Modifier = Modifier,
) {
    val lineColor = MaterialTheme.colorScheme.primary
    val baselineColor = MaterialTheme.colorScheme.onSurfaceVariant
    val values = LongArray(SETTLED_WEEK_DAY_COUNT) { index ->
        // 点数不足/超出的一律视为 0：结构恒定，不因数据缺日而少画点位。
        days.getOrNull(index)?.bytes ?: 0L
    }
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(SPARKLINE_HEIGHT)
            .semantics { contentDescription = "最近 7 天走势折线" },
    ) {
        val strokeWidth = SPARKLINE_STROKE.toPx()
        val maxBytes = values.maxOrNull() ?: 0L
        val bottomY = size.height - strokeWidth
        val usableHeight = (size.height - strokeWidth).coerceAtLeast(1f)
        fun yFor(bytes: Long): Float =
            if (maxBytes <= 0L) bottomY
            else bottomY - (bytes.toFloat() / maxBytes) * usableHeight

        // 贴底基线：全零或数据很小时仍能看出这是折线的底。
        drawLine(
            color = baselineColor.copy(alpha = 0.25f),
            start = Offset(0f, bottomY),
            end = Offset(size.width, bottomY),
            strokeWidth = 1.dp.toPx(),
        )

        val stepX = size.width / (SETTLED_WEEK_DAY_COUNT - 1)
        val path = Path()
        values.forEachIndexed { index, bytes ->
            val point = Offset(x = stepX * index, y = yFor(bytes))
            if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
        }
        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(
                width = strokeWidth,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
        // 一天一个点：每个日点画一个小圆，空数据时 7 个点也都在基线上。
        values.forEachIndexed { index, bytes ->
            drawCircle(
                color = lineColor,
                radius = SPARKLINE_DOT_RADIUS.toPx(),
                center = Offset(x = stepX * index, y = yFor(bytes)),
            )
        }
    }
}
