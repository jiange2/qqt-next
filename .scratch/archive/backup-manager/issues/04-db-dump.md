# 04: 数据库 dump 任务

> 完整上下文见 [spec](../spec.md)。

**What to build:** 数据库 dump 备份任务端到端：复用 SSH 通路在生产服务器执行 mysqldump（参数按灾备手册 §2.2：`--single-transaction --quick --routines --triggers --default-character-set=utf8mb4`，gzip 压缩），取回 `.sql.gz` 落备份根目录，自动做非空 + gzip 完整性校验；校验失败删除残file、运行记失败。任务行显示结果。mysqldump 以 root 运行、依赖服务器 `/root/.my.cnf`（运维侧用户自理）。生产 3306 不公网开放，SSH 远程 dump 是唯一通路。

**Blocked by:** 03（设置页 + SSH 通路 + .env 归档）

**Status:** resolved

- [ ] 点「立即备份」生成 `qqt_next_<日期时间>.sql.gz` 于备份根目录，行显示成功
- [ ] 拉回的 dump 经非空 + gzip 完整性校验
- [ ] 校验失败时残file被删除、运行历史记失败、行显示失败原因
- [ ] dump 只存本地，无任何上传云存储的逻辑（尤其绝不进公共读媒体桶）
- [ ] dump 命名符合 spec 的清理规则（14 份清理由共用规则承接）
- [ ] 引擎测试（SSH 桩）覆盖：正常 dump、空文件、坏 gzip、SSH 断连四条路径及各自的历史记录结果
- [ ] 手工验证：真实服务器 dump 取回成功且可解压

## Comments

**2026-11-14 — resolved（implement-spec）**
- 实现提交 `d09ab0b`，并入 main `8224e2f`（--no-ff）。
- 交付：SSH 远程 mysqldump（§2.2 参数）| gzip → 取回 `db\qqt_next_<YYYY-MM-DD_HHMM>.sql.gz`（命名经编排层 PINNED）；`.part` 临时落盘 + 非空/gzip 完整性/解压非空校验，校验通过才 rename，失败删残file、记失败；dump 只存本地、无任何上传逻辑。
- **代码评审修复 `da0ff94`（并入 main `f8d1234`）**：远程命令加 `set -o pipefail`，使 mysqldump 失败（含部分 dump）经退出码守卫捕获，不再被 gzip 退出码掩盖；补 TDD 测试（部分 dump 修复前误判成功）。`validateDump` 保留为纵深防御。
- 引擎测试（SSH 桩）覆盖正常/空文件/坏 gzip/断连/部分 dump，全绿。
- 勾选项保留未勾：真实服务器 dump 取回 + 可解压待人工凭据确认。