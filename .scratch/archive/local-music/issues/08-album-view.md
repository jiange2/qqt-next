# 08: 专辑视图与本地专辑页（含封面）

**What to build:** 本地音乐页可在「歌曲 / 专辑」两视图间切换；专辑视图把本地歌曲按专辑归并成卡片墙，点开进入本地专辑页，按音轨号连续播放。封面优先取文件夹封面文件，没有则取音频内嵌图。

**Blocked by:** 03

**Status:** resolved

- [x] 本地音乐页提供「歌曲 / 专辑」切换，进入页面默认歌曲视图
- [x] 专辑按「专辑名 + 专辑艺术家」分组；专辑名为空时用所在文件夹名，不同文件夹的同名专辑互不合并
- [x] 专辑卡片展示封面、专辑名、艺术家与歌曲数量，按专辑名升序
- [x] 点击专辑进入本地专辑页，歌曲按音轨号升序，音轨号为空按标题
- [x] 在本地专辑页点歌，以整张专辑为播放队列
- [x] 封面优先取文件夹内的封面文件（`cover / folder / front / album`，`jpg / jpeg / png / webp`），没有再取音频内嵌图，都没有时显示占位图
- [x] 专辑封面缓存有容量上限并按最近使用淘汰，不会无限增长
- [x] 专辑视图与专辑页不发起任何网络请求

## Answer

**分组与排序（`data/local/LocalAlbum.kt`）。** `List<LocalSong>.groupIntoAlbums()` 纯派生、不落盘：分组键有专辑名时是「专辑名 + 专辑艺术家」，专辑名为空时是「文件夹名 + 文件夹路径 + 所属树 URI」（路径带上树，`Music/Live` 与 `SD/Live` 因此不会并成一张）；`\u0001` 作分隔符，避免「A+B」与「A」+「B」这类拼接撞键。全部分组读的都是展示值（覆盖层优先、缺失取标签），所以票据 09 改专辑名后歌曲立即换组。专辑名回退文件夹名、卡片艺术家回退该专辑首曲艺术家；专辑内音轨号升序、无音轨号的一律排在后面并按标题，专辑之间按专辑名升序，比较器统一 `Collator.getInstance(Locale.CHINA)`（同名不同专辑再按艺术家、键定序，保证列表稳定）。专辑键的编码在同一个文件：`encodeLocalAlbumKey / decodeLocalAlbumKey` 用 `Base64.URL_SAFE or NO_WRAP or NO_PADDING`，字符集只有 `A-Za-z0-9-_`，直接当路由参数不会撞上 `/`、`%`。

**封面缓存（`data/local/LocalCoverCache.kt`）。** 优先级：歌曲所在文件夹的 `cover / folder / front / album` + `jpg / jpeg / png / webp`（大小写不敏感，按此顺序取第一个读得出来的）→ 该专辑首曲的内嵌图（`MediaMetadataRetriever.embeddedPicture`）→ null（页面显示占位图）。结果按专辑键的 SHA-256 落到 `cacheDir/local_covers/*.img`，临时文件 + rename 写入；容量上限 16MB，超出按最近使用淘汰（本进程访问过的条目按访问顺序，没访问过的按文件 mtime）。本进程内还记住「确认没有封面」的专辑键，避免同一张无封面专辑每次滚回屏幕重开一次解析器；重扫提交时 `clear()` 一并作废，于是「文件夹换了封面」在重扫后生效（spec）。单张封面读取上限 8MB，读到上限即视为截断、换下一种来源。

**定位文件夹的取舍。** 索引里只存 `relativePath`（spec 的索引字段清单固定，不为封面加字段），所以封面解析时从树根按相对路径逐级 `query` 找文件夹 documentId——与扫描同源，第三方 provider 的不透明 documentId 也走得通；每张专辑只在首次解析时走一遍，之后命中缓存。

**ViewModel。** `albums: StateFlow<List<LocalAlbum>>` 由 `_songs.map { }` 派生（分组与排序在 `Dispatchers.Default`，歌多也不占主线程），扫描提交 / 移除目录 / 后续编辑覆盖层都会自然带动专辑视图，没有第二份状态需要同步。`albumCover(albumKey)` 在 IO 线程取封面文件。扫描提交后（在 `NonCancellable` 块里，与索引落盘同一段不可取消路径）清空封面缓存并把 `coverGeneration` 加一：专辑键不变而封面文件换了，卡片以代次为 key 重新取封面，不必退出再进页面。

**UI。** `LocalMusicScreen` 增加自绘分段控件「歌曲 / 专辑」（未用 Material 的 SegmentedButton，与全 App 的扁平橙白风格一致），选中项白底橙字、底槽 `PlaceholderBg`；视图选择是 `remember` 的页面内状态，不落盘也不记忆。专辑视图是两列 `LazyVerticalGrid`，卡片与在线专辑页同尺寸（1:1 封面 16dp 圆角 + 名称在下），副行是「艺术家 · N 首」。`LocalSongRow` 与原 `formatDuration` 从 Screen 文件抽到 `LocalSongRow.kt` 供专辑页复用。新增 `ui/screens/localalbum/LocalAlbumScreen.kt`：头部大封面 + 专辑名 + 艺术家 + 首数，下面按分组时算好的顺序列歌；点歌 `queue = album.songs.map { it.toSong() }`、来源仍是 `QueueSource.LOCAL`（与歌曲视图共用同一份来源记忆）。专辑不存在（重扫、移除目录、改专辑名）时给空态，索引还在读时给加载态。

**导航。** `Screen.LocalAlbum("local_album/{lid}")` + `LocalAlbumNav.albumName`（顶栏标题在导航层，拿不到页面状态，所以只暂存专辑名）。`composable` 显式声明 `navArgument("lid") { type = NavType.StringType }`，页面自己从路由参数解出专辑键——进程被杀后重建也能定位回同一张专辑，不依赖导航对象。专辑页计入 `showBackButton`、退出抽屉手势、以及「不挂离线横幅」的本地音乐域。

**附带的一致性修正。** `AppNavigation` 里断网 / 服务不可达横幅对本地音乐域（`local_music` 与 `local_album`）不再渲染：这两个页面根本不发请求，横幅文案「展示最近一次内容」对它们没有意义（spec 用户故事 38）。此前没有任何票据覆盖这一条。

**已知取舍。** 队列元素（`LocalSong.toSong()`）暂不带封面：封面是异步解析出来的本地文件，而点歌路径是同步的，硬接要么给每次点播加一次 SAF 查询与解析，要么得改造播放模型；本票只做专辑卡片与专辑页的封面展示，`toSong()` 的注释已按此口径写清（不再指向「后续票据」）。歌曲视图的行本来就不展示封面，视觉上没有缺口；MiniPlayer / 全屏播放器对本地歌仍是占位图。若要做，方向是把封面 URL 注入队列元素（封面已在缓存时是零成本），需要单独决定点播时是否允许一次 IO。

不在本票：编辑面板与覆盖层写入端（09）、授权失效引导与不可用灰显（10）、文档同步（11）。

未新增自动化测试：spec「Testing Decisions」已确定本功能不新增测试，验收走手工清单；本机无 Android SDK（无 `ANDROID_HOME` / `local.properties`），未做编译验证（与票据 01–07 同）。手工验收重点：进本地音乐页默认歌曲视图，切「专辑」看到卡片墙且按专辑名升序；两张无专辑名的同名文件夹（如两个 `Live`）应分成两张卡而不是一张；点开专辑看音轨顺序（有号的在前，无号的按标题）；点中间一首，MiniPlayer 里下一首应是同专辑的下一首；目录里放 `cover.jpg` 重进页面能看到封面，删掉它重扫后封面回落到内嵌图，两者都没有时显示占位图；飞行模式下专辑视图与专辑页照常可浏览可播放、无离线横幅；反复进出几十张专辑后查 `cacheDir/local_covers` 体积不超过 16MB。

提交：`533f210`（专辑视图、专辑页、封面缓存与导航）、`ea8ee9c`（`toSong` 封面注释口径），均合入分支 `feat/local-music`。