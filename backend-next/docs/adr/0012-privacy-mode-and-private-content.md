# 隐私模式与隐私内容可见性

配给歌曲、专辑、分类的 `is_private` 布尔标记 + settings 单行的 `privacyMode` 全局开关，实现对 App 侧内容可见性的条件控制。**关闭时全量可见，打开时只暴露三者的标记均属非隐私的条目**（级联语义见「归属链」）。

## 决策要点

### 1. 服务端单一过滤点，客户端零参与

隐私过滤**仅**在遗留协议门面（`/legacy` 路由）执行，Android 端零改动。响应契约不变（不加 `is_private` 字段，App 不感知）。

**边界已知且被接受**：本机留存（已下载歌曲、播放队列快照、响应快照、音频缓存）不受隐私模式影响——服务端无法回收已下发的字节。CDN 媒体直链不经后端，隐私模式无法阻止知悉 URL 的播放。这两项限制与下架歌曲（`status=false`）的现有处境一致。

**考虑过的备选**：App 端拉开关、本地过滤下载/队列/快照——需协议变更（新增下发字段）、门面策略评审、App 三处展示改造，且离线时仅可用陈旧状态。权衡后否决，在此文档记录边界。

### 2. 归属链级联

隐私标记的可见性条件沿归属链全链计算，与 `status`（下架）同构（ADR 0009）：歌曲可见要求自身、所属专辑、所属分类均非隐私；专辑可见要求自身、所属分类均非隐私。标记独立（不自动传染子项），由管理员在后台按需标点。

**与 status 的关系**：正交。下架（`status=false`）= 恒定不可见；隐私（`is_private=true`）= 条件不可见，仅在隐私模式打开时生效。组合产生三层语义：`status=true + is_private=false` 始终可见，`status=true + is_private=true` 模式开时隐藏，`status=false` 不论开关始终隐藏。

### 3. 新上传默认隐私，存量迁移置非隐私

新建/上传的内容 `is_private` 默认 `true`（管理员需主动取消隐私才能使其在模式开时可见），存量（迁移时）全部置 `false`（维持现状行为），模式开关默认关闭无任何行为变化。

反转的理由：隐私场景的常见需求是"管理员每次新增内容后按需公开"，而非"手动标记每一条要隐藏的内容"。

## 模型变更

- `Setting.privacyMode`: `VarChar(10)`，存 `"true"/"false"`，遵循既有布尔值存储约定。默认 `"false"`。
- `Song.isPrivate` / `Album.isPrivate` / `Category.isPrivate`: `Boolean`，默认 `false`（迁移），新上传由服务端写入 `true`。
- 除 Content Entity 外，Book 不加入隐私字段——书籍分类（`Category`）被标隐私时整类入口消失，书籍本体不单独管理。

## 生效范围

- **受控**：遗留协议门面内所有只读内容 endpoint（home/home_new、cat_list、cat_albums、cat_books、album_list、album_songs、album_list、latest、all_songs、song_search、song_info/single_song、artist_name_songs、get_recent_songs、get_favourite_post、banner_songs、playlist_songs、song_download、app_details、trending 等）。被过滤实体视同不存在（列表少条目、详情返回空、不报错）。
- **不受控**：写入端（record_song_access、song_rating、favourite_post 等，写入不拦）、管理端 `/admin` 全部端点（管理面板始终全量可见）、CDN 媒体文件。
- **顺带修正**：`get_recent_songs` 与 `get_favourite_post` 既往遗漏 `album.status` 过滤，统一接新过滤钩子时纳入。

## 管理面板

- settings 页新增 tab「隐私模式」，以 `el-switch` 控制 `privacyMode`（存 `"true"/"false"`），PUT `/admin/settings` 白名单加 `privacyMode` 键。
- 三张管理列表（分类/专辑/歌曲）各新增 `is_private` 列（`el-tag` 只读展示）；编辑弹窗内新增 `el-switch` 开关（与 status 表单模式一致，formBody 发送 `is_private: 0/1`）。歌曲批量 PATCH 扩展 `isPrivate` 字段。
- 管理员台上传/新建时 `is_private` 默认 `true`（代码硬默认），表单中允许主动取消。

## Consequences

- 隐私模式开关至多影响 App 侧的接口响应——不能防止本机已下载/已缓存内容的展示与播放，也不能阻止知悉 CDN URL 的人获取媒体文件。此边界以文档明示。
- 存量内容首次迁移后全部非隐私，管理员需逐个标记需隐藏的内容（或利用级联只标分类/专辑）。新上传自动隐私，管理员若需公开需主动取消标记。
- 不做客户端配合，故不存在「搜索展示隐私内容但点击播放失败」的新问题线路——已隐藏条目在服务端就不返回，App 也无从展示。
- 顺带修正的 album.status 过滤会从最近播放和收藏中排除专辑已下架的歌曲，是行为变化（缩小可见集合，正向修正）。