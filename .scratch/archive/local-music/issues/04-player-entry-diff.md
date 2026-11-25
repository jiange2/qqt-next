# 04: 本地歌曲的播放器入口差异化

**What to build:** 播放本地歌曲时，全屏播放器与相关列表不再露出收藏、加入歌单、评分、下载与歌词这些注定无法工作的入口；切回在线歌曲后全部恢复。

**Blocked by:** 03

**Status:** resolved

- [x] 播放本地歌曲时，全屏播放器不显示收藏、加入歌单、评分、下载入口
- [x] 本地歌曲不展示歌词页/歌词入口，且播放期间不发起任何歌词或歌曲详情请求
- [x] 切到在线歌曲后，上述入口与行为全部恢复
- [x] 本地歌曲的「更多菜单」不出现无效条目
- [x] 列表行上的收藏等快捷标记对本地歌曲不显示

## Answer

新增共享判据 `Song.isLocalSong()`（`data/local/LocalSong.kt`）：ID 走 `local:` 命名空间或 url 为 content:// 直读，UI 与播放层共用一口径；`PlayerViewModel` 的私有 `isLocalSongPlayback` 删除并改用它（顺带覆盖 content:// 直读，不再依赖 `resolveUri()` 的解析结果）。

全屏播放器以 `isLocalSong` 派生全部差异化：功能图标行只留播放设置——喜欢 / 下载 / 加入歌单 / 更多四者不组合，行改为 `Arrangement.spacedBy(40.dp, Alignment.CenterHorizontally)`（`SpaceBetween` 下单条会贴左）；顶部页签只剩「歌曲」、Pager 页数由 2 收成 1、歌词「刷新」入口换成等宽 20dp 占位以撑住页签位置；切到本地歌时无条件 `scrollToPage(0)`（页数收缩时库层可能已把页码归一，靠 `currentPage != 0` 守卫会漏复位，后果是本地歌期间封面区空白、切回在线歌又落回歌词页并补发一次 song_info）；更多菜单里的评分项对本地歌不渲染。

「不发起请求」不是另加拦截，而是入口消失的直接结果：唯一请求链是 `lyricsPageVisible → LaunchedEffect → loadLyrics → MusicRepository.getSongInfo`，本地歌下 `lyricsPageVisible` 恒 false。旁路已核：`buildMediaItems` 对本地歌不下发 `EXTRA_SONG_ID`（CarLyricsSync 无入口）、访问事实与时长回写在 ViewModel 已排除、`onPlayerError` 自愈要求 host 为 MEDIA_HOST、封面主色提取因 `thumbnailBig` 为空直接跳过。切回在线歌全部按 `currentSong` 派生恢复，没有一次性标志或「本地模式」变量。

「更多」入口在本地歌下也不出（当前菜单里只有评分）；票据 09 接入「编辑信息」后按需恢复。

额外一致性修正：`rememberFullyCached` 对直读文件直接判「整曲本地可得」，与进度条缓存染色（ViewModel 对 content:// 给 FULLY_AVAILABLE）同口径，播放队列行序号不再对本地歌显灰。

列表行快捷标记无需改动：本地歌只走 `LocalSongRow`（序号 / 标题 / 艺术家 / 时长，无任何收藏、下载标记），共享的 `SongListItem` 全仓也从不渲染收藏标记（`FavoriteStore` 只在播放器被引用），本地歌也进不去收藏与歌单，属构造性满足。

未新增自动化测试（spec「Testing Decisions」已定本功能不新增测试，验收走手工清单）；本机无 Android SDK（无 `ANDROID_HOME`/`local.properties`），未做编译验证（与票据 01、02、03 同）。手工验收重点：播放本地歌看图标行只剩播放设置、页签无「歌词」、横滑到不了第二页；停在在线歌歌词页后切到本地歌应回到歌曲页；切回在线歌后上述入口与歌词请求全部恢复。

提交：`f6f72fa`（入口差异化）、`a937f7a`（评审收敛：Pager 无条件复位 + 注释口径），均合入分支 `feat/local-music`。
