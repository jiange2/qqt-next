package com.qqt.music.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// ── 品牌色 ─────────────────────────────────────────────
/** 主强调色：珊瑚橙，用于播放键、选中态、关键交互 */
val BrandOrange = Color(0xFFFF5A2D)
/** 深橙：按压态 / 渐变收尾 */
val BrandOrangeDeep = Color(0xFFE8431B)
/** 亮橙：渐变起点 */
val BrandOrangeTint = Color(0xFFFF8A4D)
/** 浅橙底：选中态容器 */
val BrandOrangeSoft = Color(0xFFFFEFE8)
/** 品牌渐变（FAB、播放键、抽屉头图等视觉焦点） */
val BrandGradient = listOf(BrandOrangeTint, BrandOrange)
fun brandBrush() = Brush.linearGradient(BrandGradient)

// ── 中性色（暖调）─────────────────────────────────────
/** 页面背景：暖白，比冷灰更衬橙色 */
val WarmBackground = Color(0xFFFAF7F4)
/** 卡片表面 */
val WarmSurface = Color(0xFFFFFFFF)
/** 主文字：暖黑 */
val InkPrimary = Color(0xFF211D19)
/** 次文字 */
val InkSecondary = Color(0xFF8B847C)
/** 占位 / 禁用 */
val InkFaint = Color(0xFFC9C1B9)
/** 分隔线 / 描边 */
val Hairline = Color(0xFFF0EAE4)
/** 图片占位底 */
val PlaceholderBg = Color(0xFFF2EDE8)

// ── 点缀 ─────────────────────────────────────────────
val StarGold = Color(0xFFFFB93E)

// ── 全屏播放器：深色氛围 ───────────────────────────────
val PlayerBgTop = Color(0xFF2A1B12)
val PlayerBgBottom = Color(0xFF130E0A)
val PlayerOnDark = Color(0xFFF7F1EA)
val PlayerOnDarkSub = Color(0xB3F7F1EA)   // 70%
val PlayerOnDarkFaint = Color(0x80F7F1EA) // 50%
val PlayerTrack = Color(0x33F7F1EA)       // 进度轨道 20%
