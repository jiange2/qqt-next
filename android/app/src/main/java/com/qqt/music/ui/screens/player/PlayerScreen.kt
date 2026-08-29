package com.qqt.music.ui.screens.player

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.qqt.music.ui.theme.OrangePrimary
import com.qqt.music.viewmodel.PlayerViewModel
import kotlin.math.abs

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
            .background(Color.White),
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
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = song.artist.ifBlank { "佚名" },
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.8f),
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
                        tint = Color.White,
                    )
                }
            },
            actions = {
                IconButton(onClick = { isFavorite = !isFavorite }) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = Color.White,
                    )
                }
                Box {
                    IconButton(onClick = { showMenu = !showMenu }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More", tint = Color.White)
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
                containerColor = OrangePrimary,
            ),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {

        // Album art with rotation
        currentSong?.let { song ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                contentAlignment = Alignment.Center,
            ) {
                // Album art - clip to circle
                Box(
                    modifier = Modifier
                        .size(280.dp)
                        .border(BorderStroke(4.dp, OrangePrimary), CircleShape)
                        .clip(CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    AsyncImage(
                        model = song.thumbnailBig,
                        contentDescription = song.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .rotate(if (isPlaying) rotation else 0f)
                            .fillMaxSize(),
                    )
                }
            }
        }

        // Song info
        currentSong?.let { song ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                Text(
                    text = song.title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = song.artist.ifBlank { "佚名" },
                    fontSize = 14.sp,
                    color = Color.Gray,
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
                .padding(top = 16.dp),
        ) {
            Slider(
                value = if (duration > 0) currentPosition.toFloat() else 0f,
                onValueChange = { playerViewModel.seekTo(it.toLong()) },
                valueRange = 0f..maxOf(duration.toFloat(), 1f),
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = OrangePrimary,
                    activeTrackColor = OrangePrimary,
                ),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = formatTime(currentPosition),
                    fontSize = 12.sp,
                    color = Color.Gray,
                )
                Text(
                    text = formatTime(duration),
                    fontSize = 12.sp,
                    color = Color.Gray,
                )
            }
        }

        // First row of controls: shuffle, previous, play/pause, next, repeat
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 24.dp, bottom = 16.dp),
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
                    tint = if (isShuffleActive) OrangePrimary else Color(0xFF333333),
                    modifier = Modifier.size(24.dp),
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
                    tint = Color(0xFF333333),
                    modifier = Modifier.size(28.dp),
                )
            }

            // Play/Pause
            Surface(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape),
                color = OrangePrimary,
                shadowElevation = 4.dp,
            ) {
                IconButton(
                    onClick = { playerViewModel.togglePlayPause() },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = Color.White,
                        modifier = Modifier.size(40.dp),
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
                    tint = Color(0xFF333333),
                    modifier = Modifier.size(28.dp),
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
                    MusicRepeatMode.NONE -> Icons.Default.Repeat to Color(0xFF333333)
                    MusicRepeatMode.ALL -> Icons.Default.Repeat to OrangePrimary
                    MusicRepeatMode.ONE -> Icons.Default.RepeatOne to OrangePrimary
                }
                Icon(
                    icon,
                    contentDescription = "Repeat",
                    tint = color,
                    modifier = Modifier.size(24.dp),
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
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .clickable { }
                    .padding(8.dp),
            ) {
                Icon(
                    Icons.Default.AddCircle,
                    contentDescription = "Add to Playlist",
                    tint = Color(0xFF666666),
                    modifier = Modifier.size(28.dp),
                )
                Text(
                    "播放列表",
                    fontSize = 11.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            // Share
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .clickable { }
                    .padding(8.dp),
            ) {
                Icon(
                    Icons.Default.Share,
                    contentDescription = "Share",
                    tint = Color(0xFF666666),
                    modifier = Modifier.size(28.dp),
                )
                Text(
                    "分享",
                    fontSize = 11.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            // Download
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .clickable { }
                    .padding(8.dp),
            ) {
                Icon(
                    Icons.Default.FileDownload,
                    contentDescription = "Download",
                    tint = Color(0xFF666666),
                    modifier = Modifier.size(28.dp),
                )
                Text(
                    "下载",
                    fontSize = 11.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            // Rate
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .clickable { }
                    .padding(8.dp),
            ) {
                Icon(
                    Icons.Default.Star,
                    contentDescription = "Rate",
                    tint = Color(0xFF666666),
                    modifier = Modifier.size(28.dp),
                )
                Text(
                    "评分",
                    fontSize = 11.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            // Volume control
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .clickable { showVolumeSlider = !showVolumeSlider }
                    .padding(8.dp),
            ) {
                Icon(
                    Icons.Default.VolumeUp,
                    contentDescription = "Volume",
                    tint = Color(0xFF666666),
                    modifier = Modifier.size(28.dp),
                )
                Text(
                    "音量",
                    fontSize = 11.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            if (showVolumeSlider) {
                Slider(
                    value = volumeLevel,
                    onValueChange = { volumeLevel = it },
                    valueRange = 0f..100f,
                    modifier = Modifier
                        .width(100.dp)
                        .padding(8.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = OrangePrimary,
                        activeTrackColor = OrangePrimary,
                    ),
                )
            }
        }
        }
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
