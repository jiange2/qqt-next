# 本地音乐导入（接收微信/QQ 等 App 递出的音频）

Status: resolved

> 延续 ADR 0022（MediaStore 来源、行 ID 身份、全机音乐 + 排除文件夹）与 ADR 0020（元数据只写 App 内覆盖层、不回写文件标签），不推翻任何 ADR。导入是新增的写侧能力（接收外部音频 → 复制进媒体库），非架构变更。入口形态由系统边界决定：qqt 够不到来源 App 的私有目录（`Android/data/`），只能接收来源 App 经「用其他应用打开 / 分享」递出的文件，故 qqt 内不设导入按钮。

## Problem Statement

用户在微信/QQ 里收到的 mp3 等音频，被存在来源 App 自己的私有目录（`Android/data/com.tencent.mm/`、`Android/data/com.tencent.mobileqq/`）。Android 11+ 上系统媒体库不收录这些目录，本地音乐（来源为系统媒体库）扫不到，用户没法在 qqt 里听这些歌。手动用文件管理器挪文件也不顺手——文件管理器同样进不去来源 App 私有目录。用户需要一条把微信/QQ 收到的音频收进本地音乐库的路。

## Solution

入口在来源 App 一侧：用户在微信/QQ 里点音频 →「用其他应用打开」或「分享」→ 选 qqt。qqt 注册成音频的打开方式 / 分享目标，接住来源 App 递出的 `content://` URI，把文件复制进系统媒体库的公共音乐目录 `Music/导入/`，它就成为一首普通本地歌曲，自动进本地音乐库。导入只落库、不自动播放；qqt 界面内不设导入按钮（够不到来源 App 私有目录，按钮点下去也无文件可挑）。对任何 App 的音频打开/分享生效，不止微信/QQ。重复导入各自成行、不去重不覆盖（沿用 ADR 0022）。

## User Stories

1. 作为本地音乐听众，我在微信里点收到的 mp3 →「用其他应用打开」→ 选 qqt，以便把这首歌收进本地音乐。
2. 作为本地音乐听众，我在微信里用「分享」把音频发给 qqt，以便分享出口也能导入。
3. 作为本地音乐听众，我在 QQ 里同样「用其他应用打开 / 分享」选 qqt，以便 QQ 收到的歌也能进库。
4. 作为本地音乐听众，我在任何 App（文件管理器、网盘、其他播放器）里打开/分享音频选 qqt 都能导入，以便不限于微信/QQ。
5. 作为本地音乐听众，我导入成功后看到轻提示「已导入到本地音乐」，以便知道收进来了。
6. 作为本地音乐听众，我导入的歌落在公共音乐目录的 `Music/导入/` 文件夹，以便和我自己的音乐分开、好认好管理。
7. 作为本地音乐听众，我导入后回到本地音乐页就能看到这首歌（自动刷新），以便不用手动点重新扫描。
8. 作为本地音乐听众，我导入只落库、不自动播放，以便不打断我当前正在听的歌、不重建播放队列。
9. 作为本地音乐听众，我把同一首歌导两遍，库里各自成行出现两条，以便和现有「同一内容多副本各自成行」的库行为一致。
10. 作为本地音乐听众，目标文件夹已有同名文件时导入自动加后缀，以便不覆盖旧文件、每次导入都各自成行。
11. 作为本地音乐听众，导入失败时看到轻提示「导入失败」，以便知道没成功，且不留半截文件。
12. 作为本地音乐听众，qqt 没在运行时从微信打开音频选 qqt，冷启动也能完成导入，以便不用先手动开 App。
13. 作为本地音乐听众，qqt 已在运行时导入，走同一入口完成，以便不用重启 App。
14. 作为本地音乐听众，我没给音频读取权限时导入照样落库成功，以便文件先收进来；进本地音乐页走现有权限引导、授权后就能看到。
15. 作为本地音乐听众，我导入的歌成为一首普通本地歌曲，能播放、能编辑信息、能批量管理、能左滑删除，以便和库里其他歌一样对待。
16. 作为本地音乐听众，我导入的歌按现有本地专辑归并规则分组（有专辑标签按「专辑名 + 专辑艺术家」，无标签按所在文件夹名兜底），以便归并行为可预期。
17. 作为本地音乐听众，我在 qqt 界面里找不到「导入」按钮，以便不被误导以为 qqt 能扫微信——入口在来源 App 的打开方式菜单。
18. 作为本地音乐听众，我分享非音频文件给 qqt 不会被接收，以便只收音频、不收杂物。
19. 作为本地音乐听众，导入过程中当前播放不中断、播放队列不重建，以便听歌不受影响。
20. 作为本地音乐听众，我导入的歌重启 App 后仍在（随库快照落盘），以便整理成果不丢。
21. 作为本地音乐听众，我导入的歌文件被我在外部删除后，下次刷新自然消失（行 ID 身份），以便和现有失效口径一致。
22. 作为本地音乐听众，我导入的歌可被「排除文件夹」移出展示（`Music/导入/` 也是库中含音乐的文件夹），以便不想看时能排除。
23. 作为本地音乐听众，我导入的歌默认取系统媒体库预抽取的标签作展示值，并可被覆盖层编辑，以便和现有本地歌曲的元数据口径一致。

## Implementation Decisions

### 入口与接收

- MainActivity 新增 intent-filter：`ACTION_VIEW` + `ACTION_SEND`，MIME 限 `audio/*`（VIEW 取 `intent.data`，SEND 取 `EXTRA_STREAM`）。对任何 App 的音频打开/分享生效，不止微信/QQ。**实现偏差（已裁定）**：VIEW filter 声明 `content` scheme + `audio/*`；SEND filter 仅声明 `audio/*`、**不带 scheme**——SEND 意图只带 MIME、无 data URI，`IntentFilter.matchData` 在 filter 声明 scheme 时对 SEND 的空 scheme 必不匹配（NO_MATCH_DATA，源码核实），带 scheme 会令分享入口失效；`content` scheme 改由胶水层对 `EXTRA_STREAM` 防御性校验（非 content 拒收），spec 意图（只收音频 content URI）不变。
- 先只处理单文件；多文件分享（`ACTION_SEND_MULTIPLE`）本期不支持。
- qqt 界面内不设导入按钮、不做 SAF 选择器掏来源 App 私有目录（系统边界，ADR 0022 已定不补充）。
- 非音频 MIME 不接收（intent-filter 已限 `audio/*`，胶水层再校验一次）。

### 意图胶水（UI 层）

- MainActivity `onCreate`（冷启动）与 `onNewIntent`（在运行）解析意图：取音频 URI → 调 `LocalMusicViewModel.importAudio(uri)`。launchMode 取 `singleTask`（在运行复用实例走 `onNewIntent` 的必要条件，standard 会新建 Activity/ViewModel；副作用为任务根复用与桌面回栈行为变化，单 Activity + NavHost 内部栈影响面小，留真机三态验收）。
- 胶水只做「解析 URI + 转交」，不含复制/命名/刷新逻辑（那些在接缝与 ViewModel）。

### 唯一写侧接缝（数据层）

- 新增 `LocalMusicImporter` 接口（镜像 `LocalMusicDeleter`）：`suspend fun importAudio(sourceUri: Uri): LocalImportResult`。UI 与 ViewModel 只调它，不直接碰 MediaStore 写入 API。
- 结果归一 `LocalImportResult`（sealed，镜像 `LocalDeleteResult` / `LocalLibraryResult` 风格）：Success / Failure（带可空原因）。
- 实现 `MediaStoreImporter(context, sdkInt)`（与 `MediaStoreLibrarySource` / `MediaStoreDeleter` 同口径注入 sdkInt）：
  - Android 10+（API 29+）：`MediaStore.Audio.Media` 插入，`RELATIVE_PATH = "Music/导入/"`，`IS_PENDING = 1` 期间写入字节、写完置 0（失败不暴露半截文件）；App 拥有自己创建的行，**不需要新权限**。
  - Android 9 及以下（≤28）：写公共 `Music/导入/` 目录 + `MediaScannerConnection` 触发扫描（`WRITE_EXTERNAL_STORAGE` 清单已有，maxSdkVersion 29）。扫描触发是辅助步骤：`scanFile` 失败仅记日志仍返回 `Success`（文件已落位，系统周期扫描兜底收录）。
  - ≤28 权限取舍（已裁定）：API 23+ 未授予 `WRITE_EXTERNAL_STORAGE` 时直接返回 `Failure`、**不弹运行时申请框**——spec 钉死单步 suspend 接缝，不镜像删除的两步动作机（`RequestWritePermission` 交接）；≤28 的 READ/WRITE 同属 STORAGE 权限组，授过读即有写（API 21–22 安装即授予），影响面极小。
- 框架 IO（ContentResolver 读写、MediaStore 插入、扫描）留真机手工验收。

### 纯函数（JVM 可测）

- `importRoute(sdkInt)`：版本路由（MediaStore 插入 / 直写 + 扫描），镜像 `deleteRoute`。
- `resolveImportFileName(desired, existingNames, ignoreCase)`：目标目录重名时加后缀、保留扩展名，保证「各自成行、不覆盖」。29+ 精确匹配（ignoreCase 默认 false）；≤28 外置存储文件系统大小写不敏感，传 ignoreCase = true——大小写撞名（`Song.mp3` vs `song.mp3`）同样加后缀而非失败，后缀候选也按大小写不敏感判占用。
- 展示名推导：从来源 URI 尾段 / 文件名取展示名（接缝 impl 取原始名，纯函数做规范化）；规范化对无扩展名的非空名（微信语音裸名等）补 `.mp3`——扩展名是入库提示（MediaStore / 扫描器按扩展名推 MIME、置 `IS_MUSIC`），缺了它导入「成功」却库里看不到；实际容器格式由播放器按字节嗅探，已有扩展名原样保留。

### 重复与重名语义

- 每次导入生成新文件、新媒体库行（各自成行，沿用 ADR 0022「同一内容多副本各自成行、不做内容级去重」）。
- 目标目录已有同名文件 → 纯函数加后缀，不覆盖旧文件；≤28 按大小写不敏感判「同名」（外置存储文件系统大小写不敏感），29+ 精确匹配。落位前的目标 exists 核对是第二道防线，真并发竞态重列目录 + 重解析有限重试（收敛、不死循环），超限仍失败不覆盖。

### ViewModel 编排

- 新增 `importAudio(uri)` 入口：调接缝 → Success 触发 `refresh(notify = false)`（复用现有整库刷新管线）+ `notice`「已导入到本地音乐」；Failure → `notice`「导入失败」。
- 不自动播放、不重建播放队列、不动当前播放。
- 注入沿用现有内部构造器（与 `source` / `deleter` 同口径），fake 接缝即可 JVM 测编排。
- 提示经现有 `notice` StateFlow → 导航层 collector 弹 Toast（两页共用口径）。

### 权限与身份

- 复制落库本身不需音频读取权限：接住来源 App 授予的 URI 读权限 + 写自己创建的媒体库行。
- 要在本地音乐页**看到**导入的歌仍需现有音频读取权限——没给就走现有权限引导（灰显 + 重新授权横幅），导入照样成功。
- 导入的歌 = 普通本地歌曲，行 ID 身份（ADR 0022，Android 10+ 带卷名限定）；覆盖层编辑、批量管理、左滑删除、播放全复用现有机制。

### 文档

- CONTEXT.md 已新增「导入（本地音乐）」词条（grilling 期间落地），弃用「扫描微信/QQ」「导入按钮」。
- 文档口径（票据03 已同步）：`android/doc/ui.md` 记导入入口（来源 App 打开方式 / 分享、qqt 内无按钮）、intent-filter 事实与意图胶水、VM 实例共享口径、分版本落库与结果提示；`android/doc/storage.md` 记导入分版本落库、重名加后缀、≤28 写入权限取舍与静默刷新后整份快照重写；`android/project.md` 记导入能力、目录树与不新增权限口径。
- 不新增 ADR：导入是可逆的新增能力（去掉 intent-filter 与接收逻辑即可），延续 ADR 0020 / 0022、无推翻；三条门槛（难以反转 / 无上下文会让人惊讶 / 真实取舍）中「难以反转」不成立。「入口在来源 App 而非 qqt 按钮」的系统边界理由记入词条与本 spec。

## Testing Decisions

- 好测试只测外部行为、不测实现细节：纯函数测路由/命名/归一的输入输出，ViewModel 测「成功→刷新+提示 / 失败→提示 / 不播放」的可观察结果，不断言内部调用顺序。
- 纯函数 JVM 测试：`importRoute` 版本路由、`resolveImportFileName`（重名加后缀、扩展名保留、无扩展名/空名等边界、≤28 大小写不敏感撞名与后缀候选占用、29+ 精确匹配不受影响）、展示名规范化（含无扩展名补 .mp3、点号开头/结尾点号边界）、落库目录口径与 ≤28 写入权限门槛（`LocalImportRulesTest` **30 例**，照 `LocalMusicDeleterTest` 先例：独立字面量断言、不重言式），已写、未执行。
- ViewModel 编排 JVM 测试：fake `LocalMusicImporter` / `LocalMusicLibrarySource` 经 internal 构造器注入（导入 fake 拆成功 / 失败两个无分支 fake：成功 fake 把新歌行放进 source 模拟落库、失败 fake 不动 source——mocking.md「mock 内无分支」），断言 Success 触发刷新与「已导入到本地音乐」（新歌经 `songs` 可见）、Failure 出「导入失败」且列表不动、无音频权限时成功提示 + 刷新静默跳过，不自动播放（`LocalMusicViewModelImportTest` **3 例**），已写、未执行。「照 source / deleter 的 fake 注入范式」澄清为**构造器口径**：internal 构造器原已存在但零调用方，仓内原无 VM 测试先例，本测试为首个。
- VM 测试基础设施（spec 缺口，票据02 已裁定）：新增 `testImplementation` kotlinx-coroutines-test **1.8.1**（与运行时解析版本对齐：coil 2.7.0 → coroutines-android 1.8.1，lifecycle 2.8.4 声明 1.7.3 冲突取高；gradle 缓存仅 descriptor 无 jar，**首次人工编译需联网**拉取）+ `testOptions.unitTests.isReturnDefaultValues = true`（Application() 可构造、框架方法返回默认值；现有纯函数测试不调 android 方法、不受影响），不引 Robolectric/mockk。mockable jar 清空 `Uri` 静态初始化 → 测试 source set `android.net.TestUri` 替身（同包继承，除 toString 外全抛、意外框架调用响亮失败）；测试 Application 覆写 `getFilesDir` 到 `TemporaryFolder`（否则 filesDir=null 快照落工作目录、用例间串库）；`StandardTestDispatcher` + `Dispatchers.setMain` 驱动 viewModelScope，VM 硬编码 IO/Default 跳线程由 runTest 实时等待、挂起 `first{}` 等可观察结果落定。
- 接缝 MediaStore 实现 + 意图胶水：真机手工验收（设备级验收清单，各票未勾选项）——微信/QQ 打开方式与分享、冷启动/在运行/桌面图标回栈（singleTask 三态）、落 `Music/导入/`、重名加后缀、重复导入各自成行、失败提示、无权限落库成功、导入歌可播/可编辑/可删除、飞行模式可用（纯本地）。
- 先例：`LocalMusicDeleterTest`、`MediaStoreProjectionTest`、`AudioReadPermissionTest`（纯函数 JVM）；ViewModel fake 注入照 `LocalMusicLibrarySource` / `LocalMusicDeleter` 的 internal 构造器口径（无现成 VM 测试代码先例）。
- 本仓规约：AI 不编译、不跑 gradlew，测试已写待人工执行。

## Out of Scope

- qqt 内导入按钮 / SAF 掏微信/QQ 私有目录（系统边界，ADR 0022 不补充）。
- 多文件分享（`ACTION_SEND_MULTIPLE`）——先单文件。
- 内容去重、导入历史、来源标记徽章。
- 导入后自动播放。
- 自定义导入目录（固定 `Music/导入/`）。
- 导入进度条（复制毫秒~秒级，Toast 即够；大文件进度本期不做）。
- 写回文件标签 / 改动来源 App 的文件（只复制，不动来源）。
- 非音频文件导入。
- 本地音乐内搜索、排序菜单（沿用既有 Out of Scope）。

## Further Notes

- 「入口在来源 App 一侧、qqt 内无按钮」是系统边界决定的（qqt 够不到 `Android/data/` 私有目录，SAF 选择器同样被挡），不是产品取舍——未来读者疑惑「为何没有导入按钮」以此解释。
- 无专辑标签的导入歌按文件夹名兜底，聚成一个「导入」专辑（grilling Q2 已接受的副作用）；有专辑标签的按「专辑名 + 专辑艺术家」正常归并。
- 行 ID 身份：导入歌文件被外部删除后重导 = 新行 = 覆盖层编辑丢失（ADR 0022 已知取舍，导入不改变）。
- 专辑艺术家列 API 30+ 才有值，低版本标签为空走兜底（与现有本地歌曲一致）。
- 导入与在途刷新的竞态沿用既有已知取舍（最后写入胜出）：导入成功触发的刷新与用户手动重扫重叠时，各按提交时刻现值落盘，不额外缓解。

## Comments

- 2026-11-14 grilling 定稿：方向（做导入功能）与入口形态（来源 App「用其他应用打开 / 分享」+ qqt 接收器、qqt 内无按钮）确认；Q1 进库即止不自动播放、Q2 落 `Music/导入/`、Q3 各自成行不去重不覆盖，三项全部同意。机制委托项（VIEW+SEND audio/*、MediaStore 插入 IS_PENDING、重名加后缀、冷启动/在运行、复用刷新与 notice、无需新权限、单文件先行）随答复一并确认。接缝提案（唯一写侧接缝 `LocalMusicImporter` 镜像 `LocalMusicDeleter` + 纯函数 JVM 测 + ViewModel fake 注入 + 意图胶水真机验收）经用户确认。CONTEXT.md「导入（本地音乐）」词条已写入。spec 入库，Status ready-for-agent，票据留待 /to-tickets 编排（本仓规约：AI 不编译、不跑 gradlew）。
- 2026-11-14 票据03 文档同步：实现提交票据01 e07b270（合并 a2ba40c）、票据02 689e60f（合并 c701e4b）；本票文档提交哈希留待编排层补记。spec 文字对齐代码事实：SEND filter 仅 MIME 不带 scheme 的偏差与原因（IntentFilter.matchData 对 SEND 空 scheme 必不匹配，content scheme 由胶水层校验）、「唯一写侧接缝」补 ≤28 未授 WRITE 直接 Failure 不弹申请框的取舍（单步接缝钉死、STORAGE 权限组授读即有写）、launchMode 落定 singleTask、Testing Decisions 更新为 LocalImportRulesTest 24 例 + LocalMusicViewModelImportTest 3 例已写未执行、fake 注入澄清为构造器口径、VM 测试基础设施裁定（coroutines-test 1.8.1 + returnDefaultValues + TestUri 替身 + TemporaryFolder 隔离，首次编译需联网）。android/doc/ui.md（关键文件表 + 接线 bullets + LocalMusicScreen 导入三段 + 注意事项所有权口径）、android/doc/storage.md（关键文件表 + 导入落库段）、android/project.md（模块职责 / 目录树 / MainActivity 注释 / 权限句）已同步；CONTEXT.md「导入（本地音乐）」词条核对与最终实现一致、未改。**不新增 ADR 判定对照交付代码核实成立**：导入是可逆新增能力（去掉 intent-filter、意图胶水与接缝即可，读 / 删除 / 编辑 / 播放管线零改动）；延续 ADR 0022（行 ID 身份、各自成行不去重、全机音乐 + 排除文件夹自然收录 `Music/导入/`，查询零改动）与 ADR 0020（只复制字节、不写来源文件标签，导入歌编辑仍走覆盖层），无推翻；三条门槛（难以反转 / 无上下文会让人惊讶 / 真实取舍）中「难以反转」不成立——SEND filter 偏差是平台机制（IntentFilter.matchData）强制，≤28 权限取舍是单步接缝约束下的实现选择，均不构成架构推翻，理由已记入正文；「入口在来源 App 而非 qqt 按钮」的系统边界理由已在词条与本 spec。Status 推进与票据归档留待评审修复合入后统一执行（照 local-music-delete / local-music-rescan 先例）。
- 2026-11-14 双轴评审修复（Standards + Spec）。修复提交 9d698be（ticket/lmi-review-fixes，基于集成分支 78bbba5）。**FIX1（Spec 硬修复）**：≤28 外置存储文件系统大小写不敏感，原精确匹配让 `Song.mp3` 穿过纯函数、撞 `target.exists()` 直接 Failure，违反用户故事 10——`resolveImportFileName` 加 `ignoreCase` 参数（默认 false，29+ 精确匹配语义不变、现有 24 例零改动），≤28 链路传 true：大小写撞名加后缀而非失败，后缀候选同口径判占用；`target.exists()` 二次防线保留，真并发竞态由 `resolveLegacyTarget` 重列目录 + 重解析有限重试（上限 3 次，每轮重列纳入新文件、后缀递增必收敛，超限失败不覆盖、不死循环）。**FIX2（Spec 硬修复）**：`normalizeImportDisplayName` 对无可用扩展名的非空名补 `.mp3`（无点号如微信语音裸名、点号开头无主名 `.mp3`、结尾点号空扩展 `song.`，与 resolve 的扩展名切分同口径；已有扩展名含非 mp3 原样保留）——推广 DEFAULT_IMPORT_NAME「扩展名是入库提示、播放器按字节嗅探」口径，堵 MIME 推断失败 `IS_MUSIC=0`「提示成功却库里看不到」（用户故事 15）。**FIX3（Spec 文档对齐）**：唯一写侧接缝 ≤28 条补 scanFile best-effort（扫描触发是辅助步骤，失败仅记日志仍 Success、系统周期扫描兜底收录），与票据01 Comments / storage.md 既有口径一致。**FIX4（Standards）**：VM 测试 FakeImporter 含 `if (result is Success)` 分支违反 mocking.md「mock 内无分支」，拆 FakeSuccessImporter（把新歌行放进 source 模拟落库）/ FakeFailureImporter（不动 source）两个无分支 fake，三用例改用对应 fake、断言不变。**FIX5（Standards，做）**：两链路共享命名调用提取 `desiredImportName`（原始名 → 规范化）；FIX1 后撞名解析口径分链路（29+ 精确 / ≤28 大小写不敏感 + 重试），全组合不再同形，共享粒度取在规范化为止（重试不重复查 provider），catch→清理形状差异（pending 行 vs 临时文件）镜像 MediaStoreDeleter 先例维持不强提。测试：LocalImportRulesTest 24→30 例（新增 6：大小写撞名加后缀、后缀候选大小写占用、29+ 精确匹配放行、无扩展名补 mp3、已有扩展名保留、点号开头/结尾边界，独立字面量），VM 3 例不变，全部已写未执行（本仓规约：AI 不编译）。文档：storage.md 导入段与 spec 纯函数 / 重名语义 / Testing Decisions 已同步。已接受取舍不动：隐式 VM 共享（ui.md 已记前提与失效预案）、单文件结构（镜像 LocalMusicDeleter 先例）。不改票据文件、Status 不变、不新增 ADR。
- 2026-11-14 归档：Status ready-for-agent → resolved。票据 01（e07b270，合并 a2ba40c）、02（689e60f，合并 c701e4b）、03（c4a103b，合并 c5b40ca）与双轴评审修复（9d698be，合并 c2f7d11）全部合入集成分支 feat/local-music-import（tip c2f7d11）。评审结论：Standards 0 硬违规（4 判断项：隐式 VM 共享与单文件结构为已接受取舍——前者 ui.md 已记机制前提与失效预案、后者镜像 LocalMusicDeleter 先例；FIX4 无分支 fake、FIX5 共享命名已修），Spec 0 缺失（FIX1 ≤28 大小写撞名加后缀、FIX2 无扩展名补 .mp3、FIX3 scanFile best-effort 口径已修）。测试 LocalImportRulesTest 30 例 + LocalMusicViewModelImportTest 3 例已写未执行；随用户请求合并 main 并打包 release（本次编译为用户显式授权，禁编译规约的当次例外）。三票 Status 同步推进 resolved；全部 `- [ ]` 保持未勾——设备级验收清单，待人工编译后真机逐项确认（微信/QQ 打开方式与分享、singleTask 三态、≤28 大小写撞名与裸名进库、29+ 重名后缀与失败清理、无权限落库、飞行模式）。
- 2026-11-25 归档：目录移至 `.scratch/archive/local-music-import/`。spec 与票据 01–03 均 resolved；集成分支 `feat/local-music-import` tip `c2f7d11`，main 合并 `bb0c4c8`。未完成项：设备级验收复选框待人工真机确认；`LocalImportRulesTest` 30 例 + `LocalMusicViewModelImportTest` 3 例已写未执行。