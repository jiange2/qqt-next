# API 接口层

> **何时阅读**：当你要新增 API 方法、修改现有 API 响应字段、调整签名验证逻辑，或排查 Android 端接口调用问题时，阅读此文件。

## 功能概述

所有 Android 客户端请求均通过 `api.php` 这一个入口处理。请求体使用统一的编码格式（`base64(urlencode(json))`），API 内部通过 `method_name` 字段路由到对应的处理逻辑。签名验证在 `includes/function.php` 的 `checkSignSalt()` 函数中执行。

## 关键文件

| 文件 | 职责 |
|------|------|
| `api.php` | API 唯一入口：验证签名、按 `method_name` 分发逻辑、查库、返回 JSON |
| `includes/function.php` | `checkSignSalt()` 解码并验证签名；`getBaseUrl()` 生成资源 URL 前缀 |
| `includes/connection.php` | 建立 MySQLi 连接，从 `tbl_settings` 读取全局常量（含 `PACKAGE_NAME`） |
| `language/app_language.php` | API 返回的多语言文本（如错误提示） |

## 请求流程

1. Android 构造参数 JSON，`urlencode` → `base64_encode` 得到 `data` 字段
2. POST 到 `api.php`，body 为 `data=<编码串>`
3. `api.php` 调用 `checkSignSalt($_POST['data'])` 解码并验证：
   - `base64_decode` → `urldecode` → `json_decode`
   - 校验 `package_name == PACKAGE_NAME`（来自 `tbl_settings`）
   - 校验 `sign == md5("viaviweb" + salt)`
   - 验证失败返回 `{ "ONLINE_MP3": [{"success": -1, "msg": "Invalid sign salt."}] }`
4. 取出 `method_name`，进入对应 `if` 分支处理
5. 查询 MySQL，组装数组，`json_encode` 后输出

## 签名验证

```php
// includes/function.php — checkSignSalt($data)
$decoded = json_decode(urldecode(base64_decode($data)), true);
$sign_check = md5("viaviweb" . $decoded['salt']);
if ($decoded['sign'] != $sign_check || $decoded['package_name'] != PACKAGE_NAME) {
    // 返回错误
}
```

> **注意**：`PACKAGE_NAME` 常量在 `connection.php` 启动时从 `tbl_settings` 读取。修改后台包名配置会导致所有 App 请求鉴权失败。

## 所有 API 方法（method_name）

| method_name | 说明 | 关键参数 |
|-------------|------|---------|
| `home` / `home_new` | 首页聚合（Banner + 专辑 + 艺术家 + 趋势歌曲） | `user_id`（可选） |
| `latest` / `all_songs` | 最新/全部歌曲列表（分页） | `page`, `user_id` |
| `cat_list` | 分类列表（分页） | `page` |
| `cat_songs` | 分类下的歌曲（分页） | `cat_id`, `page`, `user_id` |
| `artist_list` | 艺术家列表（分页） | `page` |
| `artist_songs` | 艺术家的歌曲（分页） | `artist_id`, `page`, `user_id` |
| `album_list` | 专辑列表（分页） | `page` |
| `album_songs` | 专辑下的歌曲（分页） | `album_id`, `page`, `user_id` |
| `playlist` | 播放列表列表（分页） | `page` |
| `playlist_songs` | 播放列表下的歌曲 | `playlist_id`, `page`, `user_id` |
| `song_search` | 搜索歌曲 | `search_text`, `search_type`, `page`, `user_id` |
| `get_favourite_post` | 获取用户收藏 | `user_id`, `type`('song'), `page` |
| `favourite_post` | 切换收藏状态 | `post_id`, `user_id`, `type` |
| `get_recent_songs` | 根据 ID 串查歌曲详情 | `songs_ids`(逗号分隔), `page`, `user_id` |
| `login` | 用户登录 | `email`, `password` |
| `register` | 用户注册 | `name`, `email`, `password` |
| `app_info` | 获取应用基础信息（名称/Logo/版本配置） | — |

## 响应格式

```json
// 列表型：
{ "ONLINE_MP3": [ { "id": "1", "mp3_title": "...", ... } ] }

// 首页聚合型（home_new）：
{ "ONLINE_MP3": { "home_banner": [...], "trending_songs": [...], "latest_album": [...], "latest_artist": [...] } }

// 操作型（如收藏）：
{ "ONLINE_MP3": [ { "success": "1", "msg": "Added to favourite" } ] }
```

> 所有数值字段（如 `id`、`total_views`）在后端以字符串形式返回。Android 端的 `BooleanAdapter` 处理 `is_favourite` 字段的 `"0"`/`"1"` → Boolean 转换。

## 注意事项

- `api.php` 顶部会检查 `envato_purchased_status`，未激活的情况下所有接口返回购买码验证失败
- 歌曲 `mp3_type` 为 `"local"` 时，`mp3_url` 是服务器上 `uploads/` 目录的绝对 URL；为 `"youtube"` 或 `"external"` 时直接为外链
- 分页参数 `page` 从 `1` 开始，每页条数由 `tbl_settings.api_latest_limit` 控制
- LRC 歌词：`mp3_lrc_txt` 为内嵌文本，`mp3_lrc_url` 为外部 LRC 文件 URL
