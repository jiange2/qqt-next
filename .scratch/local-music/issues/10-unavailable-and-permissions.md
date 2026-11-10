# 10: 文件不可用与授权失效

**What to build:** 文件在扫描后消失或无法读取时，用户看到的是该行灰显加提示，而不是整页清空；文件夹授权被系统回收时，用户看到明确的重选引导，而不是以为歌丢了。

**Blocked by:** 05

**Status:** resolved

- [x] 点播已删除或不可读的文件时，提示「文件不可用」，该行灰显且不可点
- [x] 文件不可用不会自动触发扫描，也不影响其它歌曲播放
- [x] 下次重新扫描时，已不存在的文件从列表移除
- [x] 进入本地音乐页时检查所有根目录的持久化授权
- [x] 全部授权失效时展示重新选择目录的空态与主按钮
- [x] 部分授权失效时给出提示，并提供管理音乐文件夹入口，对应歌曲灰显
- [x] 重新授权成功后，对应歌曲恢复可播，不需要重装或清数据
- [x] 授权检查不阻塞页面首帧

## Answer

**播放失败标记（`PlayerViewModel` → `LocalMusicViewModel`）。** `PlayerViewModel` 新增 `unavailableLocalSongId: StateFlow<String?>`；`onPlayerError` 先判当前歌是否本地歌（`Song.isLocalSong()`，即 `local:` 命名空间或 content URI），命中即写 ID 并返回——不进在线歌「解析/解码自愈」分支、不重试、不触发任何重扫。`AppNavigation` 在导航层收集这个信号：调 `LocalMusicViewModel.markUnavailable(id)` 把索引中该条 `available` 置 false 并原子落盘、Toast「文件不可用」、再清空信号。放在导航层而不是本地音乐页，是为了在专辑页或全屏播放器里触发时同样有提示和状态落地。

**不可用行的展示（`LocalSongRow`）。** 行内按 `song.available` 决定 `combinedClickable(enabled = ...)`：不可用时整体灰显（标题 / 艺术家 / 序号转 `InkFaint`，不再标当前曲目橙色），右侧时长位置改为「不可用」，长按编辑一并禁用。歌曲视图与本地专辑页共用这一行，两处同时生效。

**持久化授权检查（`LocalMusicViewModel.checkRootPermissions()`）。** 在 `restoredLibrary()` 读完索引后与 `LocalMusicScreen` 首次组合时各调一次。取值在 `Dispatchers.IO` 上读 `contentResolver.persistedUriPermissions`（只认读授权，URI 尾斜杠归一），失败（binder 异常）时跳过本次判定而不是误报全部失效；页面首帧不等结果，检查完成才更新状态。

**失效状态只影响展示、不写索引。** 新增 `revokedTrees: StateFlow<Set<String>>` 与派生 `allRootsRevoked`；对外暴露的 `songs` 由 `_songs` 与 `revokedTrees` 组合而来——来自失效目录的歌曲在展示副本上标 `available = false`，磁盘上的可用状态不动；`albums` 改为从这份展示列表派生，所以专辑页同样灰显。重新授权后 `checkRootPermissions` 重算即恢复，不需要重装或清数据。`applyOverride` 在替换记录时取索引里的 `available` 本值，避免用展示副本把临时灰显状态写回。

**全部 / 部分失效的界面（`LocalMusicScreen`）。** 全部根目录失效时优先展示「音乐文件夹访问权限已失效」空态 + 「重新选择文件夹」主按钮；部分失效时列表顶部加一条 `RevokedPermissionBanner`（说明数量与原因）并带「管理」按钮打开 `AuthorizedFoldersDialog`，弹层里失效目录名灰显并注明「重新添加同一个文件夹即可恢复」。

**重新授权。** `addRoot` 对已在列表中的同一棵树不再只开始扫描：先重新 `takePersistableUriPermission`，再核对授权，然后重扫（重扫会把文件重新标为可用；已不存在的文件自然不再出现在结果里）。新添加目录同样在落盘前核对一次。

**重扫与移除的行为不变。** 扫描器原本只枚举目录树里存在的文件，所以「已不存在的文件下次重扫从列表移除」无需改动；`readSong` 每次都为文件新建记录（`available` 默认 true），重扫也会清掉此前的不可用标记。

**取舍。** 判定「文件不可用」依赖 ExoPlayer 的 `onPlayerError`：本地 content URI 的任何播放错误都按不可用处理（本地直读没有网络错误可言），但错误到来前会有一次装载 + 失败的延迟；不做点击时的预检查，避免每次点播都多一次 `openFileDescriptor`。授权失效的检查不覆盖「授权仍在但 provider 暂时读不到」的情况，那种情况由播放失败兜底。

不在本票：文档同步（11）。未新增自动化测试：spec「Testing Decisions」已确定本功能不新增测试，验收走手工清单；本机无 Android SDK（无 `ANDROID_HOME` / `local.properties`），未做编译验证（与票据 01–09 同）。手工验收重点：索引里有歌后把其中一个音频文件删掉，再点播它 → Toast「文件不可用」、该行灰显且再点无反应、其它歌照常播放；杀进程重启 → 该行仍灰显；再点「重新扫描」→ 该行从列表消失；系统设置里对 App 撤销某个文件夹的授权（或用 `adb shell` 清掉持久化授权）后进入本地音乐页 → 顶部提示 + 「管理」入口 + 该目录歌曲灰显；全部撤销 → 全页「访问权限已失效」空态 + 重新选择文件夹；重新选择同一文件夹 → 歌曲恢复可播、灰显消失；进入页面时列表先出、失效提示随后出现（首帧不被授权检查阻塞）。
