# QQT 项目 - Docker 快速启动指南

## 前置条件
- 已安装 Docker
- 已安装 Docker Compose

## 启动方法

### 1. 启动容器

在项目根目录（`d:\Project\qqt`）打开终端，运行：

```powershell
docker-compose up -d
```

这会：
- 构建 PHP + Apache 镜像
- 启动 PHP web 服务（端口 8080）
- 启动 MySQL 数据库（端口 3306）
- 自动导入数据库表

### 2. 访问应用

浏览器打开：
```
http://localhost:8080
```

### 3. 登录凭证

- **用户名**：admin
- **密码**：admin

---

## 常用命令

### 查看容器日志
```powershell
docker-compose logs -f web
docker-compose logs -f mysql
```

### 停止容器
```powershell
docker-compose down
```

### 删除数据并重新开始
```powershell
docker-compose down -v
docker-compose up -d
```

### 进入 PHP 容器
```powershell
docker-compose exec web bash
```

### 进入 MySQL 容器
```powershell
docker-compose exec mysql mysql -uroot -pcobbe qqt
```

---

## 服务信息

| 服务 | 主机 | 端口 | 用户名 | 密码 |
|------|------|------|--------|------|
| **Web (Apache)** | localhost | 8080 | - | - |
| **MySQL** | localhost | 3306 | root | lq520977WC |
| **数据库名** | qqt | - | - | - |

---

## 文件说明

- `Dockerfile` - PHP 7.4 + Apache 镜像配置
- `docker-compose.yml` - 容器编排配置
- `backend/` - 项目应用代码

---

## 常见问题

### Q: 端口 8080 被占用怎么办？
**A:** 编辑 `docker-compose.yml`，修改：
```yaml
ports:
  - "8888:80"  # 改成其他端口，如 8888
```

### Q: 数据库连接失败？
**A:** 检查是否已执行 `docker-compose up -d`，并稍等几秒让 MySQL 完全启动。

### Q: 需要修改数据库密码？
**A:** 编辑 `docker-compose.yml` 中的 `MYSQL_ROOT_PASSWORD` 环境变量。

