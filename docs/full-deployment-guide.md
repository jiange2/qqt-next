# backend-next 完整部署与灾难重建手册

> **用途一**：线上故障按最短路径恢复——先读 §1 决策树。
> **用途二**：服务器整机重建 / 迁移——从空机器到线上可用，按 §3~§11 顺序执行。
>
> **配套**：[backup-guide.md](backup-guide.md)（备份什么、恢复材料从哪来）；[backend-next-deploy.md](backend-next-deploy.md)（首版部署手册与日常运维，本文档是其超集，冲突时以本文档为准）。

## 1. 故障决策树（最快恢复路径）

| 故障 | 操作 | 预计耗时 |
|---|---|---|
| 发版后接口/面板异常 | **回滚**：`cd /opt/qqt-next && ./deploy.sh v<上一个已知良好版本>`，同时 `docker compose -f docker-compose.next.yml logs -f app` 看现场 | 1 分钟内 |
| 容器挂了 / 无响应 | `docker compose -f docker-compose.next.yml up -d`（`restart: unless-stopped` 通常已自愈，先看 `docker ps`） | 1 分钟 |
| 宿主机重启后服务没起 | 查 `systemctl status docker`（`systemctl enable docker` 保证自启）与 MySQL/Nginx 是否随宝塔自启 | ~5 分钟 |
| 数据库数据损毁 | 恢复最近 dump：§7；服务表结构坏了也让容器重启自动迁移修复 | ~10 分钟 |
| 服务器整机不可用 | **新机重建**：§3 → §11（有备份则全部可恢复；DNS 切新 IP） | 1~2 小时 |
| OSS 数据损毁 | 从备份桶回传：§10.3 | 按数据量 |
| 域名/DNS 故障 | App 自动走硬编码回退 IP（ADR 0009），修复解析即可；最坏情况改 DNS 指向备用入口 | — |

> **总开关是 DNS**：App 主入口是域名 `qqt.yunshangzhiai7.top:8001`（ADR 0009），只要域名能解析到任一可用服务器，App 即可恢复。
> **提前动作（建议现在做）**：若 `101.132.159.145` 不是弹性公网 IP（EIP），重建后 IP 会变——存量 App 的硬编码回退地址（`AppConfig.FALLBACK_BASE_URL`）将失效（主路径不受影响）。确认/换绑 EIP 后，灾后直接换绑回原 IP，回退路径也能继续用。

## 2. 架构与关键参数

```
用户/App ──────► Nginx（宿主机，宝塔）
  │                 ├─ :8001  API   ─┐
  │                 ├─ :8002  Admin ─┤─► app 容器（仅监听 127.0.0.1:8000）
  │                 └─ :80    /download/ 下载页（其余路径 301 → a.yunshangzhiai7.top）
  │                                        ├─► 宝塔 MySQL 5.7（宿主，host.docker.internal:3306，库 qqt_next）
  │                                        └─► 阿里云 OSS bucket qqt7（媒体，上传走内网 OSS_INTERNAL=true）
  └─ 媒体直连 ◄── CDN cdn.qqt.yunshangzhiai7.top ◄── OSS（App 不经过服务器）

开发机（Windows）── scripts/release.ps1（构建+推送）──► 阿里云 ACR ──► 服务器 ./deploy.sh（pull + up -d）
```

| 项 | 值 |
|---|---|
| 生产服务器 | `101.132.159.145`（阿里云 ECS 上海，宝塔面板） |
| API 入口 | `http://qqt.yunshangzhiai7.top:8001/`（App 主入口，明文 HTTP，ADR 0004/0005） |
| Admin 入口 | `http://<服务器IP>:8002/admin/` |
| 下载页 | `http://<服务器IP>/download/`（80 default_server，裸 IP 与任意域名均可达） |
| 容器端口 | compose 绑定 `127.0.0.1:8000:8000`，仅本机 Nginx 可达 |
| MySQL | 宝塔宿主机 MySQL **5.7**（代码按 5.7 兼容编写：禁窗口函数/CTE，见 ADR 0004 与 backend-next 规范）；库 `qqt_next`，账号 `qqt_next@172.%` |
| OSS | `oss-cn-shanghai` / bucket `qqt7`（公共读），媒体 key 存 DB，URL 由 `OSS_PUBLIC_BASE` 拼接 |
| 镜像 | `crpi-oyq5ia6dv9vty8p3.cn-shanghai.personal.cr.aliyuncs.com/qqt7/qqt_repo:vN`（双 tag `vN`+`latest`） |
| 代码 | `github.com/jiange2/qqt-next` |
| 服务器目录 | `/opt/qqt-next/{docker-compose.next.yml, deploy.sh, .env}` |
| 当前版本 | `scripts/IMAGE_VERSION` = `42`（以 ACR 上实际存在的 tag 为准） |
| 旧栈 | `47.111.25.157`（PHP，遗留媒体源，见附 A） |

## 3. 前置准备（动手前确认手上都有）

- [ ] ACR 登录凭据：用户名 `18826137225` + 仓库密码
- [ ] 宝塔面板地址 / 账号 / root 密码
- [ ] 阿里云账号（OSS、CDN、DNS、ECS 控制台）
- [ ] **恢复材料**（见 [backup-guide.md](backup-guide.md)）：数据库 dump、`.env` 备份
- [ ] GitHub `jiange2/qqt-next` 仓库访问
- [ ] 开发机 Windows + Docker Desktop（仅当需要重新出镜像时）

## 4. 新服务器初始化

1. **购买 ECS**：尽量选上海地域（与 OSS 同 region，上传走内网免流量费、CDN 回源更近）。如原服务器是 EIP，换绑到新机。
2. **安全组 + 防火墙放行**：`80`、`8001`、`8002`、宝塔面板端口；**3306 绝不放公网**（容器经 `host-gateway` 访问属本机流量，不需要放行）。
3. **安装宝塔面板**：官方安装脚本从 [bt.cn](https://www.bt.cn) 获取（不要照抄旧脚本，版本会失效），装完登录面板。
4. **装依赖**（宝塔「软件商店」）：
   - Nginx（任意较新版本）
   - **MySQL 5.7**（与现网同版本；代码对 5.7/8.0 都兼容，但不要无谓换版本）
   - Docker（宝塔「Docker 管理器」或官方脚本安装；确认插件可用）
5. **验证**：

```sh
docker compose version    # Compose v2 插件
docker run --rm hello-world
mysql -uroot -p -e "SELECT VERSION();"
```

## 5. 宝塔 MySQL 建库建号

1. 宝塔「数据库」→ 添加数据库：库名 `qqt_next`（utf8mb4）。
2. 添加账号 `qqt_next`，**访问权限选「指定 IP/网段」，填 `172.%`**——不要写 `172.17.%`：compose 自建网络是动态网段（实测出现过 `172.19.0.x`）。仅授权 `qqt_next` 库。
3. 检查 `my.cnf` 的 `bind-address` 不能是 `127.0.0.1`（改为 `0.0.0.0` 后重启 MySQL），否则容器连不上。
4. 记为待用：数据库 root 密码（`.env` 的 `DB_PASSWORD` 用 `qqt_next` 账号密码，不是 root）。

## 6. 部署目录与 .env

```sh
mkdir -p /opt/qqt-next && cd /opt/qqt-next
# 放入 docker-compose.next.yml（仓库根）与 deploy.sh（仓库 scripts/）
cp .env.example .env    # 来自仓库根
chmod +x deploy.sh
```

`.env` 按备份恢复；**没有备份时**按下表逐项找回：

```sh
# /opt/qqt-next/.env —— 每项含义与丢失影响见 backup-guide.md §3
DB_PASSWORD=<恢复：备份 / 第 5 步新设的 qqt_next 账号密码>
JWT_SECRET=<恢复：备份 / 无备份则 openssl rand -hex 32 重生成（仅致管理员重登）>
OSS_REGION=oss-cn-shanghai
OSS_BUCKET=qqt7
OSS_ACCESS_KEY_ID=<恢复：备份 / 无备份则 RAM 重新签发>
OSS_ACCESS_KEY_SECRET=<同上>
OSS_INTERNAL=true
OSS_PUBLIC_BASE=<恢复：备份 / 无备份填 http://cdn.qqt.yunshangzhiai7.top/>
OSS_NAME_SECRET=<尽量从备份恢复：存量密文 key 的原名依赖它；实在没有可重生成，业务不受损>
LRC_CONTENT_SECRET=<尽量从备份恢复：必须与线上 App 的 AppConfig.kt 完全一致，否则新上传歌词 App 解不开>
#ACR_HOST=crpi-oyq5ia6dv9vty8p3-vpc.cn-shanghai.personal.cr.aliyuncs.com
```

预检（必填项缺失会直接报错）：

```sh
docker compose -f docker-compose.next.yml config >/dev/null && echo ENV_OK
```

## 7. 恢复数据库

**顺序：先恢复 dump，再启动容器**（容器启动会自动跑 `prisma migrate deploy`，补齐 dump 之后的增量迁移）。

```sh
# 有备份（从备份拉回 /www/backup/qqt_next/ 下）：
gunzip < /www/backup/qqt_next/qqt_next_<最新>.sql.gz | mysql -uroot qqt_next

# 无备份（空库起步，内容全丢）：跳过本步，直接 §8；首次启动后跑 seed-admin 建管理员
```

恢复后抽查：

```sh
mysql -uroot qqt_next -e "SELECT COUNT(*) FROM songs; SELECT COUNT(*) FROM admin_users; SELECT COUNT(*) FROM settings;"
```

## 8. 部署镜像并启动

```sh
docker login --username=18826137225 crpi-oyq5ia6dv9vty8p3.cn-shanghai.personal.cr.aliyuncs.com   # 一次性

cd /opt/qqt-next
./deploy.sh v42        # 用最近已知良好版本（当前仓库 IMAGE_VERSION=42；以 ACR tag 为准）
```

deploy.sh 内部：`pull` → `up -d`（容器启动命令先跑 `prisma migrate deploy` 再起服务）→ 等 3 秒 curl `/admin/` 健康检查，非 200 退出 1。

**仅空库**（无 dump 恢复）需要建初始管理员（幂等，密码仅打印一次，立即存进密码管理器）：

```sh
docker compose -f docker-compose.next.yml run --rm app \
  sh -c "cd server && npx tsx scripts/seed-admin.ts"
```

管理员密码丢失的找回：删 `admin_users` 表记录后重跑上面命令。

## 9. Nginx 反代（完整配置）

把三个 server 块放入宝塔对应站点的 conf（或直接编辑 nginx conf），改完 `nginx -t && nginx -s reload`：

```nginx
# API：Android App 入口 http://qqt.yunshangzhiai7.top:8001/
server {
    listen 8001;
    location / {
        proxy_pass http://127.0.0.1:8000;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        client_max_body_size 520m;   # 音频上传 ≤500MB
    }
}
# Admin 面板（端口不做路径重写，原样透传；面板资源以 /admin/ 为 base）
server {
    listen 8002;
    location = / { return 302 /admin/; }
    location / {
        proxy_pass http://127.0.0.1:8000;
        proxy_set_header Host $host;
        client_max_body_size 25m;    # 图片 ≤20MB
    }
}
# 下载页（ADR 0012）：并入 80 的 default_server——裸 IP、任意域名均可达，无需动 DNS
# 注意：原来 server 级的 return 301 必须下移到 location /（server 级 return 先于 location 匹配执行），
# 否则 /download/ 永远走不到（经典坑，同 ACME challenge 失效原因）
server {
    listen 80 default_server;
    listen [::]:80 default_server;
    server_name _;

    location = / { return 302 /download/; }           # 短链入口：根路径进下载页
    location = /download { return 302 /download/; }   # 补斜杠，保证页内相对路径解析
    location /download/ {
        proxy_pass http://127.0.0.1:8000;             # 路径原样透传，勿重写
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }
    location / { return 301 https://a.yunshangzhiai7.top; }   # 其余路径维持原行为（a. 为另一站点，原样保留）
}
```

验证：

```sh
curl -sI http://127.0.0.1/download/            # 200
curl -sI http://127.0.0.1/download/apk         # 302 到后台配置的安装包地址（未配置则 503）
```

## 10. DNS / CDN / OSS

### 10.1 DNS

- A 记录 `qqt.yunshangzhiai7.top` → 新服务器公网 IP（**App 主入口，最关键的切换动作**）。
- 切换前可提前把 TTL 调低（如 60s），切换后恢复。
- `a.yunshangzhiai7.top` 是 80 端口 301 的目标（另一站点），按现网行为原样保留。

### 10.2 CDN 与防盗链（阿里云 CDN）

- 加速域名 `cdn.qqt.yunshangzhiai7.top`，源站 `qqt7.oss-cn-shanghai.aliyuncs.com`，**明文 HTTP 不加证书**（ADR 0005，App 已放行明文）。
- Referer 防盗链**分路径**配置（ADR 0006）：
  - `uploads/`：禁止空 Referer + 白名单 `com.qqt.music`（App 对媒体请求统一注入 `Referer: http://com.qqt.music/`）
  - `images/`、`images/thumbs/`、`lrc/`：允许空 Referer
- 配错这条 = App 媒体请求全体 403，是第一排查嫌疑。

### 10.3 OSS 媒体恢复（数据损毁时）

```sh
# 从备份桶回传（同 region 服务端复制最快；ossutil 亦可）
ossutil cp -r oss://<私有备份桶>/media oss://qqt7/ --update
```

两条硬约束：**key 保持原样**（DB 存 key 拼 URL，key 变=断链）；**上传侧 `OSS_NAME_SECRET` 用备份原值**。

### 10.4 安装包与下载页

下载页的版本号与安装包地址取自数据库 settings（`app_new_version` / `app_redirect_url`），随 dump 恢复；**安装包文件本体**需在 `app_redirect_url` 指向的位置可用（见 [backup-guide.md](backup-guide.md) §1 APK 一行），否则点下载 302 到死链。

## 11. 验收清单

- [ ] 容器与健康检查：`cd /opt/qqt-next && docker ps`（`qqt-next-app` Up），`curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1:8000/admin/` → 200
- [ ] 经 Nginx 的 API 链路（无签名公开端点，串起 8001→容器→DB）：

```sh
curl -s http://127.0.0.1:8001/download/app-meta        # 返回 {"version":...}，空则查 DB 恢复
curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1:8002/admin/   # 200
```

- [ ] 面板：浏览器开 `http://<服务器IP>:8002/admin/` 登录，抽查歌曲/设置数据在
- [ ] 域名：`curl -s http://qqt.yunshangzhiai7.top:8001/download/app-meta`
- [ ] 下载页：`curl -sI http://127.0.0.1/download/apk` → 302
- [ ] 媒体：浏览器/curl 带 `Referer: http://com.qqt.music/` 拉一个媒体 URL → 200
- [ ] **真机**：App 打开 → 首页列表、搜索、播放（媒体走 CDN）、歌词、封面轮播
- [ ] 后台写路径：上传一首测试歌（验证 OSS 上传）+ 改一处设置（验证写库）

> 冒烟脚本 `pnpm smoke` 会注册测试账号并写入收藏/下载计数（且需数据库直连），**不要对生产库跑**；生产验收以真机主流程为准。

## 12. 日常发版与回滚

```sh
# 本地（Windows，仓库根）：
.\scripts\release.ps1       # 版本自动 +1，构建 + 推送 vN 与 latest
# 服务器：
cd /opt/qqt-next && ./deploy.sh vN     # 回滚 = 指定旧 vN 重跑
```

- 版本号由仓库 `scripts/IMAGE_VERSION` 单调 +1 维护；**ACR 上不要删旧 vN tag**（回滚依赖）。
- 每次发版后：真机过一遍主流程（列表/播放/歌词），再放量。

## 13. 故障排查表

| 现象 | 排查 |
|---|---|
| 容器起不来 / DB 连接拒绝 | `.env` 的 `DB_PASSWORD` 与宝塔账号是否一致；账号 host 是否 `172.%`（compose 网段是 172.19.x 等动态值，不是 172.17.x）；`my.cnf` 的 `bind-address` |
| 容器反复重启（restart loop） | `docker compose -f docker-compose.next.yml logs --tail=100 app`：多为 `.env` 缺项/错值（`OSS_NAME_SECRET`、`LRC_CONTENT_SECRET` 等缺失会直接启动失败，fail fast 设计）或数据库连不上 |
| `.env` 填错/缺项 | `docker compose -f docker-compose.next.yml config` 直接报缺哪个变量 |
| 面板白屏 | 确认镜像内 `admin-web/dist` 产物资源路径带 `/admin/` 前缀（`--base=/admin/` 构建） |
| 媒体 403 | CDN Referer 规则（§10.2）；App 全体 403 即此规则配错 |
| 媒体 404 | OSS key 缺失 / `OSS_PUBLIC_BASE` 拼接错误 |
| 统计里设备 IP 是反代地址 | nginx 两个头都没透传：X-Real-IP 与 X-Forwarded-For 至少配一个（代码取头顺序 XFF 首段 → X-Real-IP → req.ip，见 §9） |
| 管理员密码丢失 | 删 `admin_users` 表记录后重跑 seed-admin.ts（§8） |
| 下载页 404/502、或访问又跳回主站 | 检查 80 default_server：server 级 `return 301` 必须下移到 `location /`；改完 `nginx -t && nginx -s reload` |
| 下载页能开、点下载 503 | 后台「版本更新」的跳转地址未配置（`/download/apk` 显式返回 503），去 Admin 面板填写安装包直链 |
| App 全部请求失败 | 检查 8001 是否被安全组/防火墙挡住；确认域名解析；App 有 IP 回退（连接层失败自动切换） |

## 附 A：旧栈 / legacy 媒体

- 旧服务器 `47.111.25.157`：旧 PHP 栈 + 媒体目录（`images/ uploads/ lrc/`）。**部分歌曲 `type=external` 仍从它拉流**，在全部「转入 OSS」完成前不要下线/格式化；其归档方法见 [backup-guide.md](backup-guide.md) §7。
- 旧库 `qqt`：仅迁移脚本（`fetch-prod.ts` / `migrate.ts`）依赖，不再服务线上请求；如需留档按 [backup-guide.md](backup-guide.md) §2 方式备份。
