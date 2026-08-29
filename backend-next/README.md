# Backend-Next

用 Node.js + TypeScript + Vue 3 重写的后端服务，语义兼容替换 PHP 版 Backend（决策见 [docs/adr/0003-nodejs-rewrite-with-legacy-facade.md](../docs/adr/0003-nodejs-rewrite-with-legacy-facade.md)）。

```
backend-next/
├── server/          Fastify 服务（pnpm workspace 包）
│   ├── src/legacy/    遗留协议门面：32 个 method，POST /api.php（等价 /legacy/api.php）
│   ├── src/admin/     管理端 REST API：/admin/*，JWT 鉴权
│   ├── src/media/     图片压缩（sharp）与阿里云 OSS 上传（ADR 0004）
│   ├── src/services/  设置缓存、视图映射等服务层
│   ├── prisma/        新库 schema（MySQL，关联规范化）
│   └── scripts/       migrate.ts（一次性迁移）、smoke.ts（契约冒烟）
└── admin-web/       Vue 3 + Element Plus 管理面板（构建产物由 server 托管于 /admin/）
```

## 本地开发

前置：Node 22+、pnpm（corepack）、MySQL 8.0。

```bash
# 1. 依赖（.npmrc 已置空代理；Prisma 引擎走 npmmirror 镜像需在 shell 里设
#    PRISMA_ENGINES_MIRROR=https://registry.npmmirror.com/-/binary/prisma）
pnpm install

# 2. 配置 server/.env（参考 server/.env.example）
#    DATABASE_URL=新库   LEGACY_DB_URL=旧库   JWT_SECRET=强随机串
#    OSS_REGION / OSS_BUCKET / OSS_ACCESS_KEY_ID / OSS_ACCESS_KEY_SECRET /
#    OSS_PUBLIC_BASE（媒体 URL 基地址，开发用独立测试 bucket）

# 3. 建新库并应用 schema
cd server
npx prisma migrate dev

# 4. 从旧库一次性迁移内容资产（保留原 ID；--media-src 指向旧 backend 站点目录）
pnpm migrate:legacy --media-src=../../backend

# 5. 启动（两个终端）
pnpm dev                          # server: http://127.0.0.1:8000
pnpm --filter admin-web dev       # 面板:  http://127.0.0.1:5173（vite 代理 /admin /api.php）
```

管理员账号由 `migrate.ts` 在 admin_users 表为空时创建（用户名 `admin`，随机密码仅打印一次；也可手动插入后自行 bcrypt 加密）。

## 契约冒烟验证

对运行中的 server 发起全部 32 个 method 的代表性请求（含验签失败、未知 method、注册→登录→评分→收藏→下载写路径、无效 user_id -2 分支），输出响应结构摘要供人工比对：

```bash
cd server && pnpm smoke [--base=http://127.0.0.1:8000]
```

## Docker 部署

```bash
OSS_REGION=oss-cn-xxx \
OSS_BUCKET=your-bucket \
OSS_ACCESS_KEY_ID=xxx OSS_ACCESS_KEY_SECRET=xxx \
OSS_PUBLIC_BASE=https://your-bucket.oss-cn-xxx.aliyuncs.com/ \
JWT_SECRET=$(openssl rand -hex 32) \
docker compose -f docker-compose.next.yml up -d --build
```

- `app` 监听宿主 **8001**（旧 PHP 栈仍占 8000，两栈平行互不影响）
- 新库 `qqt_next` 由容器启动时 `prisma migrate deploy` 自动建表
- 媒体文件存阿里云 OSS（ADR 0004）：上传可设 `OSS_INTERNAL=true` 走同 region 内网（免流量费）；对外 URL 由 `OSS_PUBLIC_BASE` 拼接，换 CDN 只改此值；bucket 需设公共读（建议另配 Referer 防盗链）
- 管理面板：`http://<host>:8001/admin/`

## 协议要点（复刻自旧 api.php / function.php）

- 请求：`application/x-www-form-urlencoded`，字段 `data=base64(urlencode(json))`；`sign` 与 `salt` 放在 **data JSON 内部**，`sign = md5("viaviweb" + salt)`
- 验签/package_name 失败：`{"ONLINE_MP3":[{"success":-1,"msg":"Invalid sign salt."}]}`；user_id 无效：`success:-2`
- 数值字段全部字符串化；`ONLINE_MP3` 根节点：`home`/`home_new`/`song_search` 直接是对象，其余是数组
- 已知怪癖按旧实现逐字复刻（如 `app_download_url` 恒为 null、banner_songs 的 `link` 字段实为分类 ID）；已实锤缺陷（favourite_post 移除失败返回 success:"1" 之外的行为、重复邮箱死分支）按本意修复，见 ADR 0003
