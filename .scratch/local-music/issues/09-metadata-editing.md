# 09: 编辑本地歌曲元数据

**What to build:** 用户可以在歌曲列表长按或从全屏播放器「更多菜单」打开编辑面板，修改标题、艺术家、专辑、专辑艺术家、音轨号、年份，或恢复默认值。改动只作用于 App 内的展示，不修改音频文件；重扫、移动、改名之后仍然生效。

**Blocked by:** 05、08

**Status:** resolved

- [x] 长按歌曲行与全屏播放器「更多菜单」都能打开同一个编辑面板
- [x] 可编辑标题、艺术家、专辑、专辑艺术家、音轨号、年份
- [x] 保存后歌曲列表、专辑分组与专辑封面立即刷新
- [x] 「恢复默认」清除该歌曲的全部改动，回到文件标签值
- [x] 改动只写覆盖层，音频文件本身不被修改（用其它播放器查看标签保持原值）
- [x] 重扫、移动文件、重命名后改动仍生效
- [x] 移除并重新添加目录后，改动仍生效
- [x] 音轨号只接受正整数、年份只接受四位数字，非法输入被拒绝并提示
- [x] 标题或艺术家被清空时回退到文件标签值
- [x] 改专辑导致原专辑歌曲为空时，该专辑从专辑视图消失

## Answer

**规则层（`data/local/LocalMetadataEdit.kt`，纯 Kotlin，不碰界面也不碰文件）。** `LocalMetadataDraft` 是六个字段的原始输入，`LocalSong.metadataDraft()` 用合并后的展示值当初值（用户看到什么就改什么）。`parseTrackNoInput / parseYearInput` 只认 ASCII 数字：音轨号正整数（`0`、符号、小数点、全角数字都判非法），年份四位且非 `0000`；留空一律 `Ok(null)`（等于回到标签值），非法给 `Invalid(message)`，提示文案「音轨号只接受正整数 / 年份只接受四位数字」。`LocalSong.overrideFrom(draft, trackNo, year)` 把草稿折算成 `LocalSongOverride`：留空的字段不写覆盖（清空即回退标签值，标题再回退去掉扩展名的文件名），与文件标签值相同的字段也不写——不制造把标签「冻结」住的假覆盖项；标题的标签基准与 `displayTitle()` 的兜底同口径（标签缺失时是文件名）。

**「没改动就不校验」（评审后补的口径）。** `trackNoInput / yearInput` 对「输入文本与文件标签值一字不差」的情况直接返回 `Ok(tagValue)`，跳过校验：面板初值就是展示值，标签里若存着不合规的老值（两位数年份、`0` 音轨号），不该在用户什么都没改的时候把整张面板锁死、保存按钮永远点不动。写入侧仍然安全——这样的输入在 `overrideFrom` 里被判成「未改动」，不会把非法值写进覆盖层；用户一旦改动这个字段，校验立即生效并红框提示。面板与 ViewModel 走同一个函数，UI 与落盘不会出现两套判断。

**ViewModel（`LocalMusicViewModel`）。** `saveMetadata(song, draft)` 返回 `LocalMetadataSaveResult.Saved(song)` 或 `.Invalid(trackNoError, yearError)`（后者是并发改稿的兜底，正常路径上保存按钮已按校验结果置灰）；`restoreDefaultMetadata(song)` 就是 `applyOverride(song, null)`。`applyOverride` 原地改写内存里的覆盖层、返回合并后的歌曲，然后整条链（换列表 → 写覆盖层 → 作废封面）排在一把 `Mutex` 里顺序执行：列表替换在 `Dispatchers.Default` 上 map + `sortedByDisplayTitle()`（改标题后列表顺序跟着变，与扫描提交共用同一份排序口径），`LocalMusicStore.saveOverlay` 原子落盘，`coverCache.invalidate(新旧专辑键)` 后把 `coverGeneration` 加一。扫描提交处也顺带对齐：合并覆盖层前先在主线程取一份覆盖层快照，再在默认调度器上合并 + 排序。

**封面为什么改成传专辑对象。** `albumCover(albumKey)` 原先靠 `albums.value` 反查专辑，编辑后「封面已作废、专辑分组还没重算」的那一帧会查不到这张专辑、返回 null 让占位图卡住；改成 `albumCover(album)` 后，卡片与专辑页头部把当下的专辑成员直接传下去，`produceState` 的 key 从专辑键换成 `(album, generation)`——成员或标题变了就重新取图，重扫清缓存则靠代次。`albumKey()` 因此由 private 提升为公开，编辑时用它取新旧两个键分别作废（改专辑名或专辑艺术家，同一首歌会落进两张不同的专辑）。

**UI。** 新增 `ui/screens/localmusic/LocalMetadataEditSheet.kt`：M3 `ModalBottomSheet` 底部弹层，六个 `OutlinedTextField`（音轨号与年份并排、数字键盘）+ 「恢复默认 / 取消 / 保存」，右上角说明「只改 App 里的显示，不会修改音频文件本身」，下方一行「留空即回到文件标签值；专辑留空时按所在文件夹名显示」（专辑卡片对空专辑名有文件夹名兜底，这句避免用户以为面板漏填了屏上明明有值的字段）。非法输入边输入边红框 + 字段下方提示，保存置灰；没有覆盖项的歌「恢复默认」置灰（本来就在用标签值）。三个入口共用这一个面板：`LocalSongRow` 从 `clickable` 换成 `combinedClickable`（`onLongClick` 是可选参数，不传时行为与原来一致），`LocalMusicScreen`（歌曲视图）与 `LocalAlbumScreen`（专辑页）各自 `remember` 一个 `editingSong` 长按打开；`PlayerScreen`「更多」菜单里在线歌是评分、本地歌是「编辑信息」，`localMusicViewModel` 由 `AppNavigation` 传进来（与本地音乐页同一个实例，同一份索引与覆盖层）。「更多」图标因此从 `if (!isLocalSong)` 里移出来，改由 `if (!isLocalSong || localSong != null)` 控制：本地歌的索引记录都找不到时（目录已被移除）没有可改的记录，入口一并收起。新增 `PlayerIcons.Edit` 铅笔图标。

**播放器展示值同步。** `PlayerViewModel.refreshLocalSongDisplay(id, title, artist)` 只换队列副本与 `currentSong`（MiniPlayer 与全屏播放器立即跟随），不动正在播放的 `MediaItem`——不重新装载、不打断播放；通知栏与车机的文案等下次装载时刷新，KDoc 已写清。

**评审收敛。** 编辑链加 `Mutex` 串行化（原先注释承诺的「连续改两首不丢前一次」在两次编辑重叠时不成立）；扫描提交的覆盖层快照挪到主线程取；`LocalAlbum.coverSong` 的注释改为「取样目录是排在最前那首所在目录」——票据 09 之后，跨文件夹改到同一张专辑是正常路径，原来的「同一专辑的歌曲同在一个目录」不再是前提；`LocalMusicStore.saveOverlay` 的「待接入」注释改成当下的调用方。

**已知取舍。** 标签里的非法老值在面板上既不红框也不提示，保存照常可用（等于继续用标签值），理由见上；「专辑留空按文件夹名显示」这种情况，用户主动把文件夹名填进专辑字段仍会换组（从文件夹键变成专辑键），这是他要「把散装歌归进专辑」的正常结果，没有拦。播放器播放中的通知栏文案不即时刷新（不重载 MediaItem 的代价）。编辑面板只有一个 `editingSong`，同一时刻不会同时开两个。

不在本票：授权失效引导与不可用灰显（10）、文档同步（11）。

未新增自动化测试：spec「Testing Decisions」已确定本功能不新增测试，验收走手工清单；本机无 Android SDK（无 `ANDROID_HOME` / `local.properties`），未做编译验证（与票据 01–08 同）。手工验收重点：歌曲视图长按改标题 → 列表顺序与专辑页跟着变、重度排序验证中文 Collator；改专辑名 → 原专辑剩余歌曲为 0 时该专辑从专辑视图消失、新专辑出现；改专辑后卡片封面换成新专辑的文件夹封面或内嵌图；播放某首本地歌时从「更多 → 编辑信息」改标题 → MiniPlayer 与全屏播放器立即换字、播放不中断、通知栏文案保持旧值；「恢复默认」后回到文件标签值且按钮变灰；用其它播放器查看该文件标签确认未被改写；音轨号填 `0`/`-1`/`1.5`、年份填 `20`/`abcd` 时红框提示且保存不可点；标题清空后回落到文件名（标签有标题时回落到标签）；移动或重命名文件后重扫，改动仍在；移除并重新添加同一目录后重扫，改动仍在。

提交：`29ad6e8`（数据层规则、ViewModel 覆盖层写入、编辑面板、三处入口与播放器展示值同步），合入分支 `feat/local-music`。
