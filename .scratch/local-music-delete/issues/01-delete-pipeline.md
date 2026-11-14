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

- （待实现）