# Context Map

QQT Music（倾轻听）是一个在线音乐流媒体与书籍阅读产品，由两个限界上下文组成：后端负责内容的管理与分发，Android 端负责用户的播放体验。两者通过一个自研的签名协议在唯一的 `api.php` 入口上交互（协议本身见 [ADR 0001](docs/adr/0001-sign-salt-api-protocol.md)）。

## Contexts

- [Backend](./backend/CONTEXT.md)：音乐内容的管理（歌曲、分类、艺术家、专辑、播放列表、横幅）与对 App 的数据分发，含一套管理员 Web 面板（**过渡期冻结，割接后下线**）
- [Backend-Next](./backend-next/CONTEXT.md)：以 Node.js + TypeScript 重写的下一代后端（见 [ADR 0003](./docs/adr/0003-nodejs-rewrite-with-legacy-facade.md)），最终替换 Backend
- [Android](./android/CONTEXT.md)：客户端，负责音乐播放（浏览、搜索、播放、收藏、下载、后台保活）与书籍阅读

## Relationships

- **Android → Backend（Conformist）**：Android 完全遵循后端定义的数据契约——所有请求经 `base64(urlencode(json))` 编码后 POST 到 `api.php`，所有响应包裹在 `ONLINE_MP3` 根节点下，数值字段一律为字符串。后端是事实上的上游，Android 端不做任何契约改造，只做适配（如 `BooleanAdapter` 处理 `"0"`/`"1"` → Boolean）。
- **Backend-Next → Android（Conformist，反向）**：割接时由新后端单方面复刻旧契约（`/legacy` 遗留协议门面），Android 端零改动。语义兼容而非逐字节兼容，已实锤的旧缺陷不复刻。
- **签名契约（Shared Kernel）**：`package_name` + `salt` + `sign = md5("viaviweb" + salt)` 三元组是双方共同遵守的鉴权约定，分别硬编码在 `AppConfig.kt`（Android）和 `tbl_settings`（Backend），任一侧改动都会导致全量请求失败。
- **媒体文件（Open Host）**：后端以 HTTP 静态文件形式暴露 `uploads/`（音频）、`images/`（封面）、`lrc/`（歌词）三个目录，Android 端直接按 URL 流式播放/加载，无二次封装。Backend-Next 沿用同样的路径形态，并以一次性复制继承媒体文件。
- **Backend → Backend-Next（继承，一次性）**：割接时迁移脚本继承内容资产（歌曲/分类/艺术家/专辑/播放列表/横幅/全局设置/媒体文件，保留原实体 ID）；行为数据（用户、收藏、评论、举报、建议）不继承，从零积累。

## 共享词汇的注意事项

- 「播放列表」只在 Backend 上下文中指管理员策展的歌曲合集（`tbl_playlist`）；Android 端当前播放的歌曲序列叫**播放队列（Queue）**，不要混用。
- 「热门歌曲」在 Backend 中指按播放量自动排序的集合；在 Backend-Next 中已被**热门榜**（管理员手工固定）取代，两个上下文并存期间注意区分。
- 「收藏」指用户标记喜欢的歌曲（`tbl_favourite`），与「已下载」（本地文件快照）是两回事。
