# 02: 意图胶水与 ViewModel 编排

**What to build:** MainActivity 新增 intent-filter：`ACTION_VIEW` + `ACTION_SEND`，MIME 限 `audio/*`，`content` scheme（VIEW 取 `intent.data`，SEND 取 `EXTRA_STREAM`）；对任何 App 的音频打开/分享生效，不止微信/QQ。`onCreate`（冷启动）与 `onNewIntent`（在运行）解析意图：取音频 URI → 调 `LocalMusicViewModel.importAudio(uri)`；必要时调整 launchMode 保证在运行复用实例走 `onNewIntent`。胶水只做「解析 URI + 转交」（MIME audio/* 胶水层再校验一次），不含复制/命名/刷新逻辑。先只处理单文件；`ACTION_SEND_MULTIPLE` 本期不支持。ViewModel 新增 `importAudio(uri)` 入口：调接缝 → Success 触发 `refresh(notify = false)`（复用现有整库刷新管线）+ `notice`「已导入到本地音乐」；Failure → `notice`「导入失败」。不自动播放、不重建播放队列、不动当前播放。注入沿用现有内部构造器（与 `source` / `deleter` 同口径），fake 接缝即可 JVM 测编排（照 source / deleter 的 fake 注入范式：断言成功→刷新+提示 / 失败→提示 / 不播放的可观察结果，不断言内部调用顺序）。提示经现有 `notice` StateFlow → 导航层 collector 弹 Toast（两页共用口径）。qqt 界面内不设导入按钮。

**Blocked by:** 01: 导入接缝与纯函数（数据层）.

**Status:** resolved

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

- 2026-11-14 完成。提交 689e60f（合并 c701e4b），7 个文件。Manifest：MainActivity 加 `singleTask`（在运行复用走 onNewIntent 的必要条件，standard 会新建 Activity/ViewModel）+ 两个 filter——VIEW 带 `content` scheme + `audio/*`；**SEND 仅 `audio/*` 不带 scheme（与 spec 字面的偏差，已裁定）**：SEND 意图只带 MIME、无 data URI，`IntentFilter.matchData` 在 filter 声明 scheme 时对 SEND 的空 scheme 必 NO_MATCH_DATA（源码核实），带 scheme 会令分享入口失效；content scheme 改由胶水层对 EXTRA_STREAM 防御性校验，spec 意图（只收音频 content URI）不变。MainActivity：`localMusicViewModel by viewModels()` 与 AppNavigation 的 Compose `viewModel()` 命中同一实例（同 Activity ViewModelStore + 默认 factory + 默认 key，无 owner 覆写；PlayerViewModel 先例同机制），**AppNavigation 零改动**；onCreate 末尾 + 新增 onNewIntent（super + setIntent）共用 handleImportIntent：VIEW 取 data、SEND 取 `IntentCompat.getParcelableExtra(EXTRA_STREAM, Uri::class.java)`（core 1.13.1 javap 核实）；MIME = intent.type ?: resolver.getType(uri)，拿不到拒收、audio/* 前缀忽略大小写；非 content 拒收；SEND_MULTIPLE 静默忽略；胶水不弹 Toast（提示走 VM notice → 导航层既有 collector，StateFlow 保值冷启动不丢）。VM：importer 字段 + internal 构造器加参（原构造器零调用方、无破坏）；`importAudio` → Success：`refresh(notify = false)` + notice「已导入到本地音乐」/ Failure：notice「导入失败」；与播放器零耦合（不引 PlayerViewModel、不调 playSong/skip*），刷新竞态沿用最后写入胜出不加锁。**测试基础设施（spec 缺口，已裁定）**：新增 `testImplementation` kotlinx-coroutines-test **1.8.1**（POM 依赖链核实：coil 2.7.0 → coroutines-android 1.8.1；lifecycle 2.8.4 声明 1.7.3 冲突取高；缓存仅 descriptor 无 jar，**首次人工编译需联网**拉取）+ `testOptions.unitTests.isReturnDefaultValues = true`（现有纯函数测试不调 android 方法、不受影响）；不引 Robolectric/mockk。VM 编排测试 3 例（成功→提示+新歌经 songs 可见 / 失败→提示+列表不动 / 无权限→成功提示+刷新静默跳过）：fake source/importer 经 internal 构造器注入，UnusedDeleter 被调即响亮失败（编排不越界信号）；StandardTestDispatcher + setMain，VM 硬编码 IO/Default 跳线程由 runTest 实时等待、挂起 first{} 落定；mockable jar 清空 Uri 静态初始化（AGP 8.5.2 MockableJarGenerator 字节码核实）→ 测试 source set `android.net.TestUri` 替身（包私有构造器同包继承，除 toString 外全抛、意外框架调用响亮失败）；测试 Application 覆写 getFilesDir 到 TemporaryFolder（否则 filesDir=null 快照落 cwd 用例间串库）。JVM 测试 3 例已写、未执行（本仓规约：AI 不编译、不跑 gradlew）。勾选项为设备级验收清单，留待人工编译后真机逐项确认（含 singleTask 三态：冷启动 / 在运行 / 桌面图标回栈）。
- 2026-11-14 归档：Status open → resolved。评审修复合入集成分支（9d698be，合并 c2f7d11）：FIX4 VM 测试 FakeImporter 拆 FakeSuccessImporter / FakeFailureImporter 两个无分支 fake（mocking.md「mock 内无分支」），三用例断言不变。集成分支 feat/local-music-import tip c2f7d11，随用户请求合并 main 并打包 release（本次编译为用户显式授权，禁编译规约的当次例外）。勾选项为设备级验收清单，保持未勾，待人工编译后真机逐项确认（含 singleTask 三态：冷启动 / 在运行 / 桌面图标回栈）。