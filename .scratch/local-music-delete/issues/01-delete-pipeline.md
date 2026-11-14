# 01: 分版本删除链路与权限

**What to build:** 新增 `LocalMusicDeleter`（data/local）作为唯一删除接缝：输入本地歌曲 content URI，按系统版本路由三条链路——Android 11+（API 30+）`MediaStore.createDeleteRequest` + `startIntentSenderForResult` 系统确认框；Android 10（API 29）`resolver.delete` 捕获 `RecoverableSecurityException` → 系统授权框 → 重试；Android 9 及以下（≤28）运行时申请 `WRITE_EXTERNAL_STORAGE` → `resolver.delete` 直接删。输出归一化删除结果（成功 / 失败 / 用户取消）。系统框经 `ActivityResultLauncher<IntentSenderRequest>` 拉起（注册在 UI 层），接缝负责版本路由与结果归一、不持有 Activity。Manifest 新增 `WRITE_EXTERNAL_STORAGE`（`maxSdkVersion=29`），仅 ≤29 申请，30+ 不持有。文件已被外部删除/URI 失效时返回失败结果而非崩溃。

**Blocked by:** None (can start immediately).

**Status:** open

- [ ] API 30+ 删除拉起系统确认框，允许后文件真删、其他应用不可见
- [ ] API 30+ 系统框取消 → 不删、返回取消结果
- [ ] API 29 删除触发系统授权框（RecoverableSecurityException），授权后重试成功
- [ ] API ≤28 首次删除申请 WRITE_EXTERNAL_STORAGE，授权后删除成功
- [ ] API ≤28 拒绝写入权限 → 不删、返回失败/提示
- [ ] Manifest WRITE_EXTERNAL_STORAGE maxSdkVersion=29；30+ 不申请不持有
- [ ] 文件已被外部删除/URI 失效 → 返回失败结果（不崩溃）
- [ ] 多卷（SD 卡）歌曲按 content URI 删除生效

## Comments

- 2026-11-14 完成。提交 caeea4a（合并 cc0036b），3 个文件：新增 `data/local/LocalMusicDeleter.kt` 与 `LocalMusicDeleterTest.kt`，`AndroidManifest.xml` 补权限。唯一删除接缝落地：`MediaStoreDeleter`（注入 application Context + sdkInt，与 `MediaStoreLibrarySource` 同口径）按版本路由三条链路——30+ `MediaStore.createDeleteRequest` → 系统删除框（允许后由系统执行删除）；29 `resolver.delete` 捕获 `RecoverableSecurityException` → 系统授权框 → 授权后重试；≤28 直接删。交接为「动作 → 回调」：`startDelete` / `onSystemDialogResult` / `onWritePermissionResult` 产出 `LaunchSystemDialog`（自带接缝构造好的 `IntentSenderRequest`）/ `RequestWritePermission` / `Finished`，结果归一为 `Success` / `Failure(cause)` / `Cancelled`；不持 Activity、不持 launcher、无状态（待删歌曲由 UI 保管），URI 直接 `Uri.parse(song.contentUri)`（查询期已按卷 + 行 ID 构造好并入盘，无第二个构造函数）。版本路由、写入权限门槛、系统框结果码分类、删除行数与系统框结果归一全部为纯函数（JVM 可测）。Manifest 补 `WRITE_EXTERNAL_STORAGE` `android:maxSdkVersion="29"`。≤28 与 ≤29 的口径冲突已裁定并实现：**运行时申请只在 ≤28 链路且 API 23+**（29 走 RSE，scoped storage 下 WRITE 不授予删除能力；Manifest 声明仍保留 maxSdkVersion=29），**API 21–22 无运行时权限框、清单声明即安装时授予**，跳过申请直接删（与 `hasAudioPermission` 对 sdkInt<23 返回 true 同口径），30+ 不申请不持有。文件已被外部删除 / URI 失效 → 失败结果不崩溃（`delete` 返回 0 行、`createDeleteRequest` 抛错均归一为 `Failure`）。JVM 行为测试 12 例已写、未执行（本仓规约：AI 不编译、不跑 gradlew）。勾选项为设备级验收清单（分版本系统框与授权重试、多卷 SD 卡按 content URI 删除、清单 maxSdk 在 30+ 不持有），留待人工编译后真机逐项确认。