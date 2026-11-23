# qqt 备份管理器（Windows 客户端）

Status: resolved

> 全新独立项目（目标目录 `D:\Project\backup-manager`），不改动 qqt 仓库任何代码。qqt 侧只消费两个既有资产：`scripts/sync-oss.ps1`（OSS 增量同步脚本）与 `backup/` 目录（备份根目录 + 两份灾备手册）。遵循 qqt ADR 0008/0010/0011（媒体内容加密/混淆，备份只搬字节）与 ADR 0005/0006（CDN 明文 + Referer 防盗链，由现有脚本处理）。

## Problem Statement

qqt 的线上数据只落在生产服务器（MySQL 库 `qqt_next`）和阿里云 OSS（媒体桶 `qqt7`）两处，加上开发机上不可再生的 Android 签名 keystore 和服务器上的 `.env`（含多个不可再生密钥）。灾备手册（backup-guide.md）早已定义「服务器 + 云 + 本地」三点备份基线，但本地这一点至今没有落地：数据库只有一份 2026-09-13 的手工 dump，OSS 只有一个跑了一半的镜像，keystore 和 `.env` 的本地副本靠手动复制。用户每次想备份都得手工 SSH、敲 mysqldump、scp、跑脚本，没有统一入口，也没有保留份数管理，备份做不做、做没做成功全凭记忆。

## Solution

一个 qqt 专用的 Windows 桌面客户端：打开后看到 4 条固定备份任务（数据库 dump、OSS 镜像、keystore 归档、服务器 .env 归档），每条显示上次备份时间与结果，点「立即备份」就跑，也可以一键「全部备份」。备份产物统一落在现有的 `D:\Project\qqt\backup\` 目录，按日期归档、自动保留最近 14 份（OSS 是单一镜像，不清理）。应用内提供历史产物浏览、渲染现成灾备手册的恢复教程页、以及只存 SSH 凭据的设置页。不做定时、不做恢复、不做加密——纯手动触发的本地备份管理器，先用起来再迭代。

## User Stories

1. 作为 qqt 运维者，我希望打开应用就看到 4 条固定备份任务和各自的上次备份时间/结果，以便一眼知道哪些资产备份过、哪些没有。
2. 作为 qqt 运维者，我希望在单条任务上点「立即备份」只跑这一条，以便想备什么就备什么。
3. 作为 qqt 运维者，我希望一键「全部备份」同时跑全部 4 条任务，以便一次操作完成整套本地备份。
4. 作为 qqt 运维者，我希望任务运行时看到运行状态与进度（尤其 OSS 首次全量约 32 GB），以便知道它没卡死、还要等多久。
5. 作为 qqt 运维者，我希望同一任务运行中时按钮不可重复触发，以便不会重复跑产生混乱产物。
6. 作为 qqt 运维者，我希望不同任务可以并行运行，以便 OSS 的长任务不挡住数据库 dump 这种快任务。
7. 作为 qqt 运维者，我希望任务失败时看到具体原因（SSH 连不上、源文件缺失、校验失败等），以便知道该修什么。
8. 作为 qqt 运维者，我希望数据库任务自动 SSH 上生产服务器跑 mysqldump 并把 `.sql.gz` 拉回本机，以便不用手工登录服务器导出再 scp。
9. 作为 qqt 运维者，我希望拉回的 dump 自动做非空 + gzip 完整性校验，以便不会拿到一份坏的备份还以为备好了。
10. 作为 qqt 运维者，我希望校验失败的 dump 残file被自动删除、历史记为失败，以便坏文件不会冒充有效备份占位。
11. 作为 qqt 运维者，我希望数据库 dump 自动保留最近 14 份，以便磁盘不无限增长、又有两周的回退点。
12. 作为 qqt 运维者，我希望 OSS 任务增量下载 `qqt7` 桶（只拉新增和改动），以便日常运行很快。
13. 作为 qqt 运维者，我希望 OSS 首次全量能接着现有部分镜像继续（断点续传），以便不用从头再下 32 GB。
14. 作为 qqt 运维者，我希望本地镜像从不主动删除文件，以便桶里误删的文件本地还留着。
15. 作为 qqt 运维者，我希望下载的媒体字节原样保存（不解析、不转码），以便加密/混淆过的内容不被破坏。
16. 作为 qqt 运维者，我希望本地文件名与真实 key 的映射以 manifest 的 Key 列为准，以便将来恢复时媒体不断链。
17. 作为 qqt 运维者，我希望看到 OSS 镜像的对象数/总大小/上次同步时间，以便掌握镜像的覆盖程度。
18. 作为 qqt 运维者，我希望 keystore 任务把本机 Android 工程里的 `release.keystore` 和 `keystore.properties` 复制成日期归档，以便这份不可再生的签名资产多一个本地副本。
19. 作为 qqt 运维者，我希望 keystore 源文件缺失时任务明确报错，以便我知道去找文件，而不是误以为已备份。
20. 作为 qqt 运维者，我希望 .env 任务经 SSH 读取服务器 `/opt/qqt-next/.env` 存成日期归档，以便 `OSS_NAME_SECRET` 等不可再生密钥有兜底。
21. 作为 qqt 运维者，我希望 keystore 和 .env 都是明文拷贝，以便拿出来就能用，不用解密。
22. 作为 qqt 运维者，我希望小件日期归档同样保留 14 份，以便清理规则统一好记。
23. 作为 qqt 运维者，我希望浏览每条任务的历史备份产物（时间/大小/结果），以便挑选恢复点。
24. 作为 qqt 运维者，我希望超出保留份数的旧产物被自动清理，以便不用手工删。
25. 作为 qqt 运维者，我希望清理只认本应用命名规则的产物，以便我现有的旧文件（9/13 的宝塔风格 `.sql`、`android\` 里的现有副本、`backend-next\` 配置快照）一律不被碰。
26. 作为 qqt 运维者，我希望运行历史在应用重启后仍在，以便关了再开还能看到过往记录。
27. 作为 qqt 运维者，我希望设置页只需填 SSH 登录信息（密码或密钥文件），以便数据库和 .env 任务能跑通。
28. 作为 qqt 运维者，我希望 SSH 凭据经 Windows DPAPI 加密存储，以便明文密码不落盘。
29. 作为 qqt 运维者，我希望 OSS 凭据沿用脚本从 backend-next 服务端 `.env` 读取的既有通路，以便不用在两处维护同一个 AK。
30. 作为 qqt 运维者，我希望应用内有恢复教程页，直接渲染现成的 backup-guide 和 full-deployment-guide 两份手册，以便出事时不离开应用就能找到恢复命令。
31. 作为 qqt 运维者，我希望教程实时读取手册文件，以便我更新手册后教程自动跟着新。
32. 作为 qqt 运维者，我希望应用是普通窗口程序、用时开用完关，以便没有常驻进程和后台定时。
33. 作为 qqt 运维者，我希望交付免安装便携 exe，以便双击即用、不装东西。
34. 作为 qqt 运维者，我希望应用只服务 qqt 的固定 4 项、没有通用任务配置，以便界面简单直接。
35. 作为 qqt 运维者，我希望备份产物全部落在现有 `qqt\backup\` 目录，以便与既有镜像、手册在同一处管理。

## Implementation Decisions

### 总体架构

- 三层：Vue3 渲染进程（界面）→ Electron 主进程（接线：IPC、窗口、凭据加解密、打包）→ **备份引擎**（纯 Node/TS 模块，零 Electron 依赖）。备份引擎是唯一新接缝，四条任务全部经它进出；主进程与渲染进程不含备份业务逻辑。
- 技术栈：Electron + Vue3 + TypeScript（复用 qqt admin-web / backend-next 的既有经验；Node 生态有 ssh2 等现成库）。
- 项目为独立新仓库（目标目录 `D:\Project\backup-manager`），初始化时创建 GLOSSARY.md（术语见 Further Notes）。qqt 仓库零改动。

### 备份引擎（唯一新接缝）

- 公开入口：`runTask(taskId)` 与 `runAll()`；taskId 为四值枚举（数据库 dump / OSS 镜像 / keystore / .env）。`runAll()` 并行触发四条任务，各自受防重入约束。
- 外部世界以四个注入口接入：SSH 端口（远程执行命令 + 取回文件）、脚本执行器端口（运行 sync-oss.ps1）、文件系统根目录（备份根目录）、时钟（日期命名）。生产实现由主进程装配；测试用桩与临时目录。
- 引擎输出统一的运行结果（成功/失败 + 产物路径 + 错误信息），并负责写运行历史、执行保留份数清理。
- 同一任务运行中拒绝重复触发（返回明确的「运行中」状态）；不同任务互不阻塞。

### 四条任务的机制

- **数据库 dump**：SSH（root@101.132.159.145:22）执行手册 §2.2 的 mysqldump 命令（`--single-transaction --quick --routines --triggers --default-character-set=utf8mb4`，gzip 压缩），经 SSH 取回 `.sql.gz` 落盘。生产 3306 不公网开放、库账号限容器网段，SSH 远程 dump 是唯一通路。mysqldump 以 root 运行、依赖服务器 `/root/.my.cnf`（手册 §2.2，运维侧由用户自理）。拉回后做非空 + gzip 完整性校验；校验失败删除残file、运行记失败。
- **OSS 镜像**：调用现成 `sync-oss.ps1`（PowerShell），不重写下载逻辑。该脚本已实现：全桶枚举 → `_manifest.csv`（Key/Bytes/ETag/LastModified/LocalPath）→ 经 CDN 并行下载（带 Referer）→ 大小 + MD5(ETag) 校验、断点续传、从不删本地多余文件。客户端呈现进度与结果（基于脚本输出与 manifest），并把镜像统计（对象数/总大小/上次同步时间）纳入任务行。凭据沿用脚本从 backend-next 服务端 `.env` 读取的既有通路，应用不另存 OSS AK。
- **keystore 归档**：复制本机 `android/app/release.keystore` 与 `keystore.properties` 到备份根目录下按日期命名的子目录；源文件缺失则任务失败并明确报错。明文，不加密。
- **.env 归档**：SSH 读取 `/opt/qqt-next/.env` 内容，存为按日期命名的明文文件。

### 命名、保留与清理

- 备份根目录固定为 `D:\Project\qqt\backup\`（现有 gitignored 目录，OSS manifest 的本地路径已指向这里），不可配置。
- 日期归档命名：dump 为 `qqt_next_<日期时间>.sql.gz`（沿用手册 §2.2 命名）；keystore 为 `android\<日期>\` 子目录；.env 为 `server-env\<日期>.env`。
- 保留份数：三类日期归档各保留最近 14 份，超出自动清理。**清理只匹配本应用上述命名规则**；现有旧文件（宝塔风格 `backup_*.sql`、`android\` 下的散文件、`backend-next\` 快照等）一律不动。
- OSS 是**单一镜像**：无份数概念、不参与清理、从不主动删除本地文件（桶里误删，本地还在）。

### 运行历史

- 历史记录存 `%APPDATA%` 下应用自有目录的 JSON 文件（任务、时间、结果、产物路径、错误信息），应用重启后仍在。历史与备份产物分离：产物在备份根目录，历史在应用数据目录。

### 凭据与设置

- 设置页只有 SSH 凭据：密码或密钥文件二选一；连接目标（主机/端口/用户）固定为 qqt 生产服务器，不做成可配置项。
- SSH 凭据经 Windows DPAPI（Electron safeStorage）加密落盘。
- 应用不存储 OSS AK/SK、不存储 MySQL 密码（前者脚本读 `.env`，后者靠服务器 `/root/.my.cnf`）。

### 界面

- 四个页面：任务列表（每行：任务名、上次时间、结果、运行状态/进度、「立即备份」；顶部「全部备份」）、历史产物（按任务浏览，时间/大小/结果）、恢复教程（markdown 实时渲染备份根目录下的 backup-guide.md 与 full-deployment-guide.md，不另写内容）、设置（SSH 凭据）。
- 普通窗口程序：无托盘、无定时、关闭即退出。

### 硬边界

- 媒体字节原样搬运：OSS 对象内容已加密/混淆（文件名 AES 密文依赖 `OSS_NAME_SECRET`、音频图片字节 +31、歌词内容加密），客户端不解析、不转码、不「修复」。
- 本地文件名经 NTFS 非法字符百分号编码，**文件名 ≠ 真实 key**，一切以 manifest 的 Key 列为准。
- dump 含用户数据与密码哈希，只存本地备份根目录，绝不上传任何云存储（尤其公共读的 `qqt7`）。
- 不做恢复功能：恢复照手册命令手工执行，应用只提供教程。

### 交付

- electron-builder 便携（portable）目标，免安装单 exe。

## Testing Decisions

- 好测试只断言外部行为：产物命名与落盘位置、保留份数清理边界、校验失败处理、历史记录内容、错误报告——不断言内部实现细节（调用顺序、私有函数）。
- **唯一测试接缝 = 备份引擎公开入口**（`runTask` / `runAll`）。测试在纯 Node 中跑（Vitest），不启动 Electron、不连真实服务器、不真实下载 OSS：文件系统用真实临时目录；SSH 端口与脚本执行器端口用桩（可模拟成功/失败/断连/坏 dump）；时钟注入保证日期命名可断言。
- 覆盖行为清单：四任务各自的产物命名与落盘；14 份清理（含「只认本应用命名、旧文件不动」的边界）；dump 非空 + gzip 校验与失败残file删除；镜像从不删除；运行历史持久化；防重入；keystore 源缺失报错；SSH 失败报错；`runAll` 并行与单任务失败不拖累其他任务。
- 先例：对齐 qqt 仓库 local-music-delete spec 的「唯一新接缝 `LocalMusicDeleter` + 行为级断言」模式，以及 Android JVM 单测风格（独立字面量、内容级断言，见近期 local-music 系列提交）。backend-next 无测试套件，不作先例。
- Vue 界面与 Electron 壳层不写自动化测试：个人工具，手工点一遍验证（含首次 OSS 续传、SSH 失败提示、教程渲染）。

## Out of Scope

- 定时任务、托盘常驻、错过补跑（用户明确只要手动触发）。
- 恢复功能（写回生产库、传回 OSS）——只做恢复教程页。
- 加密归档（keystore/.env/dump 均明文，用户明确「先明文」）。
- 通用任务模型/多项目支持（只服务 qqt 固定 4 项；`project_backup\tianyun` 等不涉及）。
- Nginx conf 自动拉取、防火墙/DNS/CDN 快照自动化（用户手动维护手册）。
- P2 旧服务器（47.111.25.157）legacy 媒体与旧库备份。
- APK 独立任务（`apks/` 随 OSS 镜像、`app_redirect_url` 随 DB dump 天然覆盖）。
- 云侧防护（桶版本控制、跨区域复制、私有备份桶）——控制台运维由用户自理。
- 安装程序、自动更新、失败通知（邮件/推送等）。
- 备份有效性季度验证（手册 §9）的自动化。

## Further Notes

### 术语（项目初始化时写入 GLOSSARY.md）

- **备份任务**：针对一项 qqt 资产的固定备份项，共 4 条。_Avoid_: 通用任务、备份计划
- **镜像**：OSS 桶的本地副本，增量保鲜、从不主动删除、只有一份。_Avoid_: 快照、同步副本
- **日期归档**：按运行日期命名的备份产物，受保留份数清理。_Avoid_: 版本、备份副本
- **恢复教程**：应用内只读手册页，不是恢复功能。_Avoid_: 恢复功能、一键恢复

### 环境事实（设计依据）

- 生产 MySQL 5.7（宝塔宿主机，非容器）；`qqt_next` 账号 host 限 `172.%`；3306 不公网放行。
- OSS 桶 `qqt7`（oss-cn-shanghai，公共读），约 32 GB / 1635 对象：`uploads/` 1390、`lrc/` 123、`images/` 116（含 thumbs 61）、`apks/` 6。
- `backup\oss\` 已有部分镜像与 `_failed.txt`；`backup\db\` 仅一份 2026-09-13 手工 dump；`backup\android\` 已有明文 keystore + APK 副本。
- 两份手册在 `qqt\backup\`（gitignored）与 `D:\Project\project_backup\qqt\` 各有一份拷贝；教程页渲染备份根目录下的那份。

### 运维依赖（用户自理，应用不做）

- 服务器 `/root/.my.cnf` 按手册 §2.2 配置（mysqldump 免密）。
- 本机到生产服务器的 SSH 可达（密码或密钥）。
- backend-next 服务端 `.env` 存在且含有效 OSS AK（脚本枚举桶需要）。

### 工作区说明

- 本 spec 发布在 qqt 仓库的本地 tracker（`.scratch/backup-manager/`），但实现目标是独立新目录 `D:\Project\backup-manager`（在 qqt 工作区之外，本会话创建被策略拦截）。实现阶段需以该目录为工作区（或将其加入信任路径），届时一并创建 GLOSSARY.md。

## Implementation Record（2026-11-14 implement-spec 收尾）

- 集成分支：独立仓库 `D:\Project\backup-manager` 的 `main`，tip `f8d1234`（含评审修复）。7 张票 01–07 全部 resolved，`--no-ff` 保留票据边界。用户已将目标目录加入信任路径，本会话完成实现。
- 交付：4 条固定任务全部真实实现（db-dump / oss-mirror / keystore / server-env），4 页（任务列表/历史产物/恢复教程/设置），14 份保留清理（只认本应用命名、OSS 与旧文件不碰），%APPDATA% 运行历史，DPAPI SSH 凭据，portable 单 exe（`release\backup-manager-0.1.0-portable.exe`，~106.9 MiB）。
- 测试：Vitest 纯 Node 引擎接缝 **60/60 绿**；typecheck + build + portable 打包均通过。Vue/Electron 壳层手工冒烟（打包 exe CDP 14/14 PASS）。
- 代码评审：两轴（Standards/Spec）**零硬违规**；唯一实质修复 = db-dump 远程命令加 `set -o pipefail`（部分 dump 不再误判成功）+ 删死代码 `notImplementedTask` + DRY 抽取（format / queue / scanArchiveDir）。
- qqt 仓库零改动（仅只读消费 `sync-oss.ps1` / `backup/` / android keystore 源 / 两份手册；本 tracker 更新为 spec 发布地约定）。
- 待人工确认（各票勾选项保留未勾）：真实 SSH 凭据下服务器 dump/.env 取回、真实 OSS 完整增量/首次全量走查、portable exe 双击黄金路径。运维依赖（`/root/.my.cnf`、SSH 可达、backend-next `.env` 含 OSS AK）用户自理。