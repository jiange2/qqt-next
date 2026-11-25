# 01: Walking skeleton — keystore 归档端到端

> 完整上下文见 [spec](../spec.md)（共识、硬边界、测试接缝均在 spec，本票只描述增量）。

**What to build:** 项目初始化（Electron + Vue3 + TS + Vitest，独立新仓库，目标目录 `D:\Project\backup-manager`），应用作为普通窗口程序启动。任务列表页显示 keystore 备份任务行，点「立即备份」经 IPC 调用备份引擎的唯一入口：把本机 Android 工程的 `release.keystore` 与 `keystore.properties` 复制成备份根目录下的日期归档，行上显示上次备份时间与结果，运行历史持久化到应用数据目录（重启仍在）。备份引擎是纯 Node/TS 模块、零 Electron 依赖，外部世界以四个注入口接入（SSH、脚本执行器、文件系统根目录、时钟）；同一任务防重入、不同任务互不阻塞在引擎层落地。

**Blocked by:** None (can start immediately)

**Status:** resolved

- [ ] 应用启动，任务列表页显示 keystore 任务行（任务名、上次时间、结果、「立即备份」按钮）
- [ ] 点「立即备份」在备份根目录生成含两个文件的日期归档，行更新为成功与本次时间
- [ ] keystore 源文件缺失时任务失败，行上显示明确错误原因
- [ ] 运行历史写入应用数据目录的 JSON，应用重启后历史记录仍在
- [ ] 同一任务运行中拒绝重复触发（返回「运行中」状态）；不同任务互不阻塞
- [ ] 引擎测试（Vitest 纯 Node，真实临时目录 + 注入时钟）覆盖：归档命名、成功路径、源缺失报错、历史写入、防重入
- [ ] GLOSSARY.md 创建，含 spec 四个术语：备份任务 / 镜像 / 日期归档 / 恢复教程
- [ ] qqt 仓库零改动

## Comments

**2026-11-14 — resolved（implement-spec）**
- 实现提交 `e23fd1f`，并入集成分支 main `324d471`（--no-ff）。
- 交付：独立新仓库 Electron+Vue3+TS+Vitest 脚手架；纯 Node/TS 备份引擎（零 Electron 依赖）+ 四注入口（SSH/脚本执行器/backupRoot/时钟）+ keystoreSources/historyDir；keystore 归档端到端（`android\<日期>\` 含两文件）；任务列表页 + IPC + 运行历史 JSON（%APPDATA%，重启仍在）；同任务防重入、不同任务互不阻塞；GLOSSARY.md 四术语。
- 引擎测试（Vitest 纯 Node，真实临时目录 + 注入时钟）覆盖命名/成功/源缺失/历史/防重入，全绿。
- 勾选项保留未勾：应用启动 / 真机 GUI 走查待人工确认。qqt 仓库零改动（仅只读消费 keystore 源）。