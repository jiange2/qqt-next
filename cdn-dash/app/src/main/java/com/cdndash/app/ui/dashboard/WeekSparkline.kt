package com.cdndash.app.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.cdndash.app.domain.DayTrafficPoint
import com.cdndash.app.domain.SETTLED_WEEK_DAY_COUNT

private val SPARKLINE_HEIGHT = 44.dp
private val SPARKLINE_STROKE = 2.5.dp
private val SPARKLINE_DOT_RADIUS = 3.dp

/**
 * 最近 7 天走势折线（Canvas 自绘，不引入图表库）：一天一个点，固定 7 个点位。
 *
 * - [days] 应是 [SETTLED_WEEK_DAY_COUNT] 个按日升序的已结算日点
 *   （[com.cdndash.app.domain.aggregateSettledDays] 的输出）；
 * - 空数据（或点数不是 7）仍按 7 个零点画一条平线，保持「恒 7 点」结构，不崩溃；
 * - 线条按最大值归一化；全零时贴底画平线，不出现除零；
 * - Catmull-Rom 转贝塞尔做平滑，线下垫一层品牌色渐变面积。
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
        val dotRadius = SPARKLINE_DOT_RADIUS.toPx()
        val maxBytes = values.maxOrNull() ?: 0L
        val bottomY = size.height - strokeWidth
        val usableHeight = (size.height - strokeWidth - dotRadius).coerceAtLeast(1f)
        fun yFor(bytes: Long): Float =
            if (maxBytes <= 0L) bottomY
            else bottomY - (bytes.toFloat() / maxBytes) * usableHeight

        // 贴底基线：全零或数据很小时仍能看出这是折线的底。
        drawLine(
            color = baselineColor.copy(alpha = 0.2f),
            start = Offset(0f, bottomY),
            end = Offset(size.width, bottomY),
            strokeWidth = 1.dp.toPx(),
        )

        val stepX = size.width / (SETTLED_WEEK_DAY_COUNT - 1)
        val points = Array(SETTLED_WEEK_DAY_COUNT) { index ->
            Offset(x = stepX * index, y = yFor(values[index]))
        }
        val path = smoothPath(points)

        // 渐变面积：沿路径向下闭合到基线，顶部着色、底部透明。
        val areaPath = Path().apply {
            addPath(path)
            lineTo(points.last().x, bottomY)
            lineTo(points.first().x, bottomY)
            close()
        }
        drawPath(
            path = areaPath,
            brush = Brush.verticalGradient(
                colors = listOf(lineColor.copy(alpha = 0.18f), Color.Transparent),
                startY = 0f,
                endY = bottomY,
            ),
        )
        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(
                width = strokeWidth,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
        // 一天一个点：白芯圆点让点位在面积填充上仍然清晰。
        points.forEach { point ->
            drawCircle(color = lineColor, radius = dotRadius, center = point)
        }
    }
}

/** Catmull-Rom 样条转三次贝塞尔：过每个数据点的平滑曲线，端点用最近点代替。 */
private fun smoothPath(points: Array<Offset>): Path {
    val path = Path()
    path.moveTo(points.first().x, points.first().y)
    for (i in 0 until points.size - 1) {
        val p0 = points[(i - 1).coerceAtLeast(0)]
        val p1 = points[i]
        val p2 = points[i + 1]
        val p3 = points[(i + 2).coerceAtMost(points.size - 1)]
        // 控制点 y 夹回画布，避免平滑过冲把线画出基线以下。
        val top = 0f
        val bottom = points.maxOf { it.y }
        path.cubicTo(
            x1 = p1.x + (p2.x - p0.x) / 6f,
            y1 = (p1.y + (p2.y - p0.y) / 6f).coerceIn(top, bottom),
            x2 = p2.x - (p3.x - p1.x) / 6f,
            y2 = (p2.y - (p3.y - p1.y) / 6f).coerceIn(top, bottom),
            x3 = p2.x,
            y3 = p2.y,
        )
    }
    return path
}
