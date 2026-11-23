# 03: 文档同步与归档

**What to build:** `android/doc/ui.md`：LocalMusicScreen 节补编辑模式（⋮ 菜单入口与权限门槛、选择态与勾选交互、顶栏 / 底栏、批量面板双字段与留空 = 不改、退出路径），LocalMetadataEditSheet 节补批量变体；`android/doc/storage.md`：覆盖层节补批量提交（一次整表原子写、与单首共用 editLock、空壳删键口径不变）。核对 CONTEXT.md「专辑艺术家 / 编辑模式 / 批量管理」词条与最终实现一致（含「专辑作者」弃用词）。spec 文字对齐代码事实（照 local-music-delete / view-switch 先例逐条核对）。票据 01 / 02 补实现 Comments。Status 推进与归档留待评审修复后由编排层统一执行；设备级验收清单保持未勾，待人工编译后真机逐项确认（本仓规约：AI 不编译、不跑 gradlew）。

**Blocked by:** 01, 02

**Status:** open

- [ ] ui.md 编辑模式入口 / 选择态 / 顶栏底栏 / 面板 / 退出路径与实现一致
- [ ] storage.md 批量提交口径与实现一致
- [ ] CONTEXT.md 三词条与实现一致（含「专辑艺术家」用词）
- [ ] spec 文字对齐代码事实，差异逐条记录
- [ ] 归档提交在评审修复合入之后（先例口径）

## Comments