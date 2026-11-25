# 06: 恢复教程页

> 完整上下文见 [spec](../spec.md)。

**What to build:** 应用新增恢复教程页：实时读取备份根目录下的两份灾备手册（backup-guide 与 full-deployment-guide），markdown 渲染展示。手册更新后教程自动跟新。纯只读——应用不做任何恢复功能，恢复照手册命令手工执行。

**Blocked by:** 01（walking skeleton — keystore 归档端到端）

**Status:** resolved

- [ ] 教程页完整渲染两份手册（标题 / 表格 / 代码块正确显示）
- [ ] 修改手册文件后重新打开页面即见新内容（实时读取，不缓存旧内容）
- [ ] 手册文件缺失时给出明确提示
- [ ] 页面无任何恢复操作入口（纯只读）
- [ ] 手工验证：对照手册原文检查渲染，表格与命令块不失真

## Comments

**2026-11-14 — resolved（implement-spec）**
- 实现提交 `2c592e6`，并入 main `63e30f9`（--no-ff）。
- 交付：恢复教程页实时读取备份根目录 `backup-guide.md` + `full-deployment-guide.md`，markdown-it 渲染（标题/表格/代码块）；手册更新即跟新（不缓存）；缺失明确提示；纯只读、无任何恢复操作入口（惰性链接）。
- guide-reader 测试覆盖读取/缺失，全绿。
- 勾选项保留未勾：对照手册原文检查渲染失真待人工 GUI 确认。