# 06: 重新扫描：增量、进度、取消

**What to build:** 用户点「重新扫描」后，App 增量比对磁盘与索引：新增入列、消失移除、变更重读，结束后提示新增与移除数量。扫描期间旧列表可继续浏览，播放不被打断，取消或失败不会留下半截索引。

**Blocked by:** 05

**Status:** resolved

- [x] 顶部菜单提供「重新扫描」，点击后开始扫描并展示进度
- [x] 新增、删除、替换文件后重扫，列表按增量正确更新
- [x] 未变化的文件不重新计算内容指纹、不重读标签
- [x] 扫描完成后提示「新增 x 首、移除 y 首」
- [x] 重扫期间旧列表可浏览、可播放；正在播放的歌曲不中断
- [x] 扫描中可取消；取消后索引保持上一次完整状态
- [x] 扫描失败不破坏已有索引，并给出失败提示
- [x] 同一时刻只运行一个扫描任务，重复触发会取消旧任务

## Answer

**增量复用。** `LocalMusicScanner.scan(roots, previous, onProgress)` 新增 `previous` 入参（调用方传当前 `_songs.value`，已合并覆盖层也无妨：复用只取文件标签那部分）。上一份索引按 `SongLocation(treeUri, relativePath)` 建表；命中且 `size > 0 && lastModified > 0 && size/mtime 都相等` 才算未变，直接复用其 `fingerprint / tags / durationMs`，`documentUri` 等定位信息仍取本次遍历的新值，跳过 `contentFingerprint`（两次 32KB 读 + SHA-256）与 `MediaMetadataRetriever` 两笔主要成本。provider 不给大小或修改时间时无法确认未变，按变更加指纹重读（宁可多算，不把改过的文件当成没变）。改名/移动的文件路径命中不了，会重算一次指纹，但身份不变。

**diff 口径。** 新增 `ScanResult(songs, added, removed)`，`added/removed` 按内容身份（指纹集合差）计算，不以路径比较：移动或改名不计成「移除 + 新增」，与 ADR 0021 的身份语义一致。结果仍按标题升序（`Collator(Locale.CHINA)`）。日志打一行「扫描完成：N 首，复用 M 个未变文件（跳过指纹与标签），新增 a、移除 b」，复用一个数就是「未变文件不重算指纹/不重读标签」的现场证据。

**完成 / 失败提示。** VM 新增一次性 `notice: StateFlow<String?>` + `consumeNotice()`（StateFlow 而非 SharedFlow，避免页面尚未重组时丢事件）；开始时先清空未消费的旧提示。成功写「新增 x 首、移除 y 首」，失败写「扫描失败，请重试」（仅在有旧列表时；空列表时页面另有失败空态，不重复提示）。`LocalMusicScreen` 用 `LaunchedEffect(notice)` 收口到 Toast，与全 App（`PlayerScreen`、`MainActivity` 的下载失败）同一轻提示口径。

**失败不再被吞。** 原来 `queryChildren` 的 `runCatching` 把所有目录读取异常都当空目录，持久化授权被回收时会被判成「扫描成功、库里没歌」并落盘清空。现在给 `queryChildren` 加 `failOnError`：根目录（`relativePath` 为空）读取失败向上抛，VM 的 `catch (Exception)` 保留旧索引并置 `LocalScanState.failed = true`；子目录读取失败仍按空目录处理（文件在扫描途中被删属正常）。首次扫描就失败时页面给「扫描失败 + 重新扫描」空态。

**单任务语义。** `startScan()` 改成：`scanJob != null` 时置 `rescanRequested` 并 `cancel()` 旧任务，旧任务在 `finally` 里收尾后按最新目录重跑（之前是「结束后补跑」，与 spec「新扫描会取消旧扫描」不一致）。用「收尾后再起新任务」而不是并发起第二个，既满足取消语义又不会两个任务同时遍历；取消态只在用户主动取消（`!rescanRequested`）时展示，换新请求重跑不会闪一帧「已取消」。

**已知取舍。** 被指纹去重丢弃的副本路径不在索引里（索引每份内容只留一条），重扫时会再算一次指纹——成本是一次 64KB 读，换来索引不为副本路径额外加字段（spec「持久化」的索引字段清单固定）。重扫时仍在磁盘上的文件 `available` 复位为 true，置灰/失效流程属票据 10。

不在本票：根目录移除与重叠拒绝（07）、专辑视图与封面（08）、编辑面板（09）、授权失效引导与不可用灰显（10）、文档同步（11）。

未新增自动化测试：spec「Testing Decisions」已确定本功能不新增测试，验收走手工清单；本机无 Android SDK（无 `ANDROID_HOME` / `local.properties`），未做编译验证（与票据 01–05 同）。手工验收重点：重扫未改动目录看日志「复用 M 个未变文件」且列表不变、提示「新增 0 首、移除 0 首」；增删改各一个文件后重扫提示 1/1 且顺序正确；重扫中看旧列表可点播、播放不中断；点取消后列表与重启后的列表都是上一次结果；扫描中再点「重新扫描」（或添加目录）只有一个任务在跑且用的是最新目录；把某个根目录的持久化授权撤掉后重扫，应提示「扫描失败」且旧列表不丢。

提交：`a9fe00b`（增量扫描与提示）、`81b3cfe`（评审收敛：根目录读取失败按扫描失败、复用键与计数去重），均合入分支 `feat/local-music`（合并提交 `a4f4944`、`cd6c306`）。
