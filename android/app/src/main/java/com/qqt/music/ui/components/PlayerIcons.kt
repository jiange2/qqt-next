package com.qqt.music.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * 播放器页手绘矢量图标集：24 视口、1.8 圆头描边的细线风格。
 * 所有路径用黑色定义，实际颜色由调用方 tint 决定，保证任意密度下清晰。
 */
object PlayerIcons {

    private fun icon(name: String, builder: ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply(builder).build()

    /** 圆头描边子路径 */
    private fun ImageVector.Builder.strokePath(pathData: String, width: Float = 1.8f) =
        addPath(
            pathData = addPathNodes(pathData),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = width,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )

    /** 实填充子路径；附带同色描边可让转角圆润（用于三角箭头等） */
    private fun ImageVector.Builder.fillPath(pathData: String, strokeWidth: Float = 0f) =
        addPath(
            pathData = addPathNodes(pathData),
            fill = SolidColor(Color.Black),
            stroke = if (strokeWidth > 0f) SolidColor(Color.Black) else null,
            strokeLineWidth = strokeWidth,
            strokeLineJoin = StrokeJoin.Round,
        )

    private const val HEART =
        "M12 20.5C6.6 16.8 3.6 13.7 3.6 10.2C3.6 7.4 5.8 5.3 8.2 5.3C9.6 5.3 11 6 12 7.2" +
        "C13 6 14.4 5.3 15.8 5.3C18.2 5.3 20.4 7.4 20.4 10.2C20.4 13.7 17.4 16.8 12 20.5Z"

    /** 喜欢（描边心形） */
    val Like by lazy { icon("Like") { strokePath(HEART) } }

    /** 喜欢·已选（实心心形） */
    val LikeFilled by lazy { icon("LikeFilled") { fillPath(HEART) } }

    /** 均衡器（双滑杆） */
    val Equalizer by lazy {
        icon("Equalizer") {
            strokePath(
                "M8 4V6.6M8 11.4V20M16 4V12.6M16 17.4V20" +
                    "M10.4 9A2.4 2.4 0 1 1 5.6 9A2.4 2.4 0 1 1 10.4 9Z" +
                    "M18.4 15A2.4 2.4 0 1 1 13.6 15A2.4 2.4 0 1 1 18.4 15Z"
            )
        }
    }

    /** 下载（箭头入盘） */
    val Download by lazy {
        icon("Download") {
            strokePath(
                "M12 4.5V12.5M8.6 9.7L12 13.1L15.4 9.7" +
                    "M5 15.5V17.5A2.3 2.3 0 0 0 7.3 19.8H16.7A2.3 2.3 0 0 0 19 17.5V15.5"
            )
        }
    }

    /** 更多（竖排三点） */
    val More by lazy {
        icon("More") {
            fillPath(
                "M13.4 5.5A1.4 1.4 0 1 1 10.6 5.5A1.4 1.4 0 1 1 13.4 5.5Z" +
                    "M13.4 12A1.4 1.4 0 1 1 10.6 12A1.4 1.4 0 1 1 13.4 12Z" +
                    "M13.4 18.5A1.4 1.4 0 1 1 10.6 18.5A1.4 1.4 0 1 1 13.4 18.5Z"
            )
        }
    }

    /** 随机播放（交叉乱序箭头） */
    val Shuffle by lazy {
        icon("Shuffle") {
            strokePath("M17.8 4.8L20.8 7.8L17.8 10.8")
            strokePath(
                "M3.2 7.8H6.6C8.2 7.8 9.3 8.5 10.3 9.9L13.7 14.1" +
                    "C14.7 15.5 15.8 16.2 17.4 16.2H20.8"
            )
            strokePath("M17.8 13.2L20.8 16.2L17.8 19.2")
            strokePath(
                "M3.2 16.2H6.6C8.2 16.2 9.3 15.5 10.3 14.1L13.7 9.9" +
                    "C14.7 8.5 15.8 7.8 17.4 7.8H20.8"
            )
        }
    }

    /** 循环（双箭头环绕；播放模式「顺序播放」） */
    val Repeat by lazy {
        icon("Repeat") {
            strokePath(
                "M17 2.5L21 6.5L17 10.5M3 11.5V9.5A3 3 0 0 1 6 6.5H21" +
                    "M7 21.5L3 17.5L7 13.5M21 12.5V14.5A3 3 0 0 1 18 17.5H3"
            )
        }
    }

    /** 单曲循环（循环环绕 + 中央 1） */
    val RepeatOne by lazy {
        icon("RepeatOne") {
            strokePath(
                "M17 2.5L21 6.5L17 10.5M3 11.5V9.5A3 3 0 0 1 6 6.5H21" +
                    "M7 21.5L3 17.5L7 13.5M21 12.5V14.5A3 3 0 0 1 18 17.5H3"
            )
            strokePath("M11.2 10.8L12.4 10.1V14.2")
        }
    }

    /** 上一首（竖条 + 左三角） */
    val Previous by lazy {
        icon("Previous") {
            fillPath("M18.5 6.8L10 12L18.5 17.2Z", strokeWidth = 1.5f)
            strokePath("M6 5.5V18.5", 2.2f)
        }
    }

    /** 下一首（右三角 + 竖条） */
    val Next by lazy {
        icon("Next") {
            fillPath("M5.5 6.8L14 12L5.5 17.2Z", strokeWidth = 1.5f)
            strokePath("M18 5.5V18.5", 2.2f)
        }
    }

    /** 播放队列（横线 + 播放三角；主控行队列入口） */
    val Playlist by lazy {
        icon("Playlist") {
            strokePath("M4 6.5H20M4 11.5H20M4 16.5H11.5")
            fillPath("M15.3 14.2L20.5 17.5L15.3 20.8Z", strokeWidth = 1.2f)
        }
    }

    /** 加入歌单（三条横线 + 右侧加号；与主控行「播放队列」同基线异尾巴——加号=加入，三角=打开） */
    val PlaylistAdd by lazy {
        icon("PlaylistAdd") {
            strokePath("M4 6.5H20M4 11.5H20M4 16.5H11")
            strokePath("M17.5 13.9V19.1M14.9 16.5H20.1")
        }
    }

    /** 刷新（环形箭头） */
    val Refresh by lazy {
        icon("Refresh") {
            strokePath("M19.8 12A7.8 7.8 0 1 1 12 4.2M8.9 2.2L13.2 4.5L9.3 7.8")
        }
    }

    /** 评分（五角星；更多菜单「评分」入口） */
    val Star by lazy {
        icon("Star") {
            strokePath(
                "M12 4.2L13.8 9.6L19.4 9.6L14.9 12.9L16.6 18.3L12 15L7.4 18.3L9.1 12.9L4.6 9.6L10.2 9.6Z"
            )
        }
    }
}
