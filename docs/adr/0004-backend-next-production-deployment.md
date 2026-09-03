# backend-next 生产部署：ACR 镜像交付 + 宿主机 MySQL + Nginx 双端口明文

backend-next 上线新服务器。部署形态与旧 PHP 栈（ADR 0002 的热挂载单实例）彻底分道：

**决定**：

1. **镜像化交付**：本地 `release.ps1` 构建 → 推送阿里云 ACR（双 tag：自增版本 `vN` + `latest`，版本号由仓库内 `IMAGE_VERSION` 单调 +1）→ 服务器 `deploy.sh` 只做 `pull + up -d`。回滚 = 指定旧 vN 重跑。与 ADR 0002 的热挂载相反：新栈需要构建产物（TS 编译 + Vite 构建），且部署机与开发机分离，热挂载不可行。
2. **MySQL 外置宿主机**（宝塔安装），`docker-compose.next.yml` 不含 `db` 服务：容器经 `host-gateway` 以专用账号 `qqt_next@172.%` 连入（compose 自建网络是动态网段而非默认网桥 172.17.x，host 必须放宽到 `172.%`；3306 不对公网开放）。与旧栈「db 进容器」不同，复用服务器上现成的 MySQL 而非再造一套。
3. **Nginx 双端口分流**：API `:8001`、Admin `:8002`，均反代到容器 `127.0.0.1:8000`（仅本机绑定）。Fastify 为单端口同进程双路由（`/legacy` + `/admin`），拆端口选择在反代层做而非应用改双 listener——零代码改动。
4. **明文 HTTP，无 HTTPS**：App 现状即明文（`http://47.111.25.157/` 硬编码），用户明确决策本次不上证书；端口契约会写进 App `BASE_URL`，**换端口 = 发版，视为不可逆**。
5. **空库先上线**（迁移另约窗口）：初始管理员从 `migrate.ts` 的 `seedAdmin()` 抽出为独立幂等脚本 `seed-admin.ts`，解耦「部署链路验证」与「数据迁移」两个风险源。

**被否决的替代**：db 容器化（宝塔已有 MySQL，双份运维面）；应用双 listener（改代码、重建镜像，收益仅省两行 Nginx 配置）；HTTPS（需域名与证书，App 端明文现状未变，后续可单独给 Admin 端口补上）。

**关键配套**：admin-web 构建须 `--base=/admin/`（Vite 默认 `/assets/` 绝对路径在 `/admin/` 前缀托管下必然白屏，已修复进构建脚本）。
