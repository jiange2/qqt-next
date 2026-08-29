# Docker Compose 单实例部署：源码热挂载 + SQL 自动初始化

后端通过 `docker-compose.yml` 以单实例部署：`web`（PHP 7.4 + Apache）与 `mysql`（MySQL 8.0）两个服务，`backend/` 目录直接以卷挂载进容器（`./backend:/var/www/html`），数据库首次启动时挂载 `backend/install/database.sql` 到 `docker-entrypoint-initdb.d` 自动建表。

选择热挂载而非把代码打进镜像，是因为项目是单人维护的单实例，改动即时生效（无需重建镜像）的价值远大于镜像自包含的价值。代价是：**该部署方式不支持横向扩展**（多实例会因本地媒体文件不同步而失败，见 Backend ADR 0001）；若要扩展，需先把媒体存储迁出本地文件系统，再考虑镜像化。
