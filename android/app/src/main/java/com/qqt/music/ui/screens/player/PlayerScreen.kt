package com.qqt.music.ui.screens.player

import android.app.Activity
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import coil.compose.AsyncImage
import com.qqt.music.ui.theme.BrandOrange
import com.qqt.music.ui.theme.BrandOrangeDeep
import com.qqt.music.ui.theme.PlayerBgBottom
import com.qqt.music.ui.theme.PlayerBgTop
import com.qqt.music.ui.theme.PlayerOnDark
import com.qqt.music.ui.theme.PlayerOnDarkFaint
import com.qqt.music.ui.theme.PlayerOnDarkSub
import com.qqt.music.ui.theme.PlayerTrack
import com.qqt.music.ui.theme.brandBrush
import com.qqt.music.viewmodel.PlayerViewModel
import kotlin.math.abs

/**
 * 全屏播放器：深色沉浸氛围（暖黑渐变），黑胶唱片封面随播放旋转，
 * 播放键为品牌渐变光晕大圆钮。状态栏图标在进入本页时切换为浅色。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    playerViewModel: PlayerViewModel,
    onBackClick: () -> Unit = {},
) {
    val currentSong by playerViewModel.currentSong.collectAsState()
    val isPlaying by playerViewModel.isPlaying.collectAsState()
    val currentPosition by playerViewModel.currentPosition.collectAsState()
    val duration by playerViewModel.duration.collectAsState()
    var isFavorite by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    // 深色页面上状态栏图标切为浅色，离开时还原
    val view = LocalView.current
    if (!view.isInEditMode) {
        DisposableEffect(Unit) {
            val window = (view.context as Activity).window
            val controller = WindowCompat.getInsetsController(window, view)
            val previous = controller.isAppearanceLightStatusBars
            controller.isAppearanceLightStatusBars = false
            onDispose { controller.isAppearanceLightStatusBars = previous }
        }
    }

    // 旋转动画
    val infiniteTransition = rememberInfiniteTransition(label = "album-rotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 20000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "rotation",
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(PlayerBgTop, PlayerBgBottom))),
    ) {
        // Top bar with song info
        TopAppBar(
            title = {
                currentSong?.let { song ->
                    Column(
                        modifier = Modifier.fillMaxWidth(0.65f),
                    ) {
                        Text(
                            text = song.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = PlayerOnDark,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = song.artist.ifBlank { "佚名" },
                            fontSize = 12.sp,
                            color = PlayerOnDarkSub,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            },
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = PlayerOnDark,
                    )
                }
            },
            actions = {
                IconButton(onClick = { isFavorite = !isFavorite }) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (isFavorite) BrandOrange else PlayerOnDark,
                    )
                }
                Box {
                    IconButton(onClick = { showMenu = !showMenu }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More", tint = PlayerOnDark)
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("分享") },
                            onClick = { showMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("下载") },
                            onClick = { showMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("评分") },
                            onClick = { showMenu = false }
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
            ),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {

        // Album art：黑胶唱片（外圈唱片 + 旋转封面 + 中心纸标签）
        currentSong?.let { song ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 28.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(280.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF0C0906))
                        .border(1.dp, Color.White.copy(alpha = 0.10f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    AsyncImage(
                        model = song.thumbnailBig,
                        contentDescription = song.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(238.dp)
                            .rotate(if (isPlaying) rotation else 0f)
                            .clip(CircleShape),
                    )
                    // 黑胶中心纸标签
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(PlayerBgTop)
                            .border(1.dp, Color.White.copy(alpha = 0.14f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(BrandOrange),
                        )
                    }
                }
            }
        }

        // Song info
        currentSong?.let { song ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
            ) {
                Text(
                    text = song.title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = PlayerOnDark,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = song.artist.ifBlank { "佚名" },
                    fontSize = 14.sp,
                    color = PlayerOnDarkSub,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // Progress bar with time display
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 12.dp),
        ) {
            Slider(
                value = if (duration > 0) currentPosition.toFloat() else 0f,
                onValueChange = { playerViewModel.seekTo(it.toLong()) },
                valueRange = 0f..maxOf(duration.toFloat(), 1f),
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = BrandOrange,
                    activeTrackColor = BrandOrange,
                    inactiveTrackColor = PlayerTrack,
                ),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = formatTime(currentPosition),
                    fontSize = 12.sp,
                    color = PlayerOnDarkFaint,
                )
                Text(
                    text = formatTime(duration),
                    fontSize = 12.sp,
                    color = PlayerOnDarkFaint,
                )
            }
        }

        // First row of controls: shuffle, previous, play/pause, next, repeat
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 20.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            var isShuffleActive by remember { mutableStateOf(false) }
            var repeatMode by remember { mutableStateOf(MusicRepeatMode.NONE) } // NONE, ONE, ALL

            // Shuffle button
            IconButton(
                onClick = { isShuffleActive = !isShuffleActive },
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    Icons.Default.Shuffle,
                    contentDescription = "Shuffle",
                    tint = if (isShuffleActive) BrandOrange else PlayerOnDarkSub,
                    modifier = Modifier.size(22.dp),
                )
            }

            // Previous
            IconButton(
                onClick = { playerViewModel.skipPrev() },
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    Icons.Default.SkipPrevious,
                    contentDescription = "Previous",
                    tint = PlayerOnDark,
                    modifier = Modifier.size(30.dp),
                )
            }

            // Play/Pause：品牌渐变 + 橙色光晕
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .shadow(
                        elevation = 14.dp,
                        shape = CircleShape,
                        ambientColor = BrandOrangeDeep.copy(alpha = 0.55f),
                        spotColor = BrandOrangeDeep.copy(alpha = 0.55f),
                    )
                    .clip(CircleShape)
                    .background(brandBrush())
                    .clickable { playerViewModel.togglePlayPause() },
                contentAlignment = Alignment.Center,
            ) {
                Crossfade(targetState = isPlaying, label = "playPause") { playing ->
                    Icon(
                        imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp),
                    )
                }
            }

            // Next
            IconButton(
                onClick = { playerViewModel.skipNext() },
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    Icons.Default.SkipNext,
                    contentDescription = "Next",
                    tint = PlayerOnDark,
                    modifier = Modifier.size(30.dp),
                )
            }

            // Repeat button
            IconButton(
                onClick = {
                    repeatMode = when (repeatMode) {
                        MusicRepeatMode.NONE -> MusicRepeatMode.ALL
                        MusicRepeatMode.ALL -> MusicRepeatMode.ONE
                        MusicRepeatMode.ONE -> MusicRepeatMode.NONE
                    }
                },
                modifier = Modifier.size(48.dp),
            ) {
                val (icon, color) = when (repeatMode) {
                    MusicRepeatMode.NONE -> Icons.Default.Repeat to PlayerOnDarkSub
                    MusicRepeatMode.ALL -> Icons.Default.Repeat to BrandOrange
                    MusicRepeatMode.ONE -> Icons.Default.RepeatOne to BrandOrange
                }
                Icon(
                    icon,
                    contentDescription = "Repeat",
                    tint = color,
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        // Second row of controls: add to playlist, share, download, rate, volume
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            var showVolumeSlider by remember { mutableStateOf(false) }
            var volumeLevel by remember { mutableStateOf(80f) }

            // Add to playlist
            PlayerActionItem(
                icon = Icons.Default.AddCircle,
                label = "播放列表",
                onClick = {},
                modifier = Modifier.weight(1f),
            )

            // Share
            PlayerActionItem(
                icon = Icons.Default.Share,
                label = "分享",
                onClick = {},
                modifier = Modifier.weight(1f),
            )

            // Download
            PlayerActionItem(
                icon = Icons.Default.FileDownload,
                label = "下载",
                onClick = {},
                modifier = Modifier.weight(1f),
            )

            // Rate
            PlayerActionItem(
                icon = Icons.Default.Star,
                label = "评分",
                onClick = {},
                modifier = Modifier.weight(1f),
            )

            // Volume control
            PlayerActionItem(
                icon = Icons.Default.VolumeUp,
                label = "音量",
                onClick = { showVolumeSlider = !showVolumeSlider },
                modifier = Modifier.weight(1f),
            )

            if (showVolumeSlider) {
                Slider(
                    value = volumeLevel,
                    onValueChange = { volumeLevel = it },
                    valueRange = 0f..100f,
                    modifier = Modifier
                        .width(100.dp)
                        .padding(8.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = BrandOrange,
                        activeTrackColor = BrandOrange,
                        inactiveTrackColor = PlayerTrack,
                    ),
                )
            }
        }
        }
    }
}

@Composable
private fun PlayerActionItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(8.dp),
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = PlayerOnDarkSub,
            modifier = Modifier.size(26.dp),
        )
        Text(
            label,
            fontSize = 11.sp,
            color = PlayerOnDarkFaint,
            modifier = Modifier.padding(top = 5.dp),
        )
    }
}

// Helper function to format time in mm:ss format
private fun formatTime(millis: Long): String {
    val totalSeconds = abs(millis / 1000)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}

private enum class MusicRepeatMode {
    NONE, ALL, ONE
}
