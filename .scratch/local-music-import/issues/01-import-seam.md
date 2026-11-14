# 01: 导入接缝与纯函数（数据层）

**What to build:** 新增 `LocalMusicImporter`（data/local）作为唯一写侧接缝（镜像 `LocalMusicDeleter`）：`suspend fun importAudio(sourceUri: Uri): LocalImportResult`，UI 与 ViewModel 只调它、不直接碰 MediaStore 写入 API。结果归一 `LocalImportResult`（sealed，镜像 `LocalDeleteResult` / `LocalLibraryResult` 风格）：Success / Failure（带可空原因）。实现 `MediaStoreImporter(context, sdkInt)`（与 `MediaStoreLibrarySource` / `MediaStoreDeleter` 同口径注入 sdkInt）：Android 10+（API 29+）`MediaStore.Audio.Media` 插入，`RELATIVE_PATH = "Music/导入/"`，`IS_PENDING = 1` 期间写入字节、写完置 0（失败不暴露半截文件，清理 pending 行）；App 拥有自己创建的行，不需要新权限。Android 9 及以下（≤28）写公共 `Music/导入/` 目录 + `MediaScannerConnection` 触发扫描（`WRITE_EXTERNAL_STORAGE` 清单已有，maxSdkVersion 29）。纯函数（JVM 可测）：`importRoute(sdkInt)` 版本路由（MediaStore 插入 / 直写 + 扫描，镜像 `deleteRoute`）；`resolveImportFileName(desired, existingNames)` 目标目录重名时加后缀、保留扩展名，保证「各自成行、不覆盖」（含无扩展名/空名等边界）；展示名推导（接缝 impl 从来源 URI 尾段 / 文件名取原始名，纯函数做规范化）。每次导入生成新文件、新媒体库行（各自成行、不去重不覆盖，沿用 ADR 0022）。JVM 测试 `LocalImportRulesTest` 照 `LocalMusicDeleterTest` 先例：只测路由/命名/归一的输入输出，独立字面量断言、不重言式。框架 IO（ContentResolver 读写、MediaStore 插入、扫描）留真机手工验收。

**Blocked by:** None (can start immediately).

**Status:** open

- [ ] API 29+ 导入落 `Music/导入/`、媒体库行创建；IS_PENDING 流程不暴露半截文件
- [ ] API ≤28 导入写公共 `Music/导入/` 并触发扫描，行入库
- [ ] 同一首歌导两遍，库里各自成行出现两条（不去重不覆盖）
- [ ] 目标目录已有同名文件时自动加后缀，旧文件不被覆盖
- [ ] 导入失败不留半截文件（pending 行 / 临时文件清理）
- [ ] 无音频读取权限时导入照样落库成功（来源 URI 读权限 + 写自己创建的行）
- [ ] Manifest 不新增权限
- [ ] 飞行模式下可导入（纯本地、零网络）

## Comments