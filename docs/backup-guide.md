# 备份手册（灾备基线）

> **用途**：定义线上必须备份的资产、备份方法与验证手段。任何一项丢失后，按 [full-deployment-guide.md](full-deployment-guide.md) 重建。
> **现状**：仓库内尚无任何自动备份配置，本手册是第一版基线。**先落地 §4「最小动作」的 4 件事（约 30 分钟）**，其余按优先级补齐。

## 1. 资产总表（先看这张）

线上数据只落在四个地方：**生产服务器**（`101.132.159.145`，宝塔 MySQL + Docker 容器）、**阿里云 OSS**（bucket `qqt7`）、**阿里云 ACR**（镜像仓库）、**GitHub**（代码）。另有两项不落在服务器上：Android 签名 keystore（开发机）、APK 安装包（`app_redirect_url` 指向的地址）。

| 级别 | 资产 | 在哪 | 丢失后果 | 备份手段 |
|---|---|---|---|---|
| **P0** | MySQL 库 `qqt_next` | 生产服务器 宝塔 MySQL | 内容/用户/收藏/评分/统计全丢，**别处找不回** | 每日 dump + 异地副本（§2） |
| **P0** | 媒体 bucket `qqt7` | 阿里云 OSS（上海） | 音频/封面/歌词全丢（DB 里 key 还在，媒体全 404） | 版本控制 / 复制备份桶（§5） |
| **P0** | Android 签名 keystore | 开发机 `android/app/release.keystore` + `android/keystore.properties`（均 gitignored） | **无法再签发同签名升级包**，老用户升级只能卸载重装 | 加密打包、多介质存放（§4） |
| **P0** | 服务器 `.env` | `/opt/qqt-next/.env` | 密钥丢失的逐项影响见 §3 | 导出留档（与文档分开存） |
| P1 | APK 安装包 | 后台「版本更新」配置的 `app_redirect_url` 指向处 | 下载页 302 变死链，用户无法下载 | 归档 APK 本体 + 记录 URL |
| P1 | 服务器配置 | 宝塔（Nginx conf / 防火墙 / root 密码 / 计划任务） | 可重建但耗时 | 导出 conf + 快照表（§6） |
| P1 | DNS / CDN 配置 | 域名商控制台、阿里云 CDN | 域名不可达 → App 走 IP 回退（ADR 0009） | 快照表（§6） |
| P2 | 旧服务器 legacy 媒体 | `47.111.25.157`（若仍在线） | 未「转入 OSS」的歌曲播放失效 | §7 |
| — | 代码 | GitHub `jiange2/qqt-next` + 开发机 | 已有两份，风险低 | 已有 |
| — | 镜像 | ACR `qqt7/qqt_repo:vN/latest` | 可从代码重建 | **不要删旧 vN**（回滚依赖） |

## 2. 数据库备份（P0-1）

### 2.1 方式 A：宝塔计划任务（推荐，零脚本）

宝塔面板「计划任务」→ 添加任务 → 类型「备份数据库」→ 选择 `qqt_next` → 每天 `03:30` → 保留 14 份。备份产物在 `/www/backup/database/`。

### 2.2 方式 B：Shell 脚本（可控、易异地）

宝塔计划任务 → 类型「Shell 脚本」→ 每天 03:30 执行，脚本内容：

```sh
#!/bin/sh
# /opt/qqt-next/backup-db.sh —— 数据库每日备份，保留最近 14 份
set -e
DST=/www/backup/qqt_next
mkdir -p "$DST"
mysqldump --single-transaction --quick --routines --triggers \
  --default-character-set=utf8mb4 qqt_next | gzip > "$DST/qqt_next_$(date +%F_%H%M).sql.gz"
ls -1t "$DST"/qqt_next_*.sql.gz | tail -n +15 | xargs -r rm -f
```

首次执行前先放好 root 凭据（`mysqldump` 以 root 运行时自动读 `~/.my.cnf`，避免密码出现在 cron 里）：

```sh
cat > /root/.my.cnf <<'EOF'
[client]
user=root
password=<宝塔「数据库」页可见的 root 密码>
EOF
chmod 600 /root/.my.cnf
```

> 注意：库账号 `qqt_next` 的 host 限定为 `172.%`（只允许容器网段），**本机命令行连不上属预期**，本机备份一律用 root。

### 2.3 异地副本（关键：别只留在同一台服务器）

```sh
# 服务器上执行（ossutil 安装：下载对应平台二进制放 /usr/local/bin，或宝塔终端安装）
ossutil cp -r /www/backup/qqt_next oss://<私有备份桶>/mysql/ --update
```

- **必须新建私有读写 bucket**（如 `qqt-backup`）放 dump：dump 含用户数据与密码哈希，**绝不能放进公共读的 `qqt7`**（该桶匿名可读，只为媒体设计）。
- 建议再每月手动 `scp` 一份到本机/NAS（`scp root@101.132.159.145:/www/backup/qqt_next/*.sql.gz .`），做到"服务器 + 云 + 本地"三点。

### 2.4 恢复命令（与部署手册 §7 一致）

```sh
gunzip < qqt_next_2026-09-13_0330.sql.gz | mysql -uroot qqt_next
```

dump 自带 `DROP TABLE`/`CREATE TABLE` 与 `_prisma_migrations` 表，可直接覆盖恢复；应用再启动时 `prisma migrate deploy` 会自动补齐 dump 之后的增量迁移。

## 3. 服务器 .env（P0-2）

导出留档：`cat /opt/qqt-next/.env`，内容存进密码管理器（**不要提交进 git**）。丢失后逐项影响：

| 变量 | 丢失/改错的后果 | 找回方式 |
|---|---|---|
| `DB_PASSWORD` | 容器连不上库 → 服务全挂 | 宝塔重置 `qqt_next` 密码后同步改 .env |
| `JWT_SECRET` | 管理员会话全部失效，重新登录即可 | 重新生成 `openssl rand -hex 32`，无其他后果 |
| `OSS_ACCESS_KEY_ID/SECRET` | 后台上传/「转入 OSS」功能失效 | 阿里云 RAM 重新签发（权限限定本 bucket） |
| `OSS_NAME_SECRET` | **业务不受损**：新上传照常；只是存量密文 key 的「原名」在面板解不出（回退显示 key 尾段） | 从备份恢复原值；真丢了只能生成新随机串继续用，存量原名永久不可解（ADR 0008 已接受） |
| `LRC_CONTENT_SECRET` | **高风险**：换值后**新上传**的歌词在 App 端全部「加载失败」（存量不受影响，因为解密的 App 端用的是旧值） | 从备份恢复；无备份用 `android/app/src/main/java/com/qqt/music/AppConfig.kt` 里 `LRC_CONTENT_SECRET` 的值（`518b465562a5ec61b6d750fa68533bc1`，必须与线上 App 完全一致；仓库 `.env.example` 当前同值） |
| `OSS_PUBLIC_BASE` | 客户端媒体 URL 全错（全部播不了） | 备份恢复；无备份填 CDN 域名 `http://cdn.qqt.yunshangzhiai7.top/` |

## 4. 最小动作（建议今天就做，约 30 分钟）

1. **数据库每日备份**：按 §2.1 或 §2.2 配置，并手动执行一次确认产物生成。
2. **拉一份到本机**：`scp` 下载刚生成的 dump（验证"服务器→本地"通路真的通）。
3. **留档 .env 与 keystore**：`.env` 内容存密码管理器；`release.keystore` + `keystore.properties` 加密打包（`7z a -p -mhe=on qqt-keystore.7z ...`）存 ≥2 个介质（网盘 + 移动硬盘）。**keystore 丢失不可再生，这是整份手册里最不可逆的一项。**
4. **OSS 防护**：控制台给 `qqt7` 开「版本控制」（防误删/覆盖），并按 §5 决定复制方案。

## 5. OSS 媒体备份（P0-3）

方案 A（推荐，控制台零运维）：bucket `qqt7` 开**版本控制** + 配置**同区域复制**到私有备份桶（服务端复制，不占服务器带宽；跨区域复制成本更高，仅在需要防"上海地域整体故障"时启用——按控制台实际支持的复制类型配置）。

方案 B（自管）：`ossutil sync oss://qqt7 /mnt/backup/qqt7` 定期增量同步到本机/NAS（首次全量较慢）。

两条硬约束（恢复时违反任何一条 = 媒体断链）：

- **key 必须保持原样**：DB 只存 key，URL 是运行时拼接的；key 变了等于全部 404。
- **密文 key 依赖 `OSS_NAME_SECRET`**：恢复上传时用备份里的原值，否则存量文件原名不可解（播放不受影响）。

## 6. 配置快照表（P1，填写后随文档存档）

| 项 | 现网值 |
|---|---|
| A 记录 `qqt.yunshangzhiai7.top` | `101.132.159.145`（App 主入口，需 8001 端口） |
| CDN `cdn.qqt.yunshangzhiai7.top` | 阿里云 CDN → 源站 `qqt7.oss-cn-shanghai.aliyuncs.com`；明文 HTTP 不加证书（ADR 0005） |
| CDN Referer 防盗链 | `uploads/`：禁空 Referer + 白名单 `com.qqt.music`；`images/`、`images/thumbs/`、`lrc/`：允许空 Referer（ADR 0006） |
| 80 端口 default_server | `/download/` 下载页；其余路径 301 → `https://a.yunshangzhiai7.top` |
| 安全组/防火墙放行 | `80`、`8001`、`8002`、宝塔面板端口；**3306 不放行** |
| 宝塔面板 | 地址 `https://<IP>:<端口>`【待填】、账号【密码管理器】、root 密码【密码管理器】 |
| 服务器目录 | `/opt/qqt-next/{docker-compose.next.yml, deploy.sh, .env}` |
| ACR | `crpi-oyq5ia6dv9vty8p3.cn-shanghai.personal.cr.aliyuncs.com/qqt7/qqt_repo`，登录用户 `18826137225` |
| 当前镜像版本 | 仓库 `scripts/IMAGE_VERSION` = `42`（以 ACR 上实际最新 tag 为准） |

宝塔/Nginx 配置快照：把 `8001`/`8002`/`80` 三个 server 块的 conf 原文存一份到本机（完整内容见 [full-deployment-guide.md](full-deployment-guide.md) §9）。

## 7. 旧栈 legacy（P2，若仍在线）

旧服务器 `47.111.25.157`（PHP 栈 + 媒体目录 `images/ uploads/ lrc/`）：部分歌曲 `type=external` 仍指向它拉流，**在全部「转入 OSS」完成前不要下线该服务器**。

```sh
# 整目录归档（在开发机执行）
rsync -av --delete root@47.111.25.157:/www/wwwroot/backend/ ./legacy-backend-archive/
```

旧库 `qqt` 若仍在提供服务，按 §2 同样方式备份（库名换 `qqt`）。

## 8. 恢复入口速查

| 丢了什么 | 去哪恢复 |
|---|---|
| 数据库数据 | 本手册 §2.4 + [full-deployment-guide.md](full-deployment-guide.md) §7 |
| 服务器 .env | 本手册 §3 找回值 → [full-deployment-guide.md](full-deployment-guide.md) §6 重写 |
| 单个坏版本（发版事故） | [full-deployment-guide.md](full-deployment-guide.md) §1 决策树第一行：`./deploy.sh v<旧版本>` |
| 整台服务器 | [full-deployment-guide.md](full-deployment-guide.md) §4~§11 |
| OSS 媒体 | 本手册 §5 + 部署手册 §10 |
| keystore / APK | 本手册 §4（不在服务器上，服务器重建不涉及） |

## 9. 备份有效性验证（每季度一次，约 15 分钟）

1. **dump 可恢复**：把最新 dump 导入一个临时库（`mysql -uroot -e "CREATE DATABASE qqt_restore_test"` 后导入），比对行数：`SELECT COUNT(*) FROM songs;`、`SELECT COUNT(*) FROM access_facts;` 与生产一致。
2. **媒体可达**：随机取 3 个媒体 key，用 `curl -sI -H "Referer: http://com.qqt.music/" "<OSS_PUBLIC_BASE><key>"` 验证 200。
3. **keystore 可签名**：干净 clone 后用备份的 keystore 跑一次 `gradlew assembleRelease`，确认产物已签名（`apksigner verify`）。
4. **.env 完整**：按 §3 表格逐项核对备份内容与线上一致。
