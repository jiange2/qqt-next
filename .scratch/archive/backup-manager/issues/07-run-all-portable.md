# 07: 「全部备份」+ 便携 exe 交付

> 完整上下文见 [spec](../spec.md)。

**What to build:** 任务列表顶部增加「全部备份」按钮，经引擎 `runAll()` 并行触发四条备份任务（各任务受防重入约束，单任务失败不拖累其他任务）。electron-builder 打包为便携（portable）单 exe，免安装双击即用。对四条任务黄金路径做完整手工走查，确认整套应用可交付日常使用。

**Blocked by:** 02（保留份数清理 + 历史产物页）、04（数据库 dump 任务）、05（OSS 镜像任务）、06（恢复教程页）

**Status:** resolved

- [ ] 「全部备份」并行触发四条任务，各行独立显示进度 / 结果
- [ ] 单任务失败不拖累其他任务，失败行显示原因
- [ ] 某任务运行中时「全部备份」不重复触发该任务
- [ ] 引擎测试覆盖 `runAll` 并行触发与单任务失败隔离
- [ ] 打包产物为便携单 exe，双击即用、无需安装
- [ ] 手工走查：四条任务黄金路径全部成功（真实服务器 + 真实 OSS 增量），历史 / 清理 / 教程 / 设置页均正常

## Comments

**2026-11-14 — resolved（implement-spec）**
- 实现提交 `96bdb85`，并入 main `3353b36`（--no-ff）。
- 交付：「全部备份」按钮经引擎 `runAll()` 并行触发四任务（各任务防重入、单失败不拖累其他、各行独立进度/结果）——runAll 引擎/IPC/UI/测试由 01/04/05 增量建成，本票核验完整；electron-builder win **portable 单 exe**（`electron-builder.yml` + `dist:win`，npmRebuild:false，asar，输出 `release/`）。
- 产物：`release\backup-manager-0.1.0-portable.exe`（~106.9 MiB，免安装双击即用）。
- 冒烟（打包 exe，CDP）14/14 PASS：4 行、全部备份并行 + allBusy 守卫、逐行结果/错误、4 导航页；keystore 成功、SSH 任务未配置凭据优雅失败（预期）、OSS 真实脚本运行。
- 代码评审修复后 main `f8d1234`：60/60 测试、typecheck/build 绿、portable exe 重建 exit 0。
- 勾选项保留未勾：真实服务器 + 真实 OSS 四任务黄金路径完整手工走查待人工凭据确认。