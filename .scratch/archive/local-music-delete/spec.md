# 本地音乐删除（左滑删物理文件）

Status: resolved

> 延续 ADR 0022（MediaStore 系统媒体库）与 ADR 0018（行内左滑删除），不推翻任何 ADR。元数据覆盖层（ADR 0020）、排除文件夹、歌曲/专辑视图、播放接入均不动。

## Problem Statement

本地音乐页现在只能播放和编辑元数据，不能删除。用户要清理设备上的音频文件（删掉不想要的歌）时，得退出 App 去文件管理器或系统音乐 App 操作，本地音乐管理缺一个「删除」闭环。注意「删除」与现有「排除文件夹」是两回事：排除只是本 App 不展示、文件还在；删除是把音频文件从设备存储真正删掉，删后其他应用也不再可见。

## Solution

本地音乐页与本地专辑页的歌行增加行内左滑「删除」（复用 ADR 0018 的 `SwipeRevealRow`，样式对齐下载页），删除经系统媒体库删除链路真正移除设备上的音频文件，全系统版本支持：Android 11+ 走官方 `createDeleteRequest`（系统确认框即唯一确认）、Android 10 走 `RecoverableSecurityException` 系统授权、Android 9 及以下先经 App 内确认框、再按需申请 `WRITE_EXTERNAL_STORAGE`（API 23+ 且未授予时）后直接删。删除成功该行即时从列表消失并落盘快照；失败 Toast 提示。长按元数据编辑、排除文件夹、播放接入全部不动。

## User Stories

1. 作为本地音乐听众，我希望在本地音乐页左滑歌行露出「删除」按钮，以便不退出 App 就能清理设备上的歌。
2. 作为本地音乐听众，我希望在本地专辑详情页同样能左滑删除歌行，以便两处管理一致。
3. 作为本地音乐听众，我希望删除的是设备存储里的音频文件本身（其他播放器也不再可见），以便真正清理空间，而不只是本 App 藏起来。
4. 作为本地音乐听众，我希望删除前有一次确认，以便不会误删。
5. 作为本地音乐听众，我希望 Android 10+ 的确认就是系统删除框、不再多弹一个 App 框，以便确认一次就够。
6. 作为本地音乐听众，我希望 Android 9 及以下删除时先弹 App 确认框、确认后再按需申请存储写入权限（API 23+ 且尚未授予时才弹权限框），以便老设备也能删。
7. 作为本地音乐听众，我希望删除成功后该行立刻从列表消失，以便所见即所得、不用手动重扫。
8. 作为本地音乐听众，我希望删除失败时得到提示，以便知道文件没删掉（如已被外部删除）。
9. 作为本地音乐听众，我希望长按歌行仍是编辑元数据、与左滑删除互不干扰，以便两种管理并存。
10. 作为本地音乐听众，我希望删除正在播放的歌不被特殊打断，真播不了时走现有「文件不可用」提示，以便行为可预期。
11. 作为本地音乐听众，我希望灰显（文件已不可用）的歌行也能左滑删除，以便清理残留条目。
12. 作为本地音乐听众，我希望删除某首歌后它所在专辑的分组与计数随之更新，以便专辑视图一致。

## Implementation Decisions

### 删除语义与入口

- 删除 = 经系统媒体库删除链路移除设备上的音频文件本身（其他应用也不再可见），区别于「排除文件夹」（仅本 App 不展示）。术语见 CONTEXT.md「删除（本地歌曲）」。
- 入口：本地音乐页与本地专辑页的歌行（同一 `LocalSongRow` 组件）行内左滑露「删除」按钮，复用 `SwipeRevealRow` / `RowSwipeAction` / `RowSwipeRevealState`（ADR 0018），按钮样式对齐下载页（`DangerRed`、88dp、白字）。长按元数据编辑保持不变。
- 权限门槛：音频权限被系统关闭时整库灰显，歌行**不露**左滑删除（`swipe = null`，与既有 `albumsEnabled = false` 同口径）——整库灰显是权限问题、不是文件失效；文件真不可用（`markUnavailable`）的灰显行照常可删（用户故事 11）。
- 手势状态：两页各持一个页面级 `RowSwipeRevealState`（同页同一时刻只一行展开），`LazyColumn` 传 `rememberLazyListState()`、列表滚动即收起；本地音乐页切换歌曲/专辑视图时也收起。
- 不做多选/批量删除；删除入口只有左滑一处（长按元数据面板不加删除按钮）。

### 分版本删除链路（新接缝 LocalMusicDeleter）

- 唯一新接缝 `LocalMusicDeleter`（data/local）：输入本地歌曲，直接 `Uri.parse(song.contentUri)`——该 URI 在媒体库查询期就按卷 + 行 ID 构造好并随快照入盘，不存在也不需要第二个 URI 构造函数。按系统版本路由三条链路，输出归一化删除结果（成功 / 失败 / 用户取消）。UI 与 ViewModel 只调它，不直接碰 MediaStore 删除 API。
- Android 11+（API 30+）：`MediaStore.createDeleteRequest(resolver, uris)` → `startIntentSenderForResult` 拉系统确认框 → 用户允许后系统执行删除。无需任何写入权限（已持音频读取权限即可）。
- Android 10（API 29）：`resolver.delete(uri)` 捕获 `RecoverableSecurityException` → 用其 IntentSender 拉系统授权框 → 授权后重试删除（App 自有文件可能直接删成功、不弹框）。
- Android 9 及以下（API ≤28）：`resolver.delete(uri)` 直接删；API 23+ 且尚未授予 `WRITE_EXTERNAL_STORAGE` 时先运行时申请，授予后再删、拒绝则失败不删；API 21–22 没有运行时权限框（清单声明即安装时授予），跳过申请直接删。
- 交接模型是「动作 → 回调」：`startDelete` 产出 UI 下一步动作（`LaunchSystemDialog` 自带接缝构造好的 `IntentSenderRequest` / `RequestWritePermission` / `Finished`），UI 执行后把原始结果喂回 `onSystemDialogResult` / `onWritePermissionResult`，回调总是终态；接缝无状态，待删歌曲由 ViewModel 在两次调用之间保管。
- 两个 launcher（系统框 `StartIntentSenderForResult`、写入权限 `RequestPermission`）与 ≤28 的 App 确认框都注册/渲染在常驻导航宿主 `AppNavigation`，不在页面：本地音乐页导航到专辑页时离开组合，页面级 launcher 随之注销、系统框返回时回调会丢。`LocalMusicDeleter` 负责版本路由与结果归一，不持有 Activity 与 launcher。
- Manifest 新增 `WRITE_EXTERNAL_STORAGE`（`maxSdkVersion="29"`）；**运行时申请只在 ≤28 链路且 API 23+**——29 走 RSE 授权（scoped storage 下 WRITE 不授予删除能力），30+ 不申请、不持有。

### 确认交互

- Android 10+：系统删除/授权框即唯一确认，App 不再叠加自己的确认框（下载页有 App 框是因为删私有缓存无系统框，本地歌不同）。
- Android 9 及以下：无系统框，App 内 `AlertDialog` 确认兜底，由导航层在调用删除接缝前弹出（两页共用一份）。文案沿用下载页删除确认的句式、语义改对本地歌曲：标题 `删除本地歌曲`，正文 `「${song.displayTitle()}」将从设备存储删除，其他应用也不再可见。`，确认 `删除`、取消 `取消`；两个 `Text` 都不设 color（走 M3 默认，与下载页一致）。歌名取 `displayTitle()`（覆盖层优先，ADR 0020；`LocalSong` 没有 `.title`）。
- ≤28 的实际顺序：App 确认 → `startDelete` → 需要时才弹写入权限框 → 删除（再次删除、WRITE 已授予则无权限框）。「是否需要 WRITE」只有接缝内部知道，而 App 确认必须先于 `startDelete`，故权限框落在确认之后。
- 用户在系统框/App 框取消 → 不删、行保持原样、无提示。

### 列表联动与快照

- 删除成功：从 ViewModel 的 `_songs`（整库，含被排除文件夹的行）即时移除该行 → 专辑分组/计数随之重算（纯派生，零新增代码）→ 库快照落盘（`LocalMusicStore` 原子写），不等下次扫描。媒体库行已删，下次刷新自然不再出现。列表整形是纯函数 `songsAfterDelete`：成功只按歌曲身份移除目标行，失败/取消原样返回同一列表。
- 专辑删空自动返回：删除成功事件带被删歌曲的专辑键与删后剩余可见行数，剩余数在删除提交的主线程时刻同步算好（`albums` 派生经默认调度器异步重算，事件消费时读它会拿到删除前的旧值）；导航层只在「当前就在专辑页 + 剩余 0 + 路由专辑键与事件一致」时 `popBackStack()` 回本地音乐页。返回绑定在「删除成功」这个动作上，不做「album == null 就 pop」的通用观察——进程被杀后恢复到本就不存在的专辑时仍显示既有空态。
- 删除失败：一律 Toast「删除失败」（硬编码中文，经导航层 collector 弹出）。仅当确认媒体库行已不在（`resolver.delete` 返回 0 行——文件已被外部删除 / URI 失效）才 `markUnavailable(song.id)` 把该行灰显并落盘（走现有不可用标记链路；对已灰显行是 no-op，状态本就正确）。拒绝写入权限、provider 异常、异常框结果码、IntentSender 发送失败都保持该行可播——它们不证明文件不可播，而灰显是落盘且阻断播放的状态、必须要求正面证据；真不可播的文件仍会被既有播放失败链路（`unavailableLocalSongId`）捕获灰显，并在下次刷新时自然消失。
- 不清理派生数据：播放队列快照、元数据覆盖层（ADR 0020）、排除清单不做连带清理——与 ADR 0022 现有取舍一致（文件删除重加行 ID 变新、覆盖丢失为已知取舍）。队列里残留的同一首真播不了时走现有「文件不可用」链路。

### 提示消费与专辑页权限核对

- 一次性轻提示 `notice`（重扫数量 / 刷新失败 / 删除失败）的 Toast 消费在导航层 `AppNavigation`，不在页面：专辑页没有页面级 collector，放页面会让专辑页的删除失败静默无声、提示挂着等回到本地音乐页才迟到弹出。上移后两页 + 顶栏重扫提示口径统一，本地音乐页不再自己消费（避免双弹），`consumeNotice()` 语义不变。
- 专辑页进页面核对一次音频权限（`onAlbumPageEntered()`，**只核对权限、不刷新**）并补 `ON_RESUME` 观察（`onAppResumed()`，与本地音乐页同构）。原因：专辑页从不走 `onPageEntered()`，进程被杀后直接恢复到专辑页时 `permissionGranted` 停在初值 false，会把正常已授权的库误判成权限被关（整库灰显、左滑删除露不出）。不让专辑页调 `onPageEntered()`：那会给它加入口刷新，扩大在途刷新竞态窗口、改变既有刷新语义。

### 播放中删除

- 不做特殊处理：不主动停止/跳歌。缓冲未断继续播，真断了走现有 `_unavailableLocalSongId` → `markUnavailable` + Toast「文件不可用」→ 行灰显链路。

### 文档

- 文档口径：CONTEXT.md「本地音乐」节有「删除（本地歌曲）」词条（与「排除文件夹」明确区分）；`android/doc/ui.md` 描述两页的左滑删除入口、分版本确认与结果联动、导航层的 launcher / App 确认框 / 提示消费 / 删空返回，以及共享的行内左滑机制（ADR 0018）；`android/doc/storage.md` 描述删除成功后整库快照重写与派生数据不清理；`android/project.md` 记 `WRITE_EXTERNAL_STORAGE` 的声明与运行时申请口径。
- 不新增 ADR：本特性走的正是 ADR 0018 预留的扩展路径（「以后新增行内操作一律走同一手势与同一个按钮位」），身份 / 权限 / 快照模型仍落在 ADR 0022 内，也不与 ADR 0020 冲突（0020 只否决回写文件标签，不禁止删除文件）。三条门槛（难以反转 / 无上下文会让人惊讶 / 真实取舍）均不成立——分版本路由是平台强制、确认口径是平台能力差异。分版本链路与确认交互决策记入本 spec，ADR 0018 的启用范围枚举不改。

## Testing Decisions

- 好的测试只测外部行为：给 `LocalMusicDeleter` 喂 URI 与版本条件、断言删除结果与列表/快照最终形态，不测 MediaStore 内部。
- 接缝：`LocalMusicDeleter` 之下的结果处理（成功移除行 + 落盘、失败 Toast + 刷新、取消不动）与专辑分组联动为可注入逻辑；分版本系统框链路（`createDeleteRequest` / `RecoverableSecurityException` / WRITE 申请）依赖系统交互，以真机手工验收为主。
- 纯函数以 JVM 测试覆盖（版本路由 / 写入权限门槛 / 系统框结果码与删除行数归一 / 失败结果的文件不可用判定 / 删除结果列表整形 / App 确认门槛 / 删除后专辑分组与剩余可见行数）：`LocalMusicDeleterTest` 13 例 + `LocalDeleteRulesTest` 9 例，均已写、待人工执行（本仓规约：AI 不编译、不跑 gradlew）。「删空专辑自动返回」的剩余可见行数判据抽为纯函数接缝 `remainingVisibleInAlbum`（与专辑视图同口径：排除文件夹后按专辑键清点），测试以独立字面量断言、不再与生产同源重算。
- 本仓维持手工验收（AI 不编译），清单覆盖：30+ 左滑删除 → 系统框 → 允许 → 行即时消失 + 重启不复活；取消 → 行不动；29 系统授权框链路；≤28 App 确认框 → 写入权限申请（API 23+ 首次）→ 删除，21–22 无权限框；拒绝写入权限 → 不删 + 提示、**行保持可播不灰显**；删除失败（外部已删）→ Toast + 该行灰显；长按编辑与左滑共存（含按住 >400ms 再横滑的竞争）；灰显行可删；权限被关时不露左滑；删正在播放的歌 → 走文件不可用；专辑页删除 → 分组/计数更新、失败提示当场弹；专辑删空 → 自动返回本地音乐页；进程被杀恢复到专辑页 → 不误灰显、真不存在的专辑不误 pop；飞行模式可删（纯本地）。

## Out of Scope

- 多选/批量删除、删除整个专辑/文件夹。
- 删除后清理播放队列快照、元数据覆盖层等派生数据。
- 把删除入口加到长按元数据面板、播放队列浮层等其他表面。
- 回收站/可恢复删除（系统媒体库删除即真删，不做 App 侧回收站）。
- 删除前「正在播放则停止/跳歌」的特殊处理。
- 监听媒体库变更做后台自动刷新（沿用进页面静默刷新）。

## Further Notes

- 左滑组件 `SwipeRevealRow` 为 internal（ui.components），同模块可用；行内容须不透明，白底由组件加在内容 Box 上，`LocalSongRow` 自身不画背景。灰显不是自绘背景，而是 `InkFaint` 文字 + `combinedClickable(enabled = false)`——只关行内点击、不影响外层左滑，故灰显行照样可删。
- `LocalSongRow` 用 `combinedClickable`（单击播放 + 长按编辑），包左滑后横向拖动与单击/长按共存（下载页/歌单页已有左滑 + 单击先例，长按共存为本特性新增验证点）。仓内无 swipe + 长按先例，已知失败模式：先按住不动 >400ms 再横滑 → 长按先触发弹出元数据面板、行可能停在半开位（滚动可收起）；按最简包裹实现，不预先加 `enabled` 回传机制，留真机验证。
- 与在途刷新的竞态（已知取舍，刻意不缓解）：静默刷新是整库重查 → 整份列表原子替换，若其查询在删除前执行、提交在删除后，被删的行会复活并重新落盘，直到下次刷新才消失。窗口毫秒级、入口刷新只在进页面时触发；不取消 refreshJob、不维护已删 id 黑名单。
- 系统框拉起自身失败的边缘（罕见）：`StartIntentSenderForResult` 在 IntentSender 发送失败时回 `RESULT_CANCELED` + `EXTRA_SEND_INTENT_EXCEPTION`。导航层 launcher 检查该 extra 并把 `launchFailed = true` 喂给 ViewModel，后者不喂接缝、直接按失败处理 → Toast「删除失败」、行不灰显（文件还在）；不再被归一为 `Cancelled` 而静默吞掉。真正的用户取消（CANCELED 且无该 extra）仍按接缝契约保持静默。
- 回调状态丢失的兜底：系统框 / 权限框开着时进程被杀，ViewModel 重建后 `pendingDeleteSong` 为 null（正常流程该字段必非空——动作发出前就写好）；两个回调发现 null 时先置「删除失败」提示再返回，让用户的操作有反馈而非静默。
- 删除链路全部本地、零网络请求；与「下载列表」删私有缓存是两条独立链路（下载删 App 私有文件无系统框，本地歌删系统媒体库有系统框）。
- 多存储卷：删除按快照里已存的、按卷限定的 content URI 逐卷生效，沿用 ADR 0022 多卷身份（不另构造 URI）。
- 快照落盘失败只记日志（`LocalMusicStore.writeAtomically`），故「重启不复活」不可静态观测、需真机验收；落盘异步（`viewModelScope`）并与刷新 / 排除开关共用同一把写锁，最后写入胜出。

## Comments

- 2026-11-14 实现完成：票据 01（caeea4a，合并 cc0036b）与票据 02（4ad4a38，合并 ea98f00）已合入集成分支 `feat/local-music-delete`（tip ea98f00）；票据 03 的文档同步与本 spec 文字对齐为 b390d8e，Comments 随后续提交入库（待编排层合入）。落地内容：`LocalMusicDeleter` 分版本删除接缝（30+ `createDeleteRequest` 系统框 / 29 `RecoverableSecurityException` 授权后重试 / ≤28 直接删）+ 纯路由与结果归一，Manifest 补 `WRITE_EXTERNAL_STORAGE(maxSdkVersion="29")`；两页歌行左滑「删除」接线（`LocalSongRow` 包 `SwipeRevealRow`），导航层双 launcher 与唯一一份 ≤28 App 确认框、`notice` Toast 上移（修专辑页静默）、删除成功即移除整库该行 + 专辑分组/计数重算 + 快照原子落盘、专辑删空自动返回、专辑页权限核对（`onAlbumPageEntered()` + `ON_RESUME`）。spec 文字已对齐代码事实与已定决策：≤28 顺序为 App 确认 → `startDelete` → 按需 WRITE 权限框 → 删除（用户故事 6）；运行时申请仅 ≤28 且 API 23+，21–22 安装即授予、29 走 RSE、Manifest 仍 `maxSdkVersion="29"`（原 :43/:45 冲突已消）；URI 直接用查询期构造并入盘的 `LocalSong.contentUri`，无第二构造函数；灰显 = `InkFaint` 文字 + `combinedClickable(enabled=false)` 而非自绘背景（白底由 `SwipeRevealRow` 提供，灰显行照样可删）；≤28 确认框精确文案 `删除本地歌曲` / `「${song.displayTitle()}」将从设备存储删除，其他应用也不再可见。` / `删除` / `取消`（两个 Text 走 M3 默认色）；并补记权限被关不露左滑、两处 `listState` + 滚动/视图切换收起、删空返回绑定删除成功动作（带同步算好的剩余可见行数）。**不新增 ADR 的判定经交付代码核对成立**：ADR 0018 预留的扩展路径、身份/权限/快照仍在 ADR 0022 内、不与 ADR 0020 冲突（只否决回写标签，不禁止删文件，且未清覆盖层），三条门槛均不成立（分版本路由是平台强制）；ADR 0018 范围枚举未改，分版本确认口径只记在本 spec。已知取舍：在途刷新可让被删行复活并重新落盘直到下次刷新（刻意不缓解）、长按 >400ms 再横滑的竞争仓内无先例（未加 `enabled` 回传，留真机）、`SendIntentException` 归一为 `Cancelled` → 静默无 Toast（罕见，记录未修）、`markUnavailable` 对已灰显行 no-op、快照写失败只记日志（「重启不复活」需真机）、派生数据（覆盖层/排除清单/队列快照）不清理。JVM 行为测试 12 + 8 例已写、未执行；各票勾选项为设备级验收清单，待人工编译后逐项真机确认（本仓规约：AI 不编译、不跑 gradlew）。Status 推进与归档留待评审修复后统一执行（照 `local-music-rescan` 先例：归档提交在评审修复合入之后）。
- 2026-11-14 评审修复：提交 2045f7a（接缝与纯规则 + 测试）、2961096（ViewModel 与导航层）与本次文档提交，落在 `ticket/lmd-review-fixes`（base 1132f31），待编排层合入 `feat/local-music-delete`。六项已接受修复全部落地：失败结果带 `fileUnavailable` 正面证据判定（仅 delete 非正行数才灰显，拒绝写入权限 / provider 异常 / 异常框结果码 / IntentSender 发送失败保持可播）、`SendIntentException` 经 `launchFailed` 呈现为「删除失败」Toast（不再静默）、回调丢状态（进程死亡 `pendingDeleteSong` 为 null）给提示、删空判据抽 `remainingVisibleInAlbum` 纯函数接缝并以独立字面量测试、失败/取消断言改内容级 `assertEquals`、spec/ui.md/票据02 与注释对齐失败口径。JVM 测试增至 `LocalMusicDeleterTest` 13 例 + `LocalDeleteRulesTest` 9 例（已写未执行）。仍开放的取舍不变：B7 在途刷新竞态（刻意不缓解）、B10 长按+左滑竞争（留真机）、快照写失败只记日志、派生数据不清理。被驳回的评审项未改（专辑页 `onAppResumed`、`sdkInt` 注入、滚动收起 helper 抽取、ADR 0018、API29 App 自有文件确认、B7 缓解）。Status 仍 `ready-for-agent`、三张票据仍 `open`、全部勾选项未动，归档留待编排层在评审修复合入后统一执行。
- 2026-11-14 归档：spec 与票据 01–03 全部 **resolved**。集成分支 `feat/local-music-delete`，tip `47ce6f6`。完整提交链：`71ee6f3`（spec/票据入库 + CONTEXT 词条）→ `caeea4a`（票据01，合并 `cc0036b`）→ `4ad4a38`（票据02，合并 `ea98f00`）→ `b390d8e`/`64b23ec`（票据03，合并 `1132f31`）→ `2045f7a`/`2961096`/`591fbbc`（评审修复，合并 `47ce6f6`）。双轴 code review（Standards / Spec）结论：**Standards 零硬伤**（ADR 0018/0020/0022 全部一致，仓内先例逐条对齐，无编译级问题）；**Spec 查出的硬伤已全部修复**——拒绝写入权限不再永久灰显可播文件（`Failure` 增 `fileUnavailable`，仅 delete 返回非正行数才置位）、IntentSender 拉起失败与进程死亡丢状态不再静默（一律 Toast「删除失败」）、删空判据抽为 `remainingVisibleInAlbum` 纯函数接缝并以独立字面量断言（原测试属重言式）。评审核驳的误报：专辑页 `ON_RESUME` 不是范围蔓延（`onAppResumed` 权限无变化即早退）、ADR 0018「专辑歌曲」指在线页故无矛盾、API29 App 自有文件零确认在本 App 不可达（下载落私有 filesDir、不产生 MediaStore 音频行）。JVM 行为测试 13 + 9 例已写、**未执行**。全部勾选项保持 `- [ ]` 未勾：它们是设备级验收清单，照 `local-music-rescan` 先例待人工编译后真机逐项确认（本仓规约：AI 不编译、不跑 gradlew）。
- 2026-11-25 归档：目录移至 `.scratch/archive/local-music-delete/`。spec 与票据 01–03 均 resolved；集成分支 `feat/local-music-delete` tip `47ce6f6`，main 合并 `1770340`。未完成项：设备级验收复选框待人工真机确认；JVM 测试 13 + 9 例已写未执行。