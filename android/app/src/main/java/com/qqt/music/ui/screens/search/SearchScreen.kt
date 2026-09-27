package com.qqt.music.ui.screens.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SentimentDissatisfied
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.qqt.music.data.api.model.Album
import com.qqt.music.data.api.model.Artist
import com.qqt.music.ui.components.AlbumCard
import com.qqt.music.ui.components.EmptyState
import com.qqt.music.ui.components.SongListItem
import com.qqt.music.ui.theme.BrandOrange
import com.qqt.music.ui.theme.Hairline
import com.qqt.music.ui.theme.InkFaint
import com.qqt.music.ui.theme.InkPrimary
import com.qqt.music.ui.theme.InkSecondary
import com.qqt.music.ui.theme.PlaceholderBg
import com.qqt.music.ui.theme.WarmBackground
import com.qqt.music.ui.theme.WarmSurface
import com.qqt.music.player.QueueSource
import com.qqt.music.viewmodel.PlayerViewModel

/** 后端 song_search 歌曲段每页固定 10 条，满页才可能还有更多结果 */
private const val SONGS_PAGE_SIZE = 10

/**
 * 搜索页：顶栏搜索图标进入，提交式搜索（回车/搜索键触发）。
 *
 * 首屏走组合搜索一次取回三段结果：歌曲（第 1 页，最多 10 条，展示前 3 条 +
 * 「查看全部歌曲」入口进搜索结果页）、专辑与艺术家（各 20 条截断）。
 * 专辑点击进专辑歌曲页、艺术家点击进艺术家歌曲页，数据经各 Nav 对象交接。
 */
@Composable
fun SearchScreen(
    playerViewModel: PlayerViewModel,
    onMoreSongs: (String) -> Unit = {},
    onAlbumClick: (Album) -> Unit = {},
    onArtistClick: (Artist) -> Unit = {},
    viewModel: SearchViewModel = viewModel(),
) {
    val query by viewModel.query.collectAsState()
    val results by viewModel.results.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val searchFailed by viewModel.searchFailed.collectAsState()

    // 输入草稿仅存本地，提交后才写入 ViewModel 的生效关键词
    var draft by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(""))
    }
    val keyboard = LocalSoftwareKeyboardController.current

    Column(modifier = Modifier.fillMaxSize().background(WarmBackground)) {
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            placeholder = { Text("搜索歌曲、专辑、歌手", color = InkSecondary, fontSize = 15.sp) },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = InkSecondary) },
            trailingIcon = {
                if (draft.text.isNotEmpty()) {
                    IconButton(onClick = {
                        draft = TextFieldValue("")
                        viewModel.reset()
                    }) {
                        Icon(Icons.Outlined.Close, contentDescription = "清除", tint = InkSecondary)
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = BrandOrange,
                unfocusedBorderColor = Hairline,
                focusedContainerColor = WarmSurface,
                unfocusedContainerColor = WarmSurface,
                cursorColor = BrandOrange,
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                keyboard?.hide()
                viewModel.search(draft.text)
            }),
        )

        val r = results
        when {
            isSearching -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = BrandOrange)
            }
            searchFailed -> EmptyState(
                icon = Icons.Outlined.SentimentDissatisfied,
                title = "搜索失败",
                subtitle = "网络开小差了，稍后再试试",
                actionText = "重试",
                onAction = { viewModel.search(query) },
            )
            r == null -> EmptyState(
                icon = Icons.Outlined.Search,
                title = "搜点什么吧",
                subtitle = "输入歌名、专辑或歌手，帮你找到想听的",
            )
            // 三段全空才算无结果；空段整段隐藏，某段有内容就进入结果列表
            r.songs.isEmpty() && r.albums.isEmpty() && r.artists.isEmpty() -> EmptyState(
                icon = Icons.Outlined.Search,
                title = "没有找到相关内容",
                subtitle = "换个关键词试试",
            )
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (r.songs.isNotEmpty()) {
                        // 满 10 条（后端歌曲段整页）只展示前 3 条 + 「查看全部歌曲」入口；
                        // 不满 10 条即无更多，全量平铺避免截断后无入口看不到剩余结果
                        val visibleSongs = if (r.songs.size >= SONGS_PAGE_SIZE) r.songs.take(3) else r.songs
                        item { SectionTitle("歌曲") }
                        item {
                            SectionCard {
                                visibleSongs.forEachIndexed { index, song ->
                                    SongListItem(
                                        index = index + 1,
                                        song = song,
                                        playerViewModel = playerViewModel,
                                        // 播放队列取整页歌曲而非截断后的 3 条，上下曲仍在完整结果里
                                        onClick = { playerViewModel.playSong(song, r.songs, QueueSource.DEFAULT) },
                                    )
                                }
                            }
                        }
                        if (r.songs.size >= SONGS_PAGE_SIZE) {
                            item {
                                ActionRow(label = "查看全部歌曲", onClick = { onMoreSongs(query) })
                            }
                        }
                    }
                    if (r.albums.isNotEmpty()) {
                        item { SectionTitle("专辑") }
                        r.albums.chunked(2).forEach { rowAlbums ->
                            item {
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    rowAlbums.forEach { album ->
                                        AlbumCard(
                                            album = album,
                                            onClick = { onAlbumClick(album) },
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (r.artists.isNotEmpty()) {
                        item { SectionTitle("歌手") }
                        item {
                            SectionCard {
                                r.artists.forEach { artist ->
                                    ArtistRow(artist = artist, onClick = { onArtistClick(artist) })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 分区标题：左对齐加粗小标题 */
@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        color = InkPrimary,
        modifier = Modifier.padding(start = 2.dp, top = 4.dp),
    )
}

/** 白底圆角卡片，包裹列表行（歌曲行/歌手行），与暖色页面底色区分 */
@Composable
private fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(WarmSurface),
        content = content,
    )
}

/** 「查看全部歌曲」整行入口 */
@Composable
private fun ActionRow(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(WarmSurface)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = BrandOrange,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = InkFaint,
        )
    }
}

/** 搜索结果歌手行：圆头像 + 名字，点击进艺术家歌曲页 */
@Composable
private fun ArtistRow(artist: Artist, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(PlaceholderBg),
        ) {
            AsyncImage(
                model = artist.imageThumb.ifBlank { artist.image },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = artist.name,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = InkPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = InkFaint,
        )
    }
}
