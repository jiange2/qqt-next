# 02: 意图胶水与 ViewModel 编排

**What to build:** MainActivity 新增 intent-filter：`ACTION_VIEW` + `ACTION_SEND`，MIME 限 `audio/*`，`content` scheme（VIEW 取 `intent.data`，SEND 取 `EXTRA_STREAM`）；对任何 App 的音频打开/分享生效，不止微信/QQ。`onCreate`（冷启动）与 `onNewIntent`（在运行）解析意图：取音频 URI → 调 `LocalMusicViewModel.importAudio(uri)`；必要时调整 launchMode 保证在运行复用实例走 `onNewIntent`。胶水只做「解析 URI + 转交」（MIME audio/* 胶水层再校验一次），不含复制/命名/刷新逻辑。先只处理单文件；`ACTION_SEND_MULTIPLE` 本期不支持。ViewModel 新增 `importAudio(uri)` 入口：调接缝 → Success 触发 `refresh(notify = false)`（复用现有整库刷新管线）+ `notice`「已导入到本地音乐」；Failure → `notice`「导入失败」。不自动播放、不重建播放队列、不动当前播放。注入沿用现有内部构造器（与 `source` / `deleter` 同口径），fake 接缝即可 JVM 测编排（照 source / deleter 的 fake 注入范式：断言成功→刷新+提示 / 失败→提示 / 不播放的可观察结果，不断言内部调用顺序）。提示经现有 `notice` StateFlow → 导航层 collector 弹 Toast（两页共用口径）。qqt 界面内不设导入按钮。

**Blocked by:** 01: 导入接缝与纯函数（数据层）.

**Status:** open

- [ ] 微信音频「用其他应用打开」→ 选 qqt，导入成功
- [ ] 微信「分享」音频 → 选 qqt，导入成功
- [ ] QQ「用其他应用打开 / 分享」→ 选 qqt，导入成功
- [ ] 其他 App（文件管理器、网盘、其他播放器）打开/分享音频选 qqt 都能导入
- [ ] qqt 没在运行时冷启动也能完成导入
- [ ] qqt 已在运行时复用实例走 onNewIntent 完成导入
- [ ] 成功轻提示「已导入到本地音乐」；失败轻提示「导入失败」
- [ ] 导入后回到本地音乐页就能看到这首歌（自动刷新，不用手动重扫）
- [ ] 导入只落库：不自动播放、不重建播放队列、不打断当前播放
- [ ] 分享非音频文件给 qqt 不被接收
- [ ] qqt 界面里找不到「导入」按钮
- [ ] 导入的歌能播放、能编辑信息、能批量管理、能左滑删除
- [ ] 导入的歌按现有本地专辑归并规则分组（有专辑标签按「专辑名 + 专辑艺术家」，无标签按文件夹名兜底聚成「导入」专辑）
- [ ] 导入的歌重启 App 后仍在（随库快照落盘）
- [ ] 导入的歌文件被外部删除后，下次刷新自然消失（行 ID 身份）
- [ ] 导入的歌可被「排除文件夹」移出展示（`Music/导入/`）
- [ ] 无音频权限时导入照样落库；进本地音乐页走现有权限引导、授权后可见

## Comments