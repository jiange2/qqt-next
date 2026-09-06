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
/** 导航未选中：底部导航栏等可交互导航元素，弱于选中橙但须保证小字可读 */
val InkNav = Color(0xFF6E665D)
/** 占位 / 禁用 */
val InkFaint = Color(0xFFC9C1B9)
/** 分隔线 / 描边 */
val Hairline = Color(0xFFF0EAE4)
/** 图片占位底 */
val PlaceholderBg = Color(0xFFF2EDE8)

// ── 点缀 ─────────────────────────────────────────────
val StarGold = Color(0xFFFFB93E)

// ── 状态色 ───────────────────────────────────────────
/** 已下载态：下载完成后播放器下载按钮的绿色 */
val DownloadedGreen = Color(0xFF4CAF50)

// ── 全屏播放器：亮色氛围（背景为 drawable/player_bg 极光图 + 玻璃蒙板）──
/** 深藏青：标题 / 播放键 / 上下曲 */
val PlayerNavy = Color(0xFF333B5E)
/** 播放器次级图标与文字灰 */
val PlayerIconGray = Color(0xFF9290A2)
/** 进度轨道底色 */
val PlayerTrack = Color(0xFFD9D5E4)
