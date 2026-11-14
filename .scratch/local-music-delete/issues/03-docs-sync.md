# 03: 文档同步与归档

**What to build:** 核对 CONTEXT.md「删除（本地歌曲）」词条（已落，与「排除文件夹」区分）与最终实现一致；spec 实现完成后推进 Status 并补 Comments；判定不新增 ADR（增量功能，与 ADR 0018/0022 一致，无架构推翻，分版本决策记入 spec）；票据归档。

**Blocked by:** 01: 分版本删除链路与权限, 02: 歌行左滑删除 UI 与列表联动.

**Status:** open

- [ ] CONTEXT.md「删除（本地歌曲）」词条与最终实现一致（删除=真删文件、左滑入口、须经确认）
- [ ] spec Status 推进 + Comments 记录实现提交与验收结论
- [ ] 确认不新增 ADR 的判定成立（无架构推翻）；如实现中出现推翻性决策再补 ADR
- [ ] 票据 01–03 勾选项经人工编译真机确认后归档

## Comments

- 2026-11-14 完成。提交 b390d8e（文档同步 + spec 文字对齐 + 票据01/02 Comments）与本次提交（本条与 spec Comments）。**同步的文档**：`android/doc/ui.md`——关键文件表补两页删除职责、新增 `RowSwipeReveal.kt` 条目并修正 `SongListItem.kt` 的过期描述（原「缩略图+评分+下载按钮」→ 序号/标题/歌手与播放下载计数/行尾封面 + 可选左滑）；导航层接线补双 launcher（`StartIntentSenderForResult` / `RequestPermission`）、唯一一份 ≤28 App 确认框、`notice` Toast 上移、专辑删空自动返回，并写明「放常驻宿主而非页面」的理由（页面级 launcher 随导航注销会丢回调）；新增「行内左滑操作（SwipeRevealRow）」一节，填补 `android/doc/` 此前零左滑文档的缺口（手势/88dp DangerRed 白字 14sp/同页互斥/滚动与点击收起/白底由组件提供/四处启用范围，引 ADR 0018）；LocalMusicScreen 节补删除入口、分版本确认、成功即移除+分组重算+快照原子落盘、失败 Toast「删除失败」+灰显、取消不动、灰显行可删、权限被关不露左滑、播放中不特殊处理；LocalAlbumScreen 节补同一套删除口径、删空自动返回、`onAlbumPageEntered()` 权限核对与当场提示；DownloadScreen 节注明与本地歌删除是两条独立链路。`android/doc/storage.md`——补「删除成功后该行从整库快照移除、整份原子重写（临时文件 + rename，写失败只记日志）；覆盖层/排除清单/队列快照刻意不清理」，与既有「文件删后重加行 ID 变新、覆盖丢失」取舍一致不重复；关键文件表补 `LocalMusicDeleter.kt` / `LocalDeleteRules.kt`。`android/project.md`——权限清单补 `WRITE_EXTERNAL_STORAGE`（`maxSdkVersion="29"`）与真实运行时口径（**仅 ≤28 链路且 API 23+ 申请**；21–22 安装即授予无权限框、29 走 RSE、30+ 不申请不持有），目录树补两个 data/local 新文件与 `RowSwipeReveal.kt`、两页左滑删除，模块概述补删除能力；顺带修正 `minSdk 26` → `21`（与 `build.gradle.kts` 一致，否则与「API 21–22 安装即授予」自相矛盾）。`android/CONTEXT.md`「删除（本地歌曲）」词条经与最终实现逐条核对一致（真删文件 / 两页歌行左滑入口 / 须经一次确认：30+ 系统删除框、29 系统授权框、≤28 App 框），**未改动**；「本地音乐」等其它词条一律未动。**spec 文字对齐**：≤28/≤29 申请冲突（C3/B9）、灰显非自绘背景而是 `InkFaint` + `combinedClickable(enabled=false)`（C1）、URI 直接用已存 `LocalSong.contentUri` 无第二构造函数（C2）、≤28 确认框精确文案与 M3 默认色（C4/A1）、用户故事 6 顺序改为 App 确认 → `startDelete` → 按需 WRITE 权限框 → 删除；并补记 A2 删空返回绑定删除成功动作、A3 权限被关不露左滑、B2 notice 上移、B5 两处 listState + 滚动/视图切换收起、B7 在途刷新竞态、§3.6 专辑页权限核对、`SendIntentException` 归一为 Cancelled 的边缘、JVM 测试 12 + 8 例已写未执行。**不新增 ADR 的判定经交付代码核对成立**：本特性正是 ADR 0018 Consequences「以后新增行内操作一律走同一手势与同一个按钮位」预留的扩展路径（`LocalSongRow` 只是包 `SwipeRevealRow` + 传 label/`onAction`，无新手势机制）；身份（行 ID）/ 权限（音频读取 + ≤28 WRITE 补充）/ 快照（整库原子写）全在 ADR 0022 既有口径内；ADR 0020 只否决回写文件标签、不禁止删除文件，且实现未清覆盖层、未做「先改标签再删」，无冲突。三条门槛（难以反转 / 无上下文会让人惊讶 / 真实取舍）均不成立——分版本路由是平台强制、确认口径是平台能力差异。故未新增 ADR、未改 ADR 0018 的启用范围枚举（分版本确认口径按 spec 只记在 spec），`android/docs/adr/`、仓库级 `docs/adr/`、`GLOSSARY-MAP.md` 全未动。**Status 推进与归档留待评审修复后统一执行**（照 `local-music-rescan` 先例：归档提交 9482043 在评审修复合入 ff80782 之后，Comments 才能带全哈希），故 spec 仍 `ready-for-agent`、三张票据仍 `open`，全部 `- [ ]` 保持未勾——它们是设备级验收清单，待人工编译后真机逐项确认（本仓规约：AI 不编译、不跑 gradlew）。