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