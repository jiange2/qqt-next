# 本地音乐删除（左滑删物理文件）

Status: ready-for-agent

> 延续 ADR 0022（MediaStore 系统媒体库）与 ADR 0018（行内左滑删除），不推翻任何 ADR。元数据覆盖层（ADR 0020）、排除文件夹、歌曲/专辑视图、播放接入均不动。

## Problem Statement

本地音乐页现在只能播放和编辑元数据，不能删除。用户要清理设备上的音频文件（删掉不想要的歌）时，得退出 App 去文件管理器或系统音乐 App 操作，本地音乐管理缺一个「删除」闭环。注意「删除」与现有「排除文件夹」是两回事：排除只是本 App 不展示、文件还在；删除是把音频文件从设备存储真正删掉，删后其他应用也不再可见。

## Solution

本地音乐页与本地专辑页的歌行增加行内左滑「删除」（复用 ADR 0018 的 `SwipeRevealRow`，样式对齐下载页），删除经系统媒体库删除链路真正移除设备上的音频文件，全系统版本支持：Android 11+ 走官方 `createDeleteRequest`（系统确认框即唯一确认）、Android 10 走 `RecoverableSecurityException` 系统授权、Android 9 及以下申请 `WRITE_EXTERNAL_STORAGE` 后直接删（App 内确认框兜底）。删除成功该行即时从列表消失并落盘快照；失败 Toast 提示。长按元数据编辑、排除文件夹、播放接入全部不动。

## User Stories

1. 作为本地音乐听众，我希望在本地音乐页左滑歌行露出「删除」按钮，以便不退出 App 就能清理设备上的歌。
2. 作为本地音乐听众，我希望在本地专辑详情页同样能左滑删除歌行，以便两处管理一致。
3. 作为本地音乐听众，我希望删除的是设备存储里的音频文件本身（其他播放器也不再可见），以便真正清理空间，而不只是本 App 藏起来。
4. 作为本地音乐听众，我希望删除前有一次确认，以便不会误删。
5. 作为本地音乐听众，我希望 Android 10+ 的确认就是系统删除框、不再多弹一个 App 框，以便确认一次就够。
6. 作为本地音乐听众，我希望 Android 9 及以下删除时先申请存储写入权限、再弹 App 确认框，以便老设备也能删。
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
- 不做多选/批量删除；删除入口只有左滑一处（长按元数据面板不加删除按钮）。

### 分版本删除链路（新接缝 LocalMusicDeleter）

- 唯一新接缝 `LocalMusicDeleter`（data/local）：输入本地歌曲 content URI（按卷 + 行 ID 构造，沿用 `LocalSong`），按系统版本路由三条链路，输出归一化删除结果（成功 / 失败 / 用户取消）。UI 与 ViewModel 只调它，不直接碰 MediaStore 删除 API。
- Android 11+（API 30+）：`MediaStore.createDeleteRequest(resolver, uris)` → `startIntentSenderForResult` 拉系统确认框 → 用户允许后系统执行删除。无需任何写入权限（已持音频读取权限即可）。
- Android 10（API 29）：`resolver.delete(uri)` 捕获 `RecoverableSecurityException` → 用其 IntentSender 拉系统授权框 → 授权后重试删除。
- Android 9 及以下（API ≤28）：运行时申请 `WRITE_EXTERNAL_STORAGE` → `resolver.delete(uri)` 直接删。
- 系统框经 `ActivityResultLauncher<IntentSenderRequest>` 拉起（注册在 UI 层，沿用现有权限申请/Activity result 模式）；`LocalMusicDeleter` 负责版本路由与结果归一，不持有 Activity。
- Manifest 新增 `WRITE_EXTERNAL_STORAGE`（`maxSdkVersion=29`），仅 ≤29 删除时申请；30+ 不申请、不持有。

### 确认交互

- Android 10+：系统删除/授权框即唯一确认，App 不再叠加自己的确认框（下载页有 App 框是因为删私有缓存无系统框，本地歌不同）。
- Android 9 及以下：无系统框，App 内 `AlertDialog` 确认兜底（文案对齐下载页删除确认）；此 App 框由 UI 层在调用删除前弹出。
- 用户在系统框/App 框取消 → 不删、行保持原样。

### 列表联动与快照

- 删除成功：从 ViewModel 的 `_songs` 即时移除该行 → 专辑分组/计数随之重算 → 库快照落盘（`LocalMusicStore` 原子写），不等下次扫描。媒体库行已删，下次刷新自然不再出现。
- 删除失败（含文件已被外部删除、URI 失效）：Toast「删除失败」并刷新该行状态（走现有不可用标记链路）。
- 不清理派生数据：播放队列快照、元数据覆盖层（ADR 0020）不做连带清理——与 ADR 0022 现有取舍一致（文件删除重加行 ID 变新、覆盖丢失为已知取舍）。队列里残留的同一首真播不了时走现有「文件不可用」链路。

### 播放中删除

- 不做特殊处理：不主动停止/跳歌。缓冲未断继续播，真断了走现有 `_unavailableLocalSongId` → `markUnavailable` + Toast「文件不可用」→ 行灰显链路。

### 文档

- CONTEXT.md「本地音乐」节新增「删除（本地歌曲）」词条（已落，与「排除文件夹」明确区分）。
- 不新增 ADR：本特性为增量功能，与 ADR 0018（左滑删除）、ADR 0022（MediaStore）一致，无架构推翻；分版本链路与确认交互决策记入本 spec。

## Testing Decisions

- 好的测试只测外部行为：给 `LocalMusicDeleter` 喂 URI 与版本条件、断言删除结果与列表/快照最终形态，不测 MediaStore 内部。
- 接缝：`LocalMusicDeleter` 之下的结果处理（成功移除行 + 落盘、失败 Toast + 刷新、取消不动）与专辑分组联动为可注入逻辑；分版本系统框链路（`createDeleteRequest` / `RecoverableSecurityException` / WRITE 申请）依赖系统交互，以真机手工验收为主。
- 本仓维持手工验收（AI 不编译），清单覆盖：30+ 左滑删除 → 系统框 → 允许 → 行即时消失 + 重启不复活；取消 → 行不动；29 系统授权框链路；≤28 权限申请 + App 确认框 + 删除；拒绝写入权限 → 不删 + 提示；删除失败（外部已删）→ Toast；长按编辑与左滑共存；灰显行可删；删正在播放的歌 → 走文件不可用；专辑页删除 → 分组更新；飞行模式可删（纯本地）。

## Out of Scope

- 多选/批量删除、删除整个专辑/文件夹。
- 删除后清理播放队列快照、元数据覆盖层等派生数据。
- 把删除入口加到长按元数据面板、播放队列浮层等其他表面。
- 回收站/可恢复删除（系统媒体库删除即真删，不做 App 侧回收站）。
- 删除前「正在播放则停止/跳歌」的特殊处理。
- 监听媒体库变更做后台自动刷新（沿用进页面静默刷新）。

## Further Notes

- 左滑组件 `SwipeRevealRow` 为 internal（ui.components），同模块可用；行内容须不透明（组件已置白底），灰显行自绘背景不受影响。
- `LocalSongRow` 现用 `combinedClickable`（单击播放 + 长按编辑），包左滑后须验证横向拖动手势与单击/长按不冲突（下载页/歌单页已有左滑 + 单击共存先例，长按共存为本特性新增验证点）。
- 删除链路全部本地、零网络请求；与「下载列表」删私有缓存是两条独立链路（下载删 App 私有文件无系统框，本地歌删系统媒体库有系统框）。
- 多存储卷：content URI 按卷构造，删除按 URI 逐卷生效，沿用 ADR 0022 多卷身份。

## Comments

- （待实现）