# backend-next 生产部署手册（新服务器）

拓扑：`Nginx(:80 下载页 / :8001 API / :8002 Admin)` → `app 容器(127.0.0.1:8000)` → 宿主机宝塔 MySQL(3306)，媒体存阿里云 OSS（同 region 走内网）。明文 HTTP，无 HTTPS（ADR 0004）。

## 1. 前置（宝塔侧）

1. **建库建号**：宝塔「数据库」→ 建库 `qqt_next`；账号 `qqt_next`，**访问权限改为指定 IP/网段 `172.%`**（不要写 `172.17.%`：compose 自建网络是动态网段，实测出现过 `172.19.0.x`；docker 网桥是本机流量，不出公网，3306 无需对安全组开放）
2. **确认监听**：若容器报连不上 MySQL，检查 `my.cnf` 的 `bind-address` 不能是 `127.0.0.1`（改为 `0.0.0.0` 后重启 MySQL）

## 2. 服务器目录与配置

```sh
mkdir -p /opt/qqt-next && cd /opt/qqt-next
# 放入 docker-compose.next.yml、deploy.sh（均来自仓库，docker-compose.next.yml 在仓库根、deploy.sh 在 scripts/）
cp .env.example .env   # 来自仓库根；按注释填写
chmod +x deploy.sh
```

`.env` 必填：`DB_PASSWORD`、`JWT_SECRET`（`openssl rand -hex 32`）、`OSS_*`（`OSS_INTERNAL=true`）、`LRC_CONTENT_SECRET`（歌词内容加密口令，docs/adr/0010，须与 App 端 AppConfig 一致）。可选：`ACR_HOST` 设为 `crpi-oyq5ia6dv9vty8p3-vpc.cn-shanghai.personal.cr.aliyuncs.com`（同 region ECS 拉镜像走内网，免公网流量费）。

## 3. ACR 登录（一次性）

```sh
docker login --username=18826137225 crpi-oyq5ia6dv9vty8p3.cn-shanghai.personal.cr.aliyuncs.com
```

## 4. Nginx 反代（宝塔站点或直接 conf）

```nginx
# API：Android App 未来 BASE_URL 指向 http://<服务器IP>:8001/
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
# Admin 面板
server {
    listen 8002;
    location = / { return 302 /admin/; }
    location / {
        proxy_pass http://127.0.0.1:8000;
        proxy_set_header Host $host;
        client_max_body_size 25m;    # 图片 ≤20MB
    }
}
# 客户端下载页（ADR 0012）：并入 80 的 default_server——裸 IP、任意域名均可达，无需动 DNS
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
    location / { return 301 https://a.yunshangzhiai7.top; }   # 其余路径维持原行为
}
```

两个端口都在宝塔「安全」/安全组放行。注意 Admin 端口**不做路径重写**，原样透传（面板资源以 `/admin/` 为 base，由 `--base=/admin/` 构建保证）。

下载页刻意不走 HTTPS：全栈明文（ADR 0004/0005），且 HTTPS 页面 + 明文安装包会被 Chrome 按「不安全下载」拦截——页面与包必须同为明文（或同为 HTTPS）。验证：`curl -sI http://127.0.0.1/download/` 应为 200；`curl -sI http://127.0.0.1/download/apk` 应为 302 到后台配置的跳转地址（未配置则 503）。

## 5. 首次部署

```sh
./deploy.sh vN              # N 为本地 release.ps1 产出的最新版本
# 生成初始管理员（幂等，密码仅打印一次，妥善保存）：
docker compose -f docker-compose.next.yml run --rm app \
  sh -c "cd server && npx tsx scripts/seed-admin.ts"
```

验证：浏览器开 `http://<IP>:8002/admin/` 登录面板；`http://<服务器IP>/` 应 302 进下载页（default_server 裸 IP 直达，域名同样）；真机 App 暂不受影响（仍打旧服务器）。

## 6. 日常发版与回滚

```sh
# 本地（Windows，仓库根）：
.\scripts\release.ps1       # 版本自动 +1，构建 + 推 vN 与 latest
# 服务器：
./deploy.sh vN              # 回滚 = 指定旧 vN 重跑
```

## 7. 数据迁移窗口（另约，ADR 0003 割接流程）

1. 冻结旧后台写入
2. 旧站点媒体目录同步到新服务器：`rsync -av 旧服务器:/www/wwwroot/backend/ /opt/legacy-media/`（含 `images/ uploads/ lrc/`）
3. 跑迁移（数据 + 媒体按原路径上传 OSS，详见 docker-compose.next.yml 头部注释）
4. 黄金样本契约测试 + 真机主流程验证 → 改 App `BASE_URL` 发版 → 旧栈下线

## 8. 常见排查

| 现象 | 排查 |
|---|---|
| 容器起不来 / DB 连接拒绝 | `.env` 的 `DB_PASSWORD` 与宝塔账号是否一致；账号 host 是否 `172.%`（compose 网段是 172.19.x 等动态值，不是 172.17.x）；`bind-address` |
| 面板白屏 | 确认镜像内 `admin-web/dist` 产物资源路径带 `/admin/` 前缀（`--base=/admin/`） |
| 媒体 403 | OSS bucket 私有读（历史坑），确认走 URL 签名/公共读配置 |
| 统计里设备 IP 是反代地址 | nginx 两个头都没透传：代码取头顺序 XFF 首段 → X-Real-IP → req.ip，X-Real-IP 与 X-Forwarded-For 至少配一个（见第 4 节） |
| 管理员密码丢失 | 删 `admin_users` 表记录后重跑 seed-admin.ts |
| 下载页 404/502、或访问又跳回主站 | 检查 80 default_server：server 级 `return 301` 必须下移到 `location /`（server 级 return 先于 location 匹配执行，留在顶层则 /download/ 不可达）；改完 `nginx -t && nginx -s reload` |
| 下载页能开、点下载 503 | 后台「版本更新」的跳转地址未配置（`/download/apk` 显式返回 503），去 Admin 面板填写安装包直链 |
