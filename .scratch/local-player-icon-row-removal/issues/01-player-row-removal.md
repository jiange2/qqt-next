# 01: 本地歌曲功能图标行整行移除 + 播放器编辑接线拆除

**What to build:** PlayerScreen 功能图标行整行以「非本地歌曲」为渲染条件（本地居中分支与行内 isLocalSong 守卫一并收敛）；更多菜单删「编辑信息」项、更多图标仅在线分支出现、评分项本地兜底守卫删除（可扩展列表结构保留，ADR 0007）；拆除 editingSong 状态、页内 LocalMetadataEditSheet 渲染、localMusicViewModel 参数与 localSongs/localSong 快照查回；AppNavigation 调用点去传参并更新注释（LocalMusicViewModel 仍提升在导航层）；删除全仓无引用的 PlayerIcons.Edit；fixedContentHeight 与间距常量不动；更新页头注释与相关旧注释。定时行为与角标机制零改动，列表长按编辑入口零改动。

**Blocked by:** 无。

**Status:** open

- [ ] 本地歌曲播放页：标题区与进度条之间无任何图标（播放设置 / 更多均不出现）
- [ ] 在线歌曲播放页：五图标行（喜欢 / 播放设置 / 下载 / 加入歌单 / 更多）及全部行为原样
- [ ] 本地 ↔ 在线切歌：图标行随之消失 / 恢复，无残留态
- [ ] 本地播放期间无法打开播放设置浮层与更多菜单；在线歌曲评分（含对话框、已评分兜底）原样
- [ ] 在线设定时 → 切本地：到点照常暂停；本地页无角标、不可取消；切回在线角标恢复、可取消
- [ ] 本地音乐页歌曲视图 / 本地专辑页长按编辑信息原样；保存后在播本地歌的标题 / 歌手 / 封面即时刷新
- [ ] 正常屏本地播放页整体不可拖动（无微滚动）；矮屏滚动托底、主控行钉底不变
- [ ] 本地歌曲无歌词页、不发 song_info 请求；缓存染色（整槽浅金）、主控行、队列浮层、播放模式照旧
- [ ] 在播本地歌的媒体库行已消失时播放页无异常
- [ ] PlayerScreen 不再接收 localMusicViewModel；PlayerIcons.Edit 已删除；页头与相关旧注释更新

## Comments

- 2026-11-14 立票：grilling 四决策全部确认（见 spec.md Further Notes），spec 发布即立本票。勾选项为设备级验收清单，待人工编译后真机逐项确认（本仓规约：AI 不编译、不跑 gradlew）。