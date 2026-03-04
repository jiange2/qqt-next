# 数据库结构

> **何时阅读**：当你要新增字段、修改表结构、理解数据关系，或调试 SQL 查询时，阅读此文件。

## 功能概述

数据库使用 MySQL 8.0，字符集 utf8mb4。完整 Schema 在 `install/database.sql`，Docker 部署时自动初始化。核心实体为歌曲（`tbl_mp3`）、分类、艺术家、专辑、播放列表、横幅，用户行为数据（收藏、评分、评论）通过独立关联表存储。

## 关键文件

| 文件 | 职责 |
|------|------|
| `install/database.sql` | 完整建表 + 初始数据（含默认 admin 账号、示例数据） |
| `includes/connection.php` | 建立连接，读取 `tbl_settings` 为全局常量 |

## 数据表一览

### 核心内容表

#### `tbl_mp3`（歌曲）
| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | int PK | 歌曲 ID |
| `cat_id` | int | 关联 `tbl_category.cid` |
| `mp3_title` | varchar | 歌曲标题 |
| `mp3_url` | varchar | 文件路径（本地）或外链 URL |
| `mp3_type` | varchar | `local` / `youtube` / `external` |
| `mp3_thumbnail` | varchar | 封面文件名（`images/` 下） |
| `mp3_description` | text | 描述 |
| `mp3_lrc_txt` | text | 内嵌 LRC 歌词文本 |
| `mp3_lrc_url` | varchar | 外部 LRC 文件 URL |
| `total_views` | int | 播放量（API 查询时累加） |
| `total_download` | int | 下载量 |
| `total_rate` | int | 评分总次数 |
| `rate_avg` | decimal | 平均评分 |
| `status` | tinyint | 1=上线，0=下架 |

#### `tbl_category`（分类）
| 字段 | 说明 |
|------|------|
| `cid` PK | 分类 ID |
| `category_name` | 分类名 |
| `category_image` | 封面文件名 |
| `status` | 1=显示，0=隐藏 |

#### `tbl_artist`（艺术家）
| 字段 | 说明 |
|------|------|
| `id` PK | 艺术家 ID |
| `artist_name` | 艺术家名称 |
| `artist_image` | 头像文件名 |

#### `tbl_album`（专辑）
| 字段 | 说明 |
|------|------|
| `aid` PK | 专辑 ID |
| `album_name` | 专辑名称 |
| `album_image` | 封面文件名 |
| `artist_ids` | 关联艺术家 ID，逗号分隔（非外键，查询时用 `FIND_IN_SET`） |
| `status` | 1=显示，0=隐藏 |

#### `tbl_banner`（首页横幅）
| 字段 | 说明 |
|------|------|
| `bid` PK | 横幅 ID |
| `banner_title` | 标题 |
| `banner_sort_info` | 简介 |
| `banner_image` | 图片文件名 |
| `banner_songs` | 关联歌曲 ID，逗号分隔（查询时用 `WHERE id IN (...)` ） |
| `link` | 跳转链接 |
| `status` | 1/0 |

#### `tbl_playlist`（播放列表）
| 字段 | 说明 |
|------|------|
| `pid` PK | 播放列表 ID |
| `playlist_name` | 名称 |
| `playlist_image` | 封面 |
| `status` | 1/0 |

> 播放列表与歌曲的关联通过 `tbl_playlist_songs` 表实现（`pid`, `song_id`）。

### 用户相关表

#### `tbl_users`（注册用户）
| 字段 | 说明 |
|------|------|
| `id` PK | 用户 ID |
| `name` | 用户名 |
| `email` | 邮箱（唯一） |
| `password` | MD5 哈希密码 |
| `image` | 头像 |
| `status` | 1=正常，0=禁用 |
| `verified` | 邮箱是否已验证 |

#### `tbl_favourite`（收藏）
| 字段 | 说明 |
|------|------|
| `id` PK | — |
| `user_id` | 关联 `tbl_users.id` |
| `post_id` | 被收藏内容 ID |
| `type` | `song`（目前仅歌曲） |

#### `tbl_comments`（评论）
`user_id`, `post_id`, `comment`, `date_time`

#### `tbl_song_suggest`（歌曲建议）
用户提交希望上架的歌曲，后台 manage_suggestion.php 管理。

#### `tbl_reports`（举报）
用户对歌曲/内容的举报记录，后台 manage_reports.php 管理。

#### `tbl_active_log`（用户活跃日志）
`user_id`, `date_time`（Unix 时间戳）。

### 系统表

#### `tbl_settings`（全局设置，永远只有 id=1 的一行）
| 关键字段 | 说明 |
|---------|------|
| `app_name` | App 名称 |
| `app_logo` | App Logo 文件名 |
| `package_name` | 必须与 Android AppConfig.PACKAGE_NAME 一致 |
| `api_latest_limit` | 首页/列表接口默认返回条数 |
| `api_cat_order_by` / `api_cat_post_order_by` | 分类/歌曲排序字段 |
| `onesignal_app_id` / `onesignal_rest_key` | 推送通知凭据 |
| `email_from` | 系统邮件发件地址 |
| `envato_buyer_name` / `envato_purchase_code` / `envato_purchased_status` | Envato 购买验证（未通过时 API 全部返回错误） |
| `smtp_*` | SMTP 邮件配置 |

#### `tbl_admin`（管理员账号，后台登录用）
`username`, `password`（MD5），`email`, `image`

## 注意事项

- 专辑的 `artist_ids` 和横幅的 `banner_songs` 均为逗号分隔字符串（非标准外键），增删时需在后台手动维护，API 查询时使用 `FIND_IN_SET` 或 `IN (...)`
- 所有时间字段均为 Unix 时间戳字符串（varchar），不使用 MySQL DATETIME 类型
- 首次部署执行 `install/database.sql` 后，默认管理员账号为 `admin` / `admin`（明文，MD5 后存储），**生产环境务必修改**
