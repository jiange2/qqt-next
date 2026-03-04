# Backend（后端）

> **何时阅读**：当你需要修改 API 接口、管理后台页面、数据库结构、鉴权逻辑或媒体文件处理时，首先阅读此文件。

## 模块职责

后端提供两项核心功能：一是供 Android 客户端调用的 RESTful API（统一入口 `api.php`）；二是供管理员操作的 Web 管理面板（内容增删改查、推送通知、用户管理）。通过 Docker Compose（PHP 7.4 + Apache + MySQL 8.0）部署。

## 技术栈

- PHP 7.4（原生 MySQLi，无框架）
- Apache HTTP Server（mod_rewrite 已启用）
- MySQL 8.0
- PHPMailer（SMTP 邮件发送）
- OneSignal（推送通知，REST API 调用）
- RichFileManager（`filemanager/` 目录，Web 文件管理器）

## 目录结构

```
backend/
  api.php                 # Android API 唯一入口：所有 method_name 在此分发处理
  includes/
    connection.php        # 数据库连接 + 全局设置常量（从 tbl_settings 读取）
    function.php          # 公共工具函数（Insert/Update/Delete/checkSignSalt 等）
    session_check.php     # 管理后台 Session 鉴权守卫（require 此文件即保护页面）
    header.php / footer.php  # 管理后台 HTML 模板
    lb_helper_*.php       # 安装向导帮助函数
  install/
    database.sql          # 完整数据库 Schema（首次部署时导入）
    index.php             # Web 安装向导入口
  language/
    app_language.php      # API 返回文字（如错误消息）- 供 api.php 使用
    language.php          # 后台管理面板提示文字
  images/                 # 封面图、Banner 图原图
    thumbs/               # 缩略图（由 thumbnail_images.class.php 生成）
  uploads/                # 上传的 MP3 文件（本地存储型歌曲）
  lrc/                    # LRC 歌词文件
    thumbs/               # LRC 封面缩略图
  assets/                 # 后台静态资源（CSS/JS/字体/CKEditor/SweetAlert）
  filemanager/            # RichFileManager Web 文件管理器
  vendor/                 # Composer 依赖（PHPMailer）
  manage_mp3.php          # 歌曲管理页
  manage_artist.php       # 艺术家管理页
  manage_album.php        # 专辑管理页
  manage_category.php     # 分类管理页
  manage_playlist.php     # 播放列表管理页
  manage_banners.php      # 横幅管理页
  manage_users.php        # 用户管理页
  manage_reports.php      # 举报管理页
  manage_suggestion.php   # 歌曲建议管理页
  add_mp3.php             # 新增/编辑歌曲表单
  add_artist.php          # 新增/编辑艺术家表单
  add_album.php           # 新增/编辑专辑表单
  add_category.php        # 新增/编辑分类表单
  add_banner.php          # 新增/编辑 Banner 表单
  add_playlist.php        # 新增/编辑播放列表表单
  add_user.php            # 新增/编辑用户表单（后台）
  edit_mp3.php            # 编辑歌曲（与 add_mp3.php 共用逻辑）
  processData.php         # 后台 AJAX 操作接口（状态切换/删除/批量操作）
  getData.php             # 后台 AJAX 数据读取
  login_db.php            # 管理员登录处理
  logout.php              # 管理员退出
  send_notification.php   # 推送通知页面（OneSignal）
  settings.php            # 后台设置页面
  smtp_settings.php       # SMTP 配置页面
  smtp_email.php          # 公共邮件发送函数
  verification.php        # 用户邮箱验证处理
  profile.php             # 管理员个人资料
  home.php                # 后台首页仪表盘
  privacy_policy.php      # 隐私政策页面
  pagination.php          # 分页公共组件
  uploadscript.php        # 文件上传处理
  thumbnail_images.class.php  # 缩略图生成类
```

## 功能模块索引

| 功能 | 文档 | 说明 |
|------|------|------|
| API 接口（Android 端调用） | [`doc/api.md`](doc/api.md) | method_name 路由、签名验证、所有 API 方法 |
| 管理后台（内容管理面板） | [`doc/admin.md`](doc/admin.md) | manage_*.php / add_*.php / processData.php |
| 数据库表结构 | [`doc/db.md`](doc/db.md) | 所有 tbl_* 表字段说明 |
| 管理员登录/Session 鉴权 | [`doc/auth.md`](doc/auth.md) | login_db.php / session_check.php |
| 媒体文件、歌词、缩略图 | [`doc/media.md`](doc/media.md) | uploads/ lrc/ images/thumbs/ filemanager/ |

## 关键配置

配置全部存储在数据库 `tbl_settings`（id=1），在 `includes/connection.php` 启动时读取并定义为常量：

| 常量 | 说明 |
|------|------|
| `PACKAGE_NAME` | 必须与 Android App 的 `AppConfig.PACKAGE_NAME` 一致，API 鉴权时校验 |
| `ONESIGNAL_APP_ID` / `ONESIGNAL_REST_KEY` | 推送通知凭据 |
| `APP_NAME` / `APP_LOGO` | 应用名称和Logo（API 响应中返回） |
| `API_LATEST_LIMIT` | 首页/列表接口默认返回条数 |
| `API_CAT_ORDER_BY` / `API_CAT_POST_ORDER_BY` | 分类/歌曲排序方式 |

数据库连接通过环境变量读取（Docker 友好）：`DB_HOST`, `DB_USER`, `DB_PASSWORD`, `DB_NAME`，fallback 到硬编码默认值（`includes/connection.php`）。

## 与其他模块的接口

- **对外暴露**：`api.php`（POST，接受 `data` 字段，返回 JSON）
- **依赖**：MySQL 数据库、`uploads/` 可写目录、`images/` 可写目录

## 开发指南

```bash
# 使用 Docker Compose 启动（推荐）
docker-compose up -d

# 访问管理后台
http://localhost:8000/

# 首次部署：数据库自动从 backend/install/database.sql 初始化
# 默认管理员账号：admin / admin（见 database.sql）
```
