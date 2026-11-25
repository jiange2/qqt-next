# 01: 播放数据源支持 content://

**What to build:** 让播放器能够直接播放设备上的音频文件。遇到 `content://` 时走本地直读，不进入被动缓存、不经过网络栈；现有在线播放与已下载歌曲（`file://`）的行为保持不变。

**Blocked by:** None (can start immediately)

**Status:** resolved

- [x] 选一个设备上的 `content://` 音频地址交给播放器，能够正常起播
- [x] `content://` 播放不进入被动缓存，播放期间不产生任何网络请求
- [x] 在线 `http(s)` 播放仍走被动缓存与网络回源，缓存染色行为不变
- [x] 已下载歌曲的 `file://` 播放仍直读本地、不联网
- [x] 播放失败时不会回退到网络请求

## Answer

播放数据源按 URI scheme 三分流：`SchemeRoutingDataSource` 新增 `content` 分支走 `ContentDataSource`（经 SAF 持久化授权直读），`file` 仍走 `FileDataSource`，其余走 `CacheDataSource` + 网络回源；`when` 无兜底回落到网络，本地打开失败即播放失败。`SchemeRoutingDataSourceFactory` 改为接收 `Context`，用 Application 上下文按 DataSource 实例新建 `ContentDataSource`。

本地歌曲（`content://` 或 `local:` ID）在播放期不产生任何后端请求：跳过访问事实与时长写回，`buildMediaItems` 不下发 `EXTRA_SONG_ID`（车机歌词因此不拉歌词），通知封面加载只认 `file://` 与 `http(s)`。缓存染色把本地直读计入「整曲本地可得」。scheme 判定收敛为 `isLocalDirectReadUri()` / `isLocalSongUri()` 两个扩展。

提交：`9886ffd`（数据源分流）、`228d84b`（评审收敛），分支 `feat/local-music`。

未新增自动化测试：spec「Testing Decisions」已确定本功能不新增测试，验收走手工清单；本地无 Android SDK，未做编译验证。`android/CONTEXT.md` 的「缓存染色」词条已补「本地歌曲直读」，该文件整体保留在工作区未提交（其中还含本次特性既有未提交的术语改动）。
