# 收藏存本机而非后端收藏接口

播放器功能图标行的爱心接通为收藏切换时，App 尚无登录系统，所有接口 `user_id` 恒为 0。后端 `favourite_post` / `get_favourite_post` 以 `user_id` 隔离收藏：`user_id=0` 会落入所有游客共享的同一收藏池（多设备互相污染），且列表回填的 `is_favourite` 对 `user_id=0` 恒为 false、播放器选中态仍需本地另存。故收藏改为纯本机实现：收藏 ID 列表持久化于 SharedPreferences（仿最近播放，不设上限、不自动驱逐），收藏页经 `get_recent_songs` 按 ID 换详情（后端每页固定 10 条，按页数并行取全部页后按收藏时间倒序重排），后端收藏接口保留不用。

## Considered Options

- **`favourite_post` + user_id=0**：数据入库但所有游客共享 user_id=0 收藏池互相污染；`is_favourite` 回填失效，播放器状态仍需本地另存，两头做。否决。
- **本次顺带做登录系统**：登录、注册、token、UI 一整套，与"接通爱心"不是一个量级。否决。
- **本地 ID 列表 + `get_recent_songs` 换详情（采纳）**：未登录可用、无共享池问题、与最近播放同构；未来切换后端的改动面收敛在数据层。

## Consequences

- 收藏不跨设备同步；卸载 App 即丢失（已接受的未登录形态限制）。
- 已收藏歌曲被后台下架后从收藏页消失但本地 ID 保留，重新上架自动回来。
- 登录系统上线后需迁移：本地收藏 ID 批量写入后端 favourite 表（或逐条调 `favourite_post`），播放器与收藏页切回后端数据源，本 ADR 届时标记 superseded。
