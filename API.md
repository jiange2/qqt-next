# API 接口文档

本项目为在线音乐播放器后端，所有 API 请求均通过 `api.php` 处理。

## 基础信息

- **API 地址**: `{BASE_URL}/api.php`
- **请求方式**: `POST`
- **响应格式**: `JSON`
- **响应根节点**: `ONLINE_MP3`

---

## 请求规范

所有接口均通过同一个 `POST` 请求调用，**请求体**格式如下：

```
POST /api.php
Content-Type: application/x-www-form-urlencoded

data=<编码后的字符串>
```

**`data` 字段的生成步骤**：

1. 将所有参数（含鉴权字段）组成一个 JSON 对象
2. 对该 JSON 字符串进行 `urlencode`
3. 再对结果进行 `base64_encode`

**JSON 中必须包含的鉴权字段**：

| 字段 | 说明 |
|------|------|
| `package_name` | 应用包名，需与后台配置一致 |
| `salt` | 任意随机字符串（每次请求可不同） |
| `sign` | 签名：`md5("viaviweb" + salt)` |

**伪代码示例**（PHP）：

```php
$params = [
    "method_name"  => "home",
    "user_id"      => 1,
    "package_name" => "com.example.musicapp",
    "salt"         => "randomStr123",
    "sign"         => md5("viaviweb" . "randomStr123"),  // = md5("viaviwebrandomStr123")
];
$data = base64_encode(urlencode(json_encode($params)));
// POST api.php  body: data=$data
```

**curl 示例**（以 `home` 接口为例）：

```bash
# 1. 先生成 data 字段（PHP）
php -r "
  \$p = ['method_name'=>'home','user_id'=>1,'package_name'=>'com.example.musicapp','salt'=>'abc123','sign'=>md5('viaviweb'.'abc123')];
  echo base64_encode(urlencode(json_encode(\$p)));
"
# 假设输出为: eyJtZXRob2RfbmFtZSI6...

# 2. 发起请求
curl -X POST http://your-domain.com/api.php \
  -d "data=eyJtZXRob2RfbmFtZSI6..."
```

> **注意**：`sign` 验证失败或 `package_name` 不匹配时，接口返回 `{ "success": -1, "msg": "Invalid sign salt." }`

---

## 目录

- [首页](#首页)
- [歌曲](#歌曲)
- [分类](#分类)
- [艺术家](#艺术家)
- [专辑](#专辑)
- [播放列表](#播放列表)
- [横幅（Banner）](#横幅banner)
- [搜索](#搜索)
- [用户](#用户)
- [收藏](#收藏)
- [应用信息](#应用信息)

---

## 首页

### home — 获取首页数据

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| method_name | string | ✅ | `home` |
| user_id | int | ❌ | 用户 ID（用于判断收藏状态） |

**响应字段**：

| 字段 | 说明 |
|------|------|
| home_banner | 横幅列表（含每个横幅的歌曲列表） |
| latest_album | 最新专辑列表 |
| latest_artist | 最新艺术家列表 |
| trending_songs | 热门歌曲列表（按播放量排名） |

**请求示例**：

```json
// POST 参数 data 对应的原始 JSON
{
  "method_name": "home",
  "user_id": 1,
  "package_name": "com.example.musicapp",
  "salt": "abc123",
  "sign": "md5('viaviwebabc123')"
}
```

**响应示例**：

```json
{
  "ONLINE_MP3": {
    "home_banner": [
      {
        "bid": "1",
        "banner_title": "热门推荐",
        "banner_sort_info": "本周热门",
        "banner_link": "",
        "banner_image": "http://your-domain.com/images/banner1.jpg",
        "banner_image_thumb": "http://your-domain.com/images/thumbs/banner1.jpg",
        "total_songs": 5,
        "songs_list": [ ... ]
      }
    ],
    "latest_album": [
      {
        "aid": "3",
        "album_name": "夏日合辑",
        "album_image": "http://your-domain.com/images/album3.jpg",
        "album_image_thumb": "http://your-domain.com/images/thumbs/album3.jpg"
      }
    ],
    "latest_artist": [ ... ],
    "trending_songs": [ ... ]
  }
}
```

---

### home_new — 获取首页数据（轻量版）

与 `home` 相同，但横幅中的 `songs_list` 为空（不加载歌曲详情），`trending_songs` 按近一个月播放量排序。

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| method_name | string | ✅ | `home_new` |
| user_id | int | ❌ | 用户 ID |

**请求示例**：

```json
{
  "method_name": "home_new",
  "user_id": 0,
  "package_name": "com.example.musicapp",
  "salt": "abc123",
  "sign": "md5('viaviwebabc123')"
}
```

---

## 歌曲

### all_songs — 获取全部歌曲（分页）

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| method_name | string | ✅ | `all_songs` |
| page | int | ✅ | 页码（从 1 开始） |
| user_id | int | ❌ | 用户 ID |

**响应字段**（每条记录）：

| 字段 | 说明 |
|------|------|
| total_songs | 总记录数 |
| id | 歌曲 ID |
| cat_id | 分类 ID |
| mp3_type | 类型：`local`（本地） / 其他（外链） |
| mp3_title | 歌曲标题 |
| mp3_url | 音频地址 |
| mp3_thumbnail_b | 大封面图 |
| mp3_thumbnail_s | 小封面缩略图 |
| mp3_artist | 艺术家 |
| mp3_description | 歌曲描述 |
| total_rate | 评分次数 |
| rate_avg | 平均评分 |
| total_views | 播放次数 |
| total_download | 下载次数 |
| is_favourite | 是否已收藏（0/1） |
| cid | 分类 ID |
| category_name | 分类名称 |
| category_image | 分类大图 |
| category_image_thumb | 分类缩略图 |

**请求示例**：

```json
{
  "method_name": "all_songs",
  "page": 1,
  "user_id": 5,
  "package_name": "com.example.musicapp",
  "salt": "abc123",
  "sign": "md5('viaviwebabc123')"
}
```

**响应示例**：

```json
{
  "ONLINE_MP3": [
    {
      "total_songs": "128",
      "id": "42",
      "cat_id": "3",
      "mp3_type": "local",
      "mp3_title": "晴天",
      "mp3_url": "http://your-domain.com/uploads/42_song.mp3",
      "mp3_thumbnail_b": "http://your-domain.com/images/song42.jpg",
      "mp3_thumbnail_s": "http://your-domain.com/images/thumbs/song42.jpg",
      "mp3_artist": "周杰伦",
      "mp3_description": "经典歌曲",
      "total_rate": "20",
      "rate_avg": "5",
      "total_views": "1024",
      "total_download": "300",
      "is_favourite": false,
      "cid": "3",
      "category_name": "华语流行",
      "category_image": "http://your-domain.com/images/cat3.jpg",
      "category_image_thumb": "http://your-domain.com/images/thumbs/cat3.jpg"
    }
  ]
}
```

---

### latest — 获取最新歌曲（分页）

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| method_name | string | ✅ | `latest` |
| page | int | ✅ | 页码 |
| user_id | int | ❌ | 用户 ID |

响应字段同 `all_songs`，`total_records` 替代 `total_songs`。

**请求示例**：

```json
{
  "method_name": "latest",
  "page": 1,
  "user_id": 0,
  "package_name": "com.example.musicapp",
  "salt": "abc123",
  "sign": "md5('viaviwebabc123')"
}
```

---

### song_info — 获取单首歌曲详情（同时增加播放次数）

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| method_name | string | ✅ | `song_info` |
| song_id | int | ✅ | 歌曲 ID |
| user_id | int | ❌ | 用户 ID |

**额外响应字段**：

| 字段 | 说明 |
|------|------|
| mp3_lrc_txt | 歌词文本 |
| mp3_lrc_url | 歌词文件地址 |
| user_rate | 当前用户已打的评分（需传 user_id） |

> 调用此接口会自动将歌曲 `total_views` +1。

**请求示例**：

```json
{
  "method_name": "song_info",
  "song_id": 42,
  "user_id": 5,
  "package_name": "com.example.musicapp",
  "salt": "abc123",
  "sign": "md5('viaviwebabc123')"
}
```

**响应示例**：

```json
{
  "ONLINE_MP3": [
    {
      "id": "42",
      "mp3_title": "晴天",
      "mp3_url": "http://your-domain.com/uploads/42_song.mp3",
      "mp3_lrc_txt": "[00:01.00]晴天...",
      "mp3_lrc_url": "http://your-domain.com/lrc/42_mp3_thumb.lrc",
      "mp3_artist": "周杰伦",
      "total_views": "1025",
      "user_rate": 5,
      "is_favourite": true
    }
  ]
}
```

---

### single_song — 获取单首歌曲（同 song_info）

与 `song_info` 功能相同，额外返回歌词字段。

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| method_name | string | ✅ | `single_song` |
| song_id | int | ✅ | 歌曲 ID |
| user_id | int | ❌ | 用户 ID |

**请求示例**：

```json
{
  "method_name": "single_song",
  "song_id": 42,
  "user_id": 5,
  "package_name": "com.example.musicapp",
  "salt": "abc123",
  "sign": "md5('viaviwebabc123')"
}
```

---

### song_download — 记录下载次数

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| method_name | string | ✅ | `song_download` |
| song_id | int | ✅ | 歌曲 ID |

**响应**：

```json
{
  "ONLINE_MP3": [{ "total_download": 100 }]
}
```

**请求示例**：

```json
{
  "method_name": "song_download",
  "song_id": 42,
  "package_name": "com.example.musicapp",
  "salt": "abc123",
  "sign": "md5('viaviwebabc123')"
}
```

---

### song_rating — 歌曲评分

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| method_name | string | ✅ | `song_rating` |
| post_id | int | ✅ | 歌曲 ID |
| user_id | int | ✅ | 用户 ID |
| rate | int | ✅ | 评分值（1-5） |

**响应**：

| 字段 | 说明 |
|------|------|
| total_rate | 评分总次数 |
| rate_avg | 平均评分 |
| msg | 操作消息 |
| success | `1` 成功，`0` 已评分过 |

**请求示例**：

```json
{
  "method_name": "song_rating",
  "post_id": 42,
  "user_id": 5,
  "rate": 5,
  "package_name": "com.example.musicapp",
  "salt": "abc123",
  "sign": "md5('viaviwebabc123')"
}
```

**响应示例**（成功）：

```json
{
  "ONLINE_MP3": [{ "total_rate": "21", "rate_avg": "5", "msg": "评分成功", "success": "1" }]
}
```

**响应示例**（已评分）：

```json
{
  "ONLINE_MP3": [{ "msg": "您已经评过分了", "success": "0" }]
}
```

---

### song_report — 举报歌曲

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| method_name | string | ✅ | `song_report` |
| user_id | int | ✅ | 用户 ID |
| song_id | int | ✅ | 歌曲 ID |
| report | string | ✅ | 举报内容 |

**请求示例**：

```json
{
  "method_name": "song_report",
  "user_id": 5,
  "song_id": 42,
  "report": "歌曲链接失效",
  "package_name": "com.example.musicapp",
  "salt": "abc123",
  "sign": "md5('viaviwebabc123')"
}
```

**响应**：`{ "msg": "...", "success": "1" }`

---

### song_suggest — 建议/推荐歌曲

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| method_name | string | ✅ | `song_suggest` |
| user_id | int | ✅ | 用户 ID |
| song_title | string | ✅ | 歌曲名称 |
| message | string | ✅ | 建议内容 |
| song_image | file | ❌ | 歌曲封面图（表单文件上传） |

**请求示例**（无图片）：

```json
{
  "method_name": "song_suggest",
  "user_id": 5,
  "song_title": "稻香",
  "message": "希望能收录这首歌",
  "package_name": "com.example.musicapp",
  "salt": "abc123",
  "sign": "md5('viaviwebabc123')"
}
```

**请求示例**（带图片，使用 multipart/form-data）：

```bash
curl -X POST http://your-domain.com/api.php \
  -F "data=<编码后的data字段>" \
  -F "song_image=@/path/to/cover.jpg"
```

**响应**：`{ "msg": "...", "success": "1" }`

---

### get_recent_songs — 获取最近播放歌曲

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| method_name | string | ✅ | `get_recent_songs` |
| songs_ids | string | ✅ | 歌曲 ID 列表，逗号分隔，如 `1,2,3` |
| user_id | int | ❌ | 用户 ID |
| page | int | ✅ | 页码 |

响应字段同 `all_songs`，`total_songs` 为总数。

**请求示例**：

```json
{
  "method_name": "get_recent_songs",
  "songs_ids": "10,25,42",
  "user_id": 5,
  "page": 1,
  "package_name": "com.example.musicapp",
  "salt": "abc123",
  "sign": "md5('viaviwebabc123')"
}
```

---

## 搜索

### song_search — 综合搜索

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| method_name | string | ✅ | `song_search` |
| search_text | string | ✅ | 搜索关键词 |
| search_type | string | ✅ | 搜索类型：`songs` / `artist` / `album` / 不填（全部） |
| page | int | ✅ | 页码 |
| user_id | int | ❌ | 用户 ID |

**search_type 说明**：
- `songs`：仅搜索歌曲，返回歌曲列表
- `artist`：仅搜索艺术家，返回艺术家列表
- `album`：仅搜索专辑，返回专辑列表
- 不传或其他值：全局搜索，返回 `search_songs`、`search_album`、`search_artist` 三个列表

**请求示例**（搜索歌曲）：

```json
{
  "method_name": "song_search",
  "search_text": "晴天",
  "search_type": "songs",
  "page": 1,
  "user_id": 5,
  "package_name": "com.example.musicapp",
  "salt": "abc123",
  "sign": "md5('viaviwebabc123')"
}
```

**请求示例**（全局搜索）：

```json
{
  "method_name": "song_search",
  "search_text": "周杰伦",
  "search_type": "all",
  "page": 1,
  "user_id": 0,
  "package_name": "com.example.musicapp",
  "salt": "abc123",
  "sign": "md5('viaviwebabc123')"
}
```

**响应示例**（全局搜索）：

```json
{
  "ONLINE_MP3": {
    "search_songs": [ { "id": "42", "mp3_title": "晴天", ... } ],
    "search_album": [ { "aid": "2", "album_name": "叶惠美", ... } ],
    "search_artist": [ { "id": "1", "artist_name": "周杰伦", ... } ]
  }
}
```

---

## 分类

### cat_list — 获取分类列表（分页）

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| method_name | string | ✅ | `cat_list` |
| page | int | ✅ | 页码 |

**响应字段**：

| 字段 | 说明 |
|------|------|
| total_records | 总记录数 |
| cid | 分类 ID |
| category_name | 分类名称 |
| category_image | 分类大图 |
| category_image_thumb | 分类缩略图 |

**请求示例**：

```json
{
  "method_name": "cat_list",
  "page": 1,
  "package_name": "com.example.musicapp",
  "salt": "abc123",
  "sign": "md5('viaviwebabc123')"
}
```

---

### cat_songs — 按分类获取歌曲（分页）

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| method_name | string | ✅ | `cat_songs` |
| cat_id | int | ✅ | 分类 ID |
| page | int | ✅ | 页码 |
| user_id | int | ❌ | 用户 ID |

响应字段同 `all_songs`。

**请求示例**：

```json
{
  "method_name": "cat_songs",
  "cat_id": 3,
  "page": 1,
  "user_id": 5,
  "package_name": "com.example.musicapp",
  "salt": "abc123",
  "sign": "md5('viaviwebabc123')"
}
```

---

## 艺术家

### recent_artist_list — 获取最新艺术家（前 10 个）

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| method_name | string | ✅ | `recent_artist_list` |

**响应字段**：

| 字段 | 说明 |
|------|------|
| id | 艺术家 ID |
| artist_name | 艺术家名称 |
| artist_image | 艺术家大图 |
| artist_image_thumb | 艺术家缩略图 |

**请求示例**：

```json
{
  "method_name": "recent_artist_list",
  "package_name": "com.example.musicapp",
  "salt": "abc123",
  "sign": "md5('viaviwebabc123')"
}
```

---

### artist_list — 获取艺术家列表（分页）

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| method_name | string | ✅ | `artist_list` |
| page | int | ✅ | 页码 |

响应字段同 `recent_artist_list`，额外含 `total_records`。

**请求示例**：

```json
{
  "method_name": "artist_list",
  "page": 1,
  "package_name": "com.example.musicapp",
  "salt": "abc123",
  "sign": "md5('viaviwebabc123')"
}
```

---

### artist_album_list — 获取艺术家的专辑列表（分页）

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| method_name | string | ✅ | `artist_album_list` |
| artist_id | int | ✅ | 艺术家 ID |
| page | int | ✅ | 页码 |

**响应字段**：

| 字段 | 说明 |
|------|------|
| total_records | 总记录数 |
| aid | 专辑 ID |
| artist_ids | 艺术家 ID 列表 |
| album_name | 专辑名称 |
| album_image | 专辑大图 |
| album_image_thumb | 专辑缩略图 |

**请求示例**：

```json
{
  "method_name": "artist_album_list",
  "artist_id": 1,
  "page": 1,
  "package_name": "com.example.musicapp",
  "salt": "abc123",
  "sign": "md5('viaviwebabc123')"
}
```

---

### artist_name_songs — 按艺术家名称获取歌曲（分页）

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| method_name | string | ✅ | `artist_name_songs` |
| artist_name | string | ✅ | 艺术家名称 |
| page | int | ✅ | 页码 |
| user_id | int | ❌ | 用户 ID |

响应字段同 `all_songs`。

**请求示例**：

```json
{
  "method_name": "artist_name_songs",
  "artist_name": "周杰伦",
  "page": 1,
  "user_id": 5,
  "package_name": "com.example.musicapp",
  "salt": "abc123",
  "sign": "md5('viaviwebabc123')"
}
```

---

## 专辑

### album_list — 获取专辑列表（分页）

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| method_name | string | ✅ | `album_list` |
| page | int | ✅ | 页码 |

**响应字段**：

| 字段 | 说明 |
|------|------|
| total_records | 总记录数 |
| aid | 专辑 ID |
| album_name | 专辑名称 |
| album_image | 专辑大图 |
| album_image_thumb | 专辑缩略图 |

**请求示例**：

```json
{
  "method_name": "album_list",
  "page": 1,
  "package_name": "com.example.musicapp",
  "salt": "abc123",
  "sign": "md5('viaviwebabc123')"
}
```

---

### album_songs — 按专辑获取歌曲（分页）

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| method_name | string | ✅ | `album_songs` |
| album_id | int | ✅ | 专辑 ID |
| page | int | ✅ | 页码 |
| user_id | int | ❌ | 用户 ID |

响应字段同 `all_songs`，额外含专辑信息（`aid`、`album_name`、`album_image`、`album_image_thumb`）。

**请求示例**：

```json
{
  "method_name": "album_songs",
  "album_id": 2,
  "page": 1,
  "user_id": 5,
  "package_name": "com.example.musicapp",
  "salt": "abc123",
  "sign": "md5('viaviwebabc123')"
}
```

---

## 播放列表

### playlist — 获取播放列表（分页）

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| method_name | string | ✅ | `playlist` |
| page | int | ✅ | 页码 |

**响应字段**：

| 字段 | 说明 |
|------|------|
| total_records | 总记录数 |
| pid | 播放列表 ID |
| playlist_name | 播放列表名称 |
| playlist_image | 播放列表大图 |
| playlist_image_thumb | 播放列表缩略图 |

**请求示例**：

```json
{
  "method_name": "playlist",
  "page": 1,
  "package_name": "com.example.musicapp",
  "salt": "abc123",
  "sign": "md5('viaviwebabc123')"
}
```

---

### playlist_songs — 按播放列表获取歌曲

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| method_name | string | ✅ | `playlist_songs` |
| playlist_id | int | ✅ | 播放列表 ID |
| page | int | ✅ | 页码 |
| user_id | int | ❌ | 用户 ID |

**响应结构**：包含播放列表信息及 `songs_list` 歌曲数组。

**请求示例**：

```json
{
  "method_name": "playlist_songs",
  "playlist_id": 1,
  "page": 1,
  "user_id": 5,
  "package_name": "com.example.musicapp",
  "salt": "abc123",
  "sign": "md5('viaviwebabc123')"
}
```

**响应示例**：

```json
{
  "ONLINE_MP3": [
    {
      "pid": "1",
      "playlist_name": "经典老歌",
      "playlist_image": "http://your-domain.com/images/playlist1.jpg",
      "playlist_image_thumb": "http://your-domain.com/images/thumbs/playlist1.jpg",
      "songs_list": [
        { "total_records": "10", "id": "5", "mp3_title": "童年", ... }
      ]
    }
  ]
}
```

---

## 横幅（Banner）

### banners — 获取所有横幅

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| method_name | string | ✅ | `banners` |

**响应字段**：

| 字段 | 说明 |
|------|------|
| bid | 横幅 ID |
| banner_title | 横幅标题 |
| banner_sort_info | 横幅简介 |
| banner_link | 横幅链接 |
| banner_image | 横幅大图 |
| banner_image_thumb | 横幅缩略图 |

**请求示例**：

```json
{
  "method_name": "banners",
  "package_name": "com.example.musicapp",
  "salt": "abc123",
  "sign": "md5('viaviwebabc123')"
}
```

---

### banner_songs — 按横幅获取歌曲（分页）

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| method_name | string | ✅ | `banner_songs` |
| banner_id | int | ✅ | 横幅 ID |
| page | int | ✅ | 页码 |
| user_id | int | ❌ | 用户 ID |

响应字段同 `all_songs`，额外含 `album_id`、`link`。

**请求示例**：

```json
{
  "method_name": "banner_songs",
  "banner_id": 1,
  "page": 1,
  "user_id": 5,
  "package_name": "com.example.musicapp",
  "salt": "abc123",
  "sign": "md5('viaviwebabc123')"
}
```

---

## 用户

### user_register — 用户注册

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| method_name | string | ✅ | `user_register` |
| type | string | ✅ | 注册类型：`Normal` / `Google` / `Facebook` |
| name | string | ✅ | 用户名 |
| email | string | ✅ | 邮箱 |
| password | string | ❌ | 密码（Normal 类型必填） |
| phone | string | ❌ | 手机号 |
| auth_id | string | ❌ | 第三方授权 ID（Google / Facebook 类型必填） |

**响应**（成功）：

```json
{
  "ONLINE_MP3": [{ "user_id": "1", "name": "...", "email": "...", "success": "1", "msg": "", "auth_id": "..." }]
}
```

> 注册成功后会发送欢迎邮件。

**请求示例**（普通注册）：

```json
{
  "method_name": "user_register",
  "type": "Normal",
  "name": "张三",
  "email": "zhangsan@example.com",
  "password": "123456",
  "phone": "13800138000",
  "auth_id": "",
  "package_name": "com.example.musicapp",
  "salt": "abc123",
  "sign": "md5('viaviwebabc123')"
}
```

**请求示例**（Google 注册）：

```json
{
  "method_name": "user_register",
  "type": "Google",
  "name": "张三",
  "email": "zhangsan@gmail.com",
  "auth_id": "google_uid_xxxx",
  "package_name": "com.example.musicapp",
  "salt": "abc123",
  "sign": "md5('viaviwebabc123')"
}
```

---

### user_login — 用户登录

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| method_name | string | ✅ | `user_login` |
| type | string | ✅ | 登录类型：`Normal` / `Google` / `Facebook` |
| email | string | ✅ | 邮箱 |
| password | string | ❌ | 密码（Normal 类型必填） |
| auth_id | string | ❌ | 第三方授权 ID（Google / Facebook 类型必填） |

**响应**（成功）：

```json
{
  "ONLINE_MP3": [{ "user_id": 1, "name": "...", "email": "...", "msg": "...", "auth_id": "...", "success": "1" }]
}
```

**请求示例**（普通登录）：

```json
{
  "method_name": "user_login",
  "type": "Normal",
  "email": "zhangsan@example.com",
  "password": "123456",
  "auth_id": "",
  "package_name": "com.example.musicapp",
  "salt": "abc123",
  "sign": "md5('viaviwebabc123')"
}
```

**请求示例**（Google 登录）：

```json
{
  "method_name": "user_login",
  "type": "Google",
  "email": "zhangsan@gmail.com",
  "auth_id": "google_uid_xxxx",
  "package_name": "com.example.musicapp",
  "salt": "abc123",
  "sign": "md5('viaviwebabc123')"
}
```

**响应示例**（成功）：

```json
{
  "ONLINE_MP3": [{ "user_id": 1, "name": "张三", "email": "zhangsan@example.com", "msg": "登录成功", "auth_id": "", "success": "1" }]
}
```

**响应示例**（密码错误）：

```json
{
  "ONLINE_MP3": [{ "msg": "密码不正确", "success": "0" }]
}
```

---

### user_profile — 获取用户信息

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| method_name | string | ✅ | `user_profile` |
| user_id | int | ✅ | 用户 ID |

**响应字段**：

| 字段 | 说明 |
|------|------|
| user_id | 用户 ID |
| name | 用户名 |
| email | 邮箱 |
| phone | 手机号 |
| success | `1` |

**请求示例**：

```json
{
  "method_name": "user_profile",
  "user_id": 5,
  "package_name": "com.example.musicapp",
  "salt": "abc123",
  "sign": "md5('viaviwebabc123')"
}
```

---

### user_profile_update — 更新用户信息

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| method_name | string | ✅ | `user_profile_update` |
| user_id | int | ✅ | 用户 ID |
| name | string | ✅ | 用户名 |
| email | string | ✅ | 邮箱 |
| phone | string | ❌ | 手机号 |
| password | string | ❌ | 新密码（传空则不修改） |

**请求示例**：

```json
{
  "method_name": "user_profile_update",
  "user_id": 5,
  "name": "张三",
  "email": "zhangsan@example.com",
  "phone": "13900139000",
  "password": "",
  "package_name": "com.example.musicapp",
  "salt": "abc123",
  "sign": "md5('viaviwebabc123')"
}
```

**响应**：`{ "msg": "...", "success": "1" }`

---

### forgot_pass — 忘记密码

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| method_name | string | ✅ | `forgot_pass` |
| user_email | string | ✅ | 注册邮箱 |

> 系统会生成新随机密码并通过邮件发送，同时更新数据库密码。

**请求示例**：

```json
{
  "method_name": "forgot_pass",
  "user_email": "zhangsan@example.com",
  "package_name": "com.example.musicapp",
  "salt": "abc123",
  "sign": "md5('viaviwebabc123')"
}
```

**响应**：`{ "msg": "...", "success": "1" }`

---

## 收藏

### favourite_post — 添加 / 取消收藏

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| method_name | string | ✅ | `favourite_post` |
| post_id | int | ✅ | 内容 ID（歌曲 ID） |
| user_id | int | ✅ | 用户 ID |
| type | string | ✅ | 类型：`song` |

> 若已收藏则取消，若未收藏则添加（切换逻辑）。

**请求示例**：

```json
{
  "method_name": "favourite_post",
  "post_id": 42,
  "user_id": 5,
  "type": "song",
  "package_name": "com.example.musicapp",
  "salt": "abc123",
  "sign": "md5('viaviwebabc123')"
}
```

**响应**（收藏成功）：`{ "msg": "已加入收藏", "success": "1" }`  
**响应**（取消成功）：`{ "msg": "已取消收藏", "success": "0" }`

---

### get_favourite_post — 获取收藏列表（分页）

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| method_name | string | ✅ | `get_favourite_post` |
| user_id | int | ✅ | 用户 ID |
| type | string | ✅ | 类型：`song` |
| page | int | ✅ | 页码 |

响应字段同 `all_songs`，`total_songs` 为总收藏数。

**请求示例**：

```json
{
  "method_name": "get_favourite_post",
  "user_id": 5,
  "type": "song",
  "page": 1,
  "package_name": "com.example.musicapp",
  "salt": "abc123",
  "sign": "md5('viaviwebabc123')"
}
```

---

## 应用信息

### app_details — 获取应用设置

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| method_name | string | ✅ | `app_details` |

**响应字段**：

| 字段 | 说明 |
|------|------|
| app_name | 应用名称 |
| app_logo | 应用 Logo |
| app_version | 应用版本 |
| app_author | 作者 |
| app_contact | 联系方式 |
| app_email | 邮箱 |
| app_website | 网站 |
| app_download_url | 下载链接 |
| app_description | 应用描述 |
| app_developed_by | 开发者 |
| app_privacy_policy | 隐私政策 |
| package_name | 包名 |
| publisher_id | 广告发布商 ID |
| interstital_ad | 插屏广告开关 |
| interstital_ad_type | 插屏广告类型（admob / facebook） |
| interstital_ad_id | 插屏广告 ID |
| interstital_ad_click | 触发广告的点击次数 |

**请求示例**：

```json
{
  "method_name": "app_details",
  "package_name": "com.example.musicapp",
  "salt": "abc123",
  "sign": "md5('viaviwebabc123')"
}
```

---

## 接口汇总表

| 接口名称 | method_name | 说明 |
|---------|------------|------|
| 首页数据 | `home` | 横幅、最新专辑、艺术家、热门歌曲 |
| 首页数据（轻量） | `home_new` | 同上，横幅不含歌曲详情 |
| 全部歌曲 | `all_songs` | 分页获取所有歌曲 |
| 最新歌曲 | `latest` | 分页获取最新歌曲 |
| 横幅列表 | `banners` | 获取所有横幅 |
| 横幅歌曲 | `banner_songs` | 按横幅 ID 获取歌曲 |
| 分类列表 | `cat_list` | 分页获取分类 |
| 分类歌曲 | `cat_songs` | 按分类 ID 获取歌曲 |
| 最新艺术家 | `recent_artist_list` | 最新 10 位艺术家 |
| 艺术家列表 | `artist_list` | 分页获取艺术家 |
| 艺术家专辑 | `artist_album_list` | 按艺术家 ID 获取专辑 |
| 艺术家歌曲 | `artist_name_songs` | 按艺术家名称获取歌曲 |
| 专辑列表 | `album_list` | 分页获取专辑 |
| 专辑歌曲 | `album_songs` | 按专辑 ID 获取歌曲 |
| 播放列表 | `playlist` | 分页获取播放列表 |
| 播放列表歌曲 | `playlist_songs` | 按播放列表 ID 获取歌曲 |
| 歌曲详情 | `song_info` | 获取歌曲详情（+播放计数） |
| 单首歌曲 | `single_song` | 获取歌曲详情（+播放计数） |
| 下载计数 | `song_download` | 记录歌曲下载次数 |
| 综合搜索 | `song_search` | 搜索歌曲/艺术家/专辑 |
| 歌曲评分 | `song_rating` | 对歌曲打分（1-5 星） |
| 举报歌曲 | `song_report` | 举报歌曲 |
| 建议歌曲 | `song_suggest` | 用户推荐歌曲 |
| 最近播放 | `get_recent_songs` | 按 ID 列表获取歌曲 |
| 用户注册 | `user_register` | 普通/Google/Facebook 注册 |
| 用户登录 | `user_login` | 普通/Google/Facebook 登录 |
| 用户信息 | `user_profile` | 获取用户基本信息 |
| 更新用户信息 | `user_profile_update` | 修改用户名/邮箱/密码/手机 |
| 忘记密码 | `forgot_pass` | 通过邮件重置密码 |
| 添加/取消收藏 | `favourite_post` | 收藏或取消收藏歌曲 |
| 收藏列表 | `get_favourite_post` | 获取用户收藏的歌曲 |
| 应用信息 | `app_details` | 获取应用配置和广告设置 |
