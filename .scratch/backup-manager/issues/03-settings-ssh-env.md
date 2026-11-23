# 03: 设置页 + SSH 通路 + .env 归档

> 完整上下文见 [spec](../spec.md)。

**What to build:** 设置页配置 SSH 凭据（密码或密钥文件二选一），经 Windows DPAPI（Electron safeStorage）加密落盘；连接目标固定为 qqt 生产服务器（root@101.132.159.145:22），不做可配置项。引擎的 SSH 端口接通真实适配器（远程执行命令 + 取回文件）。.env 备份任务端到端：SSH 读取服务器 `/opt/qqt-next/.env`，存为备份根目录下的日期归档（明文）。任务失败时行上显示具体原因（连不上 / 认证失败 / 文件不存在）。

**Blocked by:** 01（walking skeleton — keystore 归档端到端）

**Status:** resolved

- [ ] 设置页可保存 SSH 密码或密钥文件，凭据经 DPAPI 加密，明文不落盘
- [ ] 配置正确凭据后点 .env 任务「立即备份」，生成日期归档且内容与服务器 `.env` 一致
- [ ] SSH 连不上 / 认证失败 / 远程文件不存在时任务失败，行上显示可区分的错误原因
- [ ] .env 归档命名符合 spec 的清理规则（14 份清理由 02 的共用规则承接）
- [ ] 应用不存储 MySQL 密码与 OSS AK（前者靠服务器 `/root/.my.cnf`，后者脚本读 `.env`）
- [ ] 引擎测试（SSH 桩）覆盖：取回成功落盘、连接失败、认证失败、远程文件不存在
- [ ] 手工验证：真实服务器 `.env` 取回成功；错误密码报明确错误

## Comments

**2026-11-14 — resolved（implement-spec）**
- 实现提交 `d8ff88f`，并入 main `035c0fd`（--no-ff）。
- 交付：设置页 SSH 凭据（密码/密钥二选一）经 DPAPI（safeStorage）加密落盘、明文不落盘；固定 `root@101.132.159.145:22`；真实 SSH 适配器（ssh2 exec + SFTP fetchFile，可区分未配置/连接失败/认证失败/远程文件不存在）；.env 归档端到端（`server-env\<日期>.env`，明文）；顺带建 4-tab 导航壳 + 抽出 TaskListView + History/Tutorial 占位。
- 引擎测试（SSH 桩）覆盖取回成功/连接失败/认证失败/文件不存在，全绿。不存 MySQL 密码与 OSS AK。
- 评审修复 `da0ff94`：settings-store 改用共用纯 TS 序列化队列 `queue.ts`；ssh-adapter 抽出 `createFinish` settle 守卫。
- 勾选项保留未勾：真实服务器 .env 取回 + 错误密码报错待人工凭据确认。