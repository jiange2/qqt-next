# 管理后台

> **何时阅读**：当你要修改管理员面板的内容管理页面（歌曲/艺术家/专辑/分类/横幅/用户）、AJAX 操作逻辑，或后台表单/列表页时，阅读此文件。

## 功能概述

后台管理面板是一套基于 PHP 原生渲染的 Web 界面，供管理员管理音乐内容。核心操作（状态切换、删除、批量操作）通过 AJAX 调用 `processData.php` 完成，数据展示通过 `getData.php` 提供分页列表。

## 关键文件

| 文件 | 职责 |
|------|------|
| `home.php` | 后台首页仪表盘（统计数据概览） |
| `manage_mp3.php` | 歌曲列表页（搜索、分页、状态切换、删除） |
| `manage_artist.php` | 艺术家列表页 |
| `manage_album.php` | 专辑列表页 |
| `manage_category.php` | 分类列表页 |
| `manage_playlist.php` | 播放列表列表页 |
| `manage_banners.php` | 横幅列表页 |
| `manage_users.php` | 用户列表页 |
| `manage_reports.php` | 用户举报管理页 |
| `manage_suggestion.php` | 用户歌曲建议管理页 |
| `add_mp3.php` | 新增歌曲表单（含艺术家绑定、专辑绑定、LRC、封面上传） |
| `edit_mp3.php` | 编辑歌曲（与 add_mp3.php 逻辑一致，预填充数据） |
| `add_artist.php` | 新增/编辑艺术家表单 |
| `add_album.php` | 新增/编辑专辑表单 |
| `add_category.php` | 新增/编辑分类表单 |
| `add_banner.php` | 新增/编辑横幅表单（含歌曲 ID 多选） |
| `add_playlist.php` | 新增/编辑播放列表表单 |
| `add_user.php` | 新增/编辑用户表单（后台管理员操作） |
| `processData.php` | 后台 AJAX 操作接口（POST，返回 JSON） |
| `getData.php` | 后台 AJAX 数据获取（分页列表数据） |
| `pagination.php` | 分页组件（公共引用） |
| `send_notification.php` | OneSignal 推送通知页面 |
| `settings.php` | 应用全局设置（包名/API 限制/OneSignal等） |
| `smtp_settings.php` | SMTP 邮件配置 |
| `user_profile.php` | 用户资料查看页 |
| `profile.php` | 管理员个人设置 |
| `verification.php` | 用户邮箱验证处理（点击邮件链接后跳转此页） |

## 数据流 / 调用链

### 列表页（manage_*.php）

1. `require("includes/session_check.php")` — 验证管理员 Session，未登录重定向到 `login_db.php`
2. `include("includes/header.php")` — 渲染 HTML 头部、导航栏
3. 页面初始渲染：PHP 直接查询 MySQL，输出列表 HTML
4. 前端：状态切换/删除按钮触发 AJAX → `processData.php`
5. `include("includes/footer.php")` — 渲染 HTML 尾部

### processData.php（AJAX 操作）

通过 `$_POST['action']` 分发：

| action | 说明 |
|--------|------|
| `toggle_status` | 切换指定记录的状态字段（0/1）；通用：传 `table`、`column`、`tbl_id`、`id` |
| `removeData` | 删除单条记录；删除用户时级联删除 comments / song_suggest / reports |
| `multi_delete` | 批量删除（传 `id[]` 数组） |
| `add_to_playlist` | 将歌曲加入播放列表 |
| `add_comment` | 用户评论 |
| `add_rating` | 歌曲评分（同时更新 tbl_mp3.total_rate / rate_avg） |
| `add_song_suggest` | 用户提交歌曲建议 |
| `add_report` | 用户举报 |
| `update_*` | 各种字段更新 |

### getData.php（AJAX 数据）

POST 传 `for_action`，返回 JSON 分页数据，供管理列表的 DataTables 等组件使用。

## 公共组件

- `includes/header.php` — 管理后台通用头部（导入 CSS/JS + 顶部导航 + 侧边菜单）
- `includes/footer.php` — 通用页脚（JS 脚本）
- `assets/` — 静态资源：`css/flat-admin.css`（主样式）、Bootstrap、jQuery、DataTables、CKEditor、SweetAlert、OneSignal JS SDK

## 注意事项

- 所有管理页面首行均需 `include("includes/session_check.php")`，否则无鉴权保护
- `processData.php` 中的 `removeData` action 在删除用户时会级联删除关联数据（3 张表），删除其他实体时无级联逻辑，需手动维护外键一致性
- 歌曲的 `status=0` 会在 API 查询中被过滤（`WHERE status='1'`），可用于下架歌曲而不删除数据
- 推送通知使用 OneSignal REST API，凭据存储在 `tbl_settings`（`onesignal_app_id` / `onesignal_rest_key`）
