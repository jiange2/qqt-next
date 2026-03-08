package com.qqt.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.qqt.music.data.api.model.Song
import com.qqt.music.ui.theme.OrangePrimary
import com.qqt.music.viewmodel.PlayerViewModel

@Composable
fun MiniPlayer(
    playerViewModel: PlayerViewModel,
    modifier: Modifier = Modifier,
    onPlayerClick: () -> Unit = {},
) {
    val currentSong by playerViewModel.currentSong.collectAsState()
    val isPlaying by playerViewModel.isPlaying.collectAsState()

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = currentSong != null) { onPlayerClick() },
        color = Color.White,
        shadowElevation = 8.dp,
        tonalElevation = 0.dp,
    ) {
        Column(modifier = Modifier.padding(bottom = 8.dp)) {
            HorizontalDivider(thickness = 0.5.dp, color = Color(0xFFEEEEEE))
            if (currentSong != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Album art
                    AsyncImage(
                        model = currentSong!!.thumbnailSmall.ifBlank { currentSong!!.thumbnailBig },
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFEEEEEE)),
                    )
                    Spacer(Modifier.width(10.dp))
                    // Song info
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentSong!!.title,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = currentSong!!.artist.ifBlank { "佚名" },
                            fontSize = 12.sp,
                            color = Color.Gray,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    // Controls
                    IconButton(onClick = { playerViewModel.skipPrev() }) {
                        Icon(Icons.Default.SkipPrevious, contentDescription = "Prev", tint = Color(0xFF333333))
                    }
                    IconButton(
                        onClick = { playerViewModel.togglePlayPause() },
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = OrangePrimary,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                    IconButton(onClick = { playerViewModel.skipNext() }) {
                        Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = Color(0xFF333333))
                    }
                }
            } else {
                // Placeholder when no song is selected
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Empty placeholder art
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFEEEEEE)),
                    )
                    Spacer(Modifier.width(10.dp))
                    // Empty state text
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "暂无播放歌曲",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "选择歌曲开始播放",
                            fontSize = 12.sp,
                            color = Color.Gray,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    // Disabled controls
                    IconButton(onClick = { }, enabled = false) {
                        Icon(Icons.Default.SkipPrevious, contentDescription = "Prev", tint = Color(0xFFCCCCCC))
                    }
                    IconButton(
                        onClick = { },
                        modifier = Modifier.size(40.dp),
                        enabled = false,
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = Color(0xFFCCCCCC),
                            modifier = Modifier.size(28.dp),
                        )
                    }
                    IconButton(onClick = { }, enabled = false) {
                        Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = Color(0xFFCCCCCC))
                    }
                }
            }
        }
    }
}
