# 媒体文件管理

> **何时阅读**：当你要修改文件上传逻辑、缩略图生成、LRC 歌词存储结构，或使用 Web 文件管理器时，阅读此文件。

## 功能概述

后端负责三类媒体文件的存储和处理：音频文件（`uploads/`）、封面图和横幅图（`images/`）、LRC 歌词文件（`lrc/`）。封面图上传时自动生成缩略图（存于 `images/thumbs/`）。Web 文件管理器（RichFileManager）集成在 `filemanager/` 目录，供管理员通过浏览器管理服务器文件。

## 关键文件

| 文件 | 职责 |
|------|------|
| `uploadscript.php` | 文件上传处理（表单提交，处理 MP3、图片上传） |
| `thumbnail_images.class.php` | 缩略图生成类（GD Library），上传封面时调用 |
| `images/` | 原始封面图、轮播横幅图存放目录 |
| `images/thumbs/` | 自动生成的缩略图（与原图同名） |
| `uploads/` | MP3 音频文件存放目录（`mp3_type='local'` 的歌曲） |
| `lrc/` | LRC 歌词文件存放目录 |
| `lrc/thumbs/` | LRC 关联的封面缩略图 |
| `filemanager/` | RichFileManager Web 文件管理器（PHP） |
| `filemanager/config/` | 文件管理器配置（允许的文件类型、根目录等） |

## 文件 URL 构造

`includes/function.php` 中的 `getBaseUrl()` 函数根据当前请求动态生成资源 URL 前缀（如 `http://your-domain.com/`）。API 响应中的所有文件 URL 均通过 `$file_path . 'images/' . $filename` 拼接：

```
原图：{BASE_URL}/images/{filename}
缩略图：{BASE_URL}/images/thumbs/{filename}
音频：{BASE_URL}/uploads/{filename}
```

## 上传流程（歌曲封面）

1. 管理员在 `add_mp3.php` / `add_artist.php` 等表单提交文件
2. `uploadscript.php` 接收 `$_FILES`，验证类型和大小
3. 保存原图到 `images/`（文件名加时间戳前缀防重名）
4. 调用 `thumbnail_images.class.php` 生成缩略图，保存到 `images/thumbs/`
5. 将文件名（不含路径）存入对应数据库字段

## LRC 歌词

- 歌词可内嵌存储（`tbl_mp3.mp3_lrc_txt` 字段，直接存 LRC 文本）
- 也可存为 `.lrc` 文件到 `lrc/` 目录，然后在 `mp3_lrc_url` 字段存储访问 URL
- API 响应同时返回两个字段，Android 端优先使用 `mp3_lrc_txt`（有内容时），否则通过 `mp3_lrc_url` 加载外部文件

## Web 文件管理器（filemanager/）

RichFileManager 是一个 PHP 实现的服务端文件管理器，集成在后台管理面板中（通过 iframe 嵌入），CKEditor 富文本中的"浏览服务器"功能也调用此管理器。

- 入口：`filemanager/index.php`
- 文件操作后端：`filemanager/ajax_calls.php`
- 上传处理：`filemanager/upload.php`（基于 `UploadHandler.php`）
- 配置文件路径（允许的扩展名、根目录等）：`filemanager/config/`

## 注意事项

- `uploads/` 目录需要 Apache `www-data` 用户的写权限（Docker 中由 `Dockerfile` 的 `chown -R www-data:www-data /var/www/html` 处理）
- 本地存储不支持横向扩展（多容器实例），如需扩展应考虑挂载共享卷或迁移到对象存储
- 文件名冲突：`uploadscript.php` 用时间戳前缀（如 `12345_original.jpg`）规避，但并发高时仍有风险
- MP3 外链歌曲（`mp3_type='youtube'` 或 `'external'`）不占用服务器存储，`mp3_url` 直接为外部 URL
