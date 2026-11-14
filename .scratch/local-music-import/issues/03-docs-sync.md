# 03: 文档同步与归档

**What to build:** `android/doc/ui.md`、`android/doc/storage.md` 补导入节（入口、接缝、落库目录、刷新与提示），随实现票据同步；`android/project.md` 同步（intent-filter、目录树、权限无新增）；核对 CONTEXT.md「导入（本地音乐）」词条（grilling 期间已落地）与最终实现一致；spec 文字对齐代码事实、Comments 记录实现提交；判定不新增 ADR 成立（导入是可逆的新增能力，延续 ADR 0020/0022、无推翻，三条门槛不成立；「入口在来源 App 而非 qqt 按钮」的系统边界理由已在词条与 spec）；Status 推进与票据归档留待评审修复合入后统一执行（照 local-music-delete / local-music-rescan 先例）。

**Blocked by:** 01: 导入接缝与纯函数（数据层）, 02: 意图胶水与 ViewModel 编排.

**Status:** open

- [ ] ui.md / storage.md 导入节与最终实现一致（入口、接缝、落库目录、刷新与提示）
- [ ] project.md intent-filter 与目录树同步
- [ ] CONTEXT.md「导入（本地音乐）」词条与最终实现一致
- [ ] spec Status 推进 + Comments 记录实现提交与验收结论
- [ ] 确认不新增 ADR 的判定成立（无架构推翻）；如实现中出现推翻性决策再补 ADR
- [ ] 票据 01–03 勾选项经人工编译真机确认后归档

## Comments

- 2026-11-14 完成。提交 c4a103b（合并 c5b40ca），4 个文件：`android/doc/ui.md`（关键文件表补 MainActivity 行与 VM 导入编排职责、接线 bullets 补导入（含 SEND filter 不带 scheme 事实与原因）、LocalMusicScreen 照删除三段范本补导入入口/分版本落库/导入结果、注意事项补 LocalMusicViewModel 隐式共享所有权口径（机制+前提+失效时需显式传参））、`android/doc/storage.md`（关键文件表加 LocalMusicImporter.kt、库快照节补导入落库段：分版本/重名后缀/≤28 权限取舍/静默刷新整份快照重写/覆盖层与排除清单零特殊处理）、`android/project.md`（模块职责/目录树/MainActivity 注释 singleTask/权限句不新增）、`spec.md` 文字对齐代码事实（SEND filter 偏差与 IntentFilter.matchData 原因、≤28 未授 WRITE 直接 Failure 取舍、launchMode 落定 singleTask、Testing Decisions 更新 24+3 例与基础设施裁定、Comments 记录实现提交）。CONTEXT.md「导入（本地音乐）」词条核对与最终实现一致、未改。**不新增 ADR 判定对照交付代码核实成立**：可逆新增能力（去掉 filter/胶水/接缝即可，读/删/编辑/播放管线零改动），延续 ADR 0022（行 ID 身份、不去重、查询零改动自然收录）与 ADR 0020（只复制字节、编辑走覆盖层），三条门槛不成立——SEND 偏差是平台机制强制、≤28 取舍是单步接缝约束下的实现选择，均非架构推翻。Status 推进与归档留待评审修复合入后统一执行（照 local-music-delete / local-music-rescan 先例）；勾选项保持未勾，待人工编译后真机逐项确认。