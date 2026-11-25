# 01: 导入接缝与纯函数（数据层）

**What to build:** 新增 `LocalMusicImporter`（data/local）作为唯一写侧接缝（镜像 `LocalMusicDeleter`）：`suspend fun importAudio(sourceUri: Uri): LocalImportResult`，UI 与 ViewModel 只调它、不直接碰 MediaStore 写入 API。结果归一 `LocalImportResult`（sealed，镜像 `LocalDeleteResult` / `LocalLibraryResult` 风格）：Success / Failure（带可空原因）。实现 `MediaStoreImporter(context, sdkInt)`（与 `MediaStoreLibrarySource` / `MediaStoreDeleter` 同口径注入 sdkInt）：Android 10+（API 29+）`MediaStore.Audio.Media` 插入，`RELATIVE_PATH = "Music/导入/"`，`IS_PENDING = 1` 期间写入字节、写完置 0（失败不暴露半截文件，清理 pending 行）；App 拥有自己创建的行，不需要新权限。Android 9 及以下（≤28）写公共 `Music/导入/` 目录 + `MediaScannerConnection` 触发扫描（`WRITE_EXTERNAL_STORAGE` 清单已有，maxSdkVersion 29）。纯函数（JVM 可测）：`importRoute(sdkInt)` 版本路由（MediaStore 插入 / 直写 + 扫描，镜像 `deleteRoute`）；`resolveImportFileName(desired, existingNames)` 目标目录重名时加后缀、保留扩展名，保证「各自成行、不覆盖」（含无扩展名/空名等边界）；展示名推导（接缝 impl 从来源 URI 尾段 / 文件名取原始名，纯函数做规范化）。每次导入生成新文件、新媒体库行（各自成行、不去重不覆盖，沿用 ADR 0022）。JVM 测试 `LocalImportRulesTest` 照 `LocalMusicDeleterTest` 先例：只测路由/命名/归一的输入输出，独立字面量断言、不重言式。框架 IO（ContentResolver 读写、MediaStore 插入、扫描）留真机手工验收。

**Blocked by:** None (can start immediately).

**Status:** resolved

- [ ] API 29+ 导入落 `Music/导入/`、媒体库行创建；IS_PENDING 流程不暴露半截文件
- [ ] API ≤28 导入写公共 `Music/导入/` 并触发扫描，行入库
- [ ] 同一首歌导两遍，库里各自成行出现两条（不去重不覆盖）
- [ ] 目标目录已有同名文件时自动加后缀，旧文件不被覆盖
- [ ] 导入失败不留半截文件（pending 行 / 临时文件清理）
- [ ] 无音频读取权限时导入照样落库成功（来源 URI 读权限 + 写自己创建的行）
- [ ] Manifest 不新增权限
- [ ] 飞行模式下可导入（纯本地、零网络）

## Comments

- 2026-11-14 完成。提交 e07b270（合并 a2ba40c），2 个文件：新增 `data/local/LocalMusicImporter.kt`（316 行）与 `LocalImportRulesTest.kt`（190 行，24 例）。唯一写侧接缝落地：接口单步 `suspend importAudio(sourceUri): LocalImportResult`（Success / Failure(cause?)，镜像 LocalDeleteResult、无 Cancelled）；`MediaStoreImporter(context, sdkInt)` 按 `importRoute` 分版本——29+ `MediaStore.Audio.Media` 插入自有行（`RELATIVE_PATH="Music/导入/"` 尾斜杠、`IS_PENDING=1` 写入→置 0 发布，任何失败 `resolver.delete` 清自有 pending 行不留半截；发布影响 ≤0 行也按失败清理）；≤28 公共 `Music/导入/` 临时文件 + rename（`target.exists()` 二次防覆盖）+ `scanFile` best-effort（扫描失败仅记日志仍 Success，系统周期扫描兜底）。纯函数：`importRoute`（29 分界，29 必须 MediaStore——清单无 requestLegacyExternalStorage）、`resolveImportFileName`（重名后缀 `name (2).ext` 从 2 递增、扩展名按最后一个点切分且点号开头视作无扩展名、空名兜底后仍参与重名解析）、`normalizeImportDisplayName`（去 `/ \ : * ? " < > |` 与控制字符、trim、空/纯点号兜底 `DEFAULT_IMPORT_NAME="导入音频.mp3"`——带 .mp3 保证 MediaStore 与扫描器识别为音频进 IS_MUSIC）、`legacyImportRequiresWriteGrant`（仅 DirectWrite 且 23+）。展示名先查来源 provider `OpenableColumns.DISPLAY_NAME`、取不到退回 URI 尾段。重名查询只查主卷 `EXTERNAL_CONTENT_URI` + RELATIVE_PATH 精确匹配（不合并 SD 卡卷）、不排除别家 pending 行（更宽重名网）；查询→插入无锁，MediaStore 插入自身同名自动加后缀兜底（系统行为）。**已裁定取舍（spec 缺口）**：≤28 未授予 WRITE_EXTERNAL_STORAGE 直接 Failure、不弹运行时申请框——spec 钉死单步 suspend 接缝、不镜像删除两步动作机；≤28 READ/WRITE 同属 STORAGE 权限组，授过读即有写，影响面极小（21–22 安装即授予）。JVM 测试 24 例已写、未执行（本仓规约：AI 不编译、不跑 gradlew）。勾选项为设备级验收清单，留待人工编译后真机逐项确认。
- 2026-11-14 归档：Status open → resolved。评审修复合入集成分支（9d698be，合并 c2f7d11）：FIX1 ≤28 大小写撞名加后缀 + 有限重试（resolveImportFileName 加 ignoreCase 参数默认 false、29+ 精确匹配语义不变，resolveLegacyTarget 重列目录重解析上限 3 次、超限失败不覆盖）、FIX2 无扩展名来源名补 .mp3 保证进库、FIX5 两链路共享 desiredImportName；纯函数测试 24→30 例已写未执行。集成分支 feat/local-music-import tip c2f7d11，随用户请求合并 main 并打包 release（本次编译为用户显式授权，禁编译规约的当次例外）。勾选项为设备级验收清单，保持未勾，待人工编译后真机逐项确认（含 ≤28 `Song.mp3` 撞 `song.mp3` 出后缀、微信语音裸名导入后库里可见）。