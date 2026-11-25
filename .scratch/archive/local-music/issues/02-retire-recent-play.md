# 02: 最近播放下线，底部入口改为本地音乐（空态）

**What to build:** 底部导航第二个入口从「最近播放」换成「本地音乐」。最近播放的页面、播放时的记录写入、本地记录键全部移除；用户在未授权任何文件夹时，看到明确的引导空态和「选择音乐文件夹」主按钮。收藏页不受影响。

**Blocked by:** None (can start immediately)

**Status:** resolved

- [x] 底部第二个 Tab 显示「本地音乐」，点击进入本地音乐页
- [x] 最近播放页面及其全部可达入口消失，播放歌曲后不再产生最近播放记录
- [x] 升级后首次启动清理旧的最近播放记录，不残留无效数据
- [x] 收藏页仍能按收集到的 ID 批量换取歌曲详情并正常展示
- [x] 未授权目录时，本地音乐页展示引导文案与「选择音乐文件夹」主按钮
- [x] 「重新扫描」「管理音乐文件夹」在未授权目录时不可用或引导先选目录

## Answer

底部第二个 Tab 由「最近播放」改为「本地音乐」（`Screen.LocalMusic`，路由 `local_music`，沿用原位置与入口，路由名从 `recent` 改为 `local_music`）。`ui/screens/recent/` 整目录删除；`PlayerViewModel.playSong()` 里的 `PrefsManager.addRecentId()` 删除；`PrefsManager` 移除 `getRecentIds / addRecentId / getRecentIdsString / MAX_RECENT`，改为 `init()` 里 `purgeLegacyRecentIds()`：首次启动删掉旧键 `recent_song_ids` 并写一次性标记 `legacy_recent_ids_purged`。收藏页与我的歌单详情的换详情通道 `MusicRepository.getRecentSongs()`（后端 `get_recent_songs`）原样保留，只更新了注释口径。

新增 `ui/screens/localmusic/`：
- `LocalMusicViewModel`：`roots: StateFlow<List<LocalMusicRoot>>` 与 `addRoot(context, treeUri)`，用 `takePersistableUriPermission` 持久化 SAF 授权、查询树根 `DISPLAY_NAME` 作展示名；同一棵树重复选择只记一次。当前只在进程内持有（索引与根目录列表持久化属票据 05）。
- `LocalMusicScreen`：未授权时引导空态 + 主按钮「选择音乐文件夹」；已授权时如实列出目录树（歌曲/专辑视图与扫描属票据 03/08）。
- `LocalMusicTopBarMenu`：重新扫描（扫描管线接入后才启用，现为不可用）、添加音乐文件夹、管理音乐文件夹（未授权时不可用）；`AuthorizedFoldersDialog` 列出已授权目录（移除属票据 07）。

接线：`LocalMusicViewModel` 提升到 `AppNavigation`，屏幕与顶栏菜单共用一份状态，目录选择器（`OpenDocumentTree`）只在导航层注册一次，空态按钮与菜单项共用同一个 launcher。`MusicTopBar` 新增可选 `actions` 槽：本地音乐页用它替换全局搜索图标，其它页面行为不变。

未新增自动化测试：spec「Testing Decisions」已确定本功能不新增测试；本机无 Android SDK，未做编译验证（与票据 01 同）。`android/CONTEXT.md`、`doc/*.md` 的最近播放描述归票据 11。

提交：`6ebe203`，分支 `feat/local-music`。
