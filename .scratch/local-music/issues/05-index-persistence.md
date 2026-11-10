# 05: 本地索引与覆盖层持久化

**What to build:** 扫描结果不再只活在内存里：冷启动直接恢复本地音乐列表，队列快照中的本地歌曲重启后仍能继续播放。索引与用户改动分开存放，为后续的元数据编辑留好落点。

**Blocked by:** 03

**Status:** resolved

- [x] 扫描完成后索引落盘，杀进程重启后本地音乐列表直接出现，无需重新扫描
- [x] 索引与覆盖层分别持久化，覆盖层不随重扫或索引重建而丢失
- [x] 两份文件均原子写入，写入过程中被杀进程，重启后仍能读出上一次完整状态
- [x] 索引带版本号；版本或内容无法识别时安全降级为空列表并提示重新扫描，不崩溃
- [x] 队列快照里的本地歌曲在冷启动后可以恢复播放与进度
- [x] 所有读写发生在 IO 线程，启动与页面首帧不被阻塞

## Answer

新增 `data/local/LocalMusicStore.kt`：两个 App 私有 JSON，各带版本号（`INDEX_VERSION` / `OVERLAY_VERSION` 均为 1）。

- 索引 `local_music_index.json`：根目录列表 + 每首歌的 `fingerprint / treeUri / documentUri / relativePath / displayName / folderName / size / lastModified / durationMs / tags 快照 / available`（`available` 是 spec 字段清单里的「可用状态」，本票只做透传，置灰流程属票据 10）。写索引时**不写** `overlay`：覆盖层是自己的文件。
- 覆盖层 `local_music_overlay.json`：`身份 → {title, artist, album, albumArtist, trackNo, year}`，字段可空且 Gson 默认跳过 null，天然满足「只存非空值」；空壳条目（`LocalSongOverride.isEmpty`）不入盘。写入端 `saveOverlay` 已备好，接面板的是票据 09。
- 原子写：临时文件 → `FileOutputStream` 写入 → `fd.sync()` → `rename` 覆盖目标，`Mutex` 串行化两次写盘（扫描完成与添加根目录可能并发）；读取前 `discardStaleTemp` 清掉上次写到一半被杀留下的 `.tmp`，目标文件始终是上一次的完整状态。rename 的删旧重试只作兜底（app 私有目录在 POSIX 文件系统上首次 rename 即覆盖）。
- 降级：JSON 坏了 / 空文件 / 版本不符 / 缺 `roots`、`songs` 顶层字段 → `IndexLoad.Unreadable`（空列表 + 提示重扫，不崩溃）；单条歌曲缺身份或定位信息就地丢弃，不会造出播不了也去不了重的记录。覆盖层读不出来一律按「无覆盖」处理（展示回落文件标签）。DTO 字段全部可空 + `orEmpty()` 兜底，规避 Gson 反射注入绕过 Kotlin 默认值导致的 NPE。

`LocalSong` 增加 `LocalSongOverride` 与 `overlay` / `available` 字段，`displayTitle/Artist/Album/AlbumArtist/TrackNo/Year` 全部改为「覆盖值优先，缺失取标签快照」；扫描出来的原始结果不带覆盖值，`withOverride()` 是唯一的合并入口，列表与 `toSong()`（播放队列）都只认合并后的实例。

`LocalMusicViewModel`：

- `init` 里 `restoreLibrary()` 在 `Dispatchers.IO` 上读回覆盖层 + 索引并合并，主线程只做 StateFlow 落位，冷启动不发起任何网络请求、也不自动重扫。
- 新增 `loading`（首帧不把「还没读出来」当成「没有音乐」）与 `indexUnreadable` 两条状态。索引不可识别时根目录从系统里的持久化授权（`persistedUriPermissions`）重建，页面给「本地索引无法读取 + 重新扫描」，点一下即重建；连授权都没了则退回「重新选择音乐文件夹」的引导空态。
- 索引缺失（`Absent`）但系统仍有授权时（升级上来、索引被清掉）直接自动扫一次，避免落到语义错误的「目录里没有音乐」。
- 扫描完成：合并覆盖层 → 整体替换列表 → `withContext(NonCancellable)` 落盘索引。写盘不可取消，是为了避免「列表已经换了、用户此刻点取消」被标成扫描取消而磁盘停在旧索引。添加根目录后也立即落盘根目录表（这次扫描被取消，下次启动也不丢授权）。
- 覆盖层只在启动时读一次进内存、重扫时只读不写，因此「重扫 / 索引重建不丢覆盖层」是结构性的。
- `addRoot` 的 `takePersistableUriPermission` 与树名查询从主线程挪进 IO（原实现会在主线程查 cursor）。

`LocalMusicScreen` 增加「正在加载本地音乐」加载态与两条「本地索引无法读取」空态，复用既有 `EmptyState` 与主题，不改视觉规范。

队列快照恢复（验收第 5 条）经核对无需改动：`LastPlayedStore` 存的是整个队列的 `Song` 副本，本地歌的 `url` 就是文件 content URI、`id` 为 `local:<指纹>`，冷启动 `restoreLastPlayed` 用同一份 `buildMediaItems` 重建队列并带上位置；`resolveUri()` 不会把 `local:` id 映射到下载路径（`DownloadManager.localUri` 对非下载 ID 返回 null），本地歌不下发歌词 extras、访问事实与时长上报被 `isLocalSong()` 拦住；持久化授权保证重启后 URI 仍可读。

不在本票：增量重扫与「新增 x 首、移除 y 首」轻提示（06）、根目录移除与重叠拒绝（07）、专辑视图与封面缓存（08）、编辑面板与覆盖层写入端（09）、授权失效引导与不可用灰显（10）、文档同步（11）。

未新增自动化测试：spec「Testing Decisions」已确定本功能不新增测试，验收走手工清单；本机无 Android SDK（无 `ANDROID_HOME` / `local.properties`），未做编译验证（与票据 01–04 同）。手工验收重点：扫描完成后杀进程重启，列表直接出现且不再扫；把 `local_music_index.json` 改坏或把 `version` 改成一个不存在的值后重启，应看到「本地索引无法读取 + 重新扫描」且不崩溃；重扫后覆盖层文件内容不变；播放本地歌后杀进程重启，MiniPlayer 应仍是那首歌且进度可续；扫描完成落盘瞬间点「取消」，内存列表与重启后的列表都是新结果。

提交：`5836817`（索引与覆盖层落盘）、`66b1107`（评审收敛：缺索引自动重扫、写盘不随取消丢失、rename 兜底），分支 `feat/local-music`。
