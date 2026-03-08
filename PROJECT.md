# QQT Music（倾轻听）

> **这是 AI 导航入口**。读完此文件你会知道项目由哪些模块组成、各模块做什么、以及要修改某功能时应该去哪里。

## 项目概述

QQT Music 是一个在线音乐流媒体应用，由 PHP 后端管理系统 + RESTful API，以及 Android 客户端两部分组成。后端负责内容管理和数据接口，Android 端负责用户播放体验。

## 整体架构

```
Android App (Kotlin/Jetpack Compose)
        │  POST api.php (base64+urlencode+JSON)
        ▼
Backend (PHP 7.4 + Apache)
        │  MySQLi
        ▼
MySQL 8.0 数据库
```

Android 端通过统一的 `api.php` 接口获取所有数据（歌曲、分类、专辑、艺术家、播放列表等）。后端还包含一套 Web 管理面板，供管理员上传歌曲、管理内容。整个后端可通过 Docker Compose 一键部署（PHP+Apache 容器 + MySQL 容器）。

## 模块一览

| 模块 | 目录 | 技术栈 | 职责 |
|------|------|--------|------|
| 后端 | `backend/` | PHP 7.4, Apache, MySQL 8.0 | API 接口 + Web 管理面板 + 文件存储 |
| Android | `android/` | Kotlin, Jetpack Compose, ExoPlayer, Retrofit2 | 音乐播放客户端 |

## 导航指引

根据要修改的内容，直接跳转：

- **API 接口逻辑**（新增/修改接口方法） → 阅读 [`backend/PROJECT.md`](backend/PROJECT.md)，再查 [`backend/doc/api.md`](backend/doc/api.md)
- **管理后台**（管理员面板、内容管理页面） → 阅读 [`backend/doc/admin.md`](backend/doc/admin.md)
- **数据库表结构** → 阅读 [`backend/doc/db.md`](backend/doc/db.md)
- **后端登录/鉴权** → 阅读 [`backend/doc/auth.md`](backend/doc/auth.md)
- **媒体文件上传/LRC歌词/缩略图** → 阅读 [`backend/doc/media.md`](backend/doc/media.md)
- **Android API 请求逻辑/数据模型** → 阅读 [`android/doc/api.md`](android/doc/api.md)
- **Android 音乐播放器（播放控制/队列/缓存/元数据）** → 阅读 [`android/doc/player.md`](android/doc/player.md)
- **Android 后台保活/自启动**（前台服务/WiFi 锁/设备重启） → 阅读 [`android/doc/background.md`](android/doc/background.md)
- **Android UI/导航/页面** → 阅读 [`android/doc/ui.md`](android/doc/ui.md)
- **Android 本地存储**（最近播放/已下载/播放进度） → 阅读 [`android/doc/storage.md`](android/doc/storage.md)

## 跨模块约定

### API 鉴权

所有 Android → 后端请求均使用相同的签名方案：

```
data = base64_encode(urlencode(json_encode({
    "method_name": "...",
    // 业务参数...
    "package_name": "com.vpapps.onlinemp3",   // 需与后台 tbl_settings 配置一致
    "salt": "<随机串>",
    "sign": md5("viaviweb" + salt)
})))
```

后端在 `includes/function.php` 的 `checkSignSalt()` 函数中验证签名。

### 响应格式

所有 API 响应均为 JSON，根节点为 `ONLINE_MP3`：

```json
{ "ONLINE_MP3": [ ... ] }          // 列表响应
{ "ONLINE_MP3": { ... } }          // 对象响应（homeData 等）
```

### 部署

- 开发/生产部署使用 `docker-compose.yml`（PHP+Apache 端口 8000，MySQL 端口 3306）
- 数据库初始 Schema：`backend/install/database.sql`
- 环境变量：`DB_HOST`, `DB_USER`, `DB_PASSWORD`, `DB_NAME`（见 `.env.example`）
