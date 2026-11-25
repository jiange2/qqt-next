# 01: 本地歌曲功能图标行整行移除 + 播放器编辑接线拆除

**What to build:** PlayerScreen 功能图标行整行以「非本地歌曲」为渲染条件（本地居中分支与行内 isLocalSong 守卫一并收敛）；更多菜单删「编辑信息」项、更多图标仅在线分支出现、评分项本地兜底守卫删除（可扩展列表结构保留，ADR 0007）；拆除 editingSong 状态、页内 LocalMetadataEditSheet 渲染、localMusicViewModel 参数与 localSongs/localSong 快照查回；AppNavigation 调用点去传参并更新注释（LocalMusicViewModel 仍提升在导航层）；删除全仓无引用的 PlayerIcons.Edit；fixedContentHeight 与间距常量不动；更新页头注释与相关旧注释。定时行为与角标机制零改动，列表长按编辑入口零改动。

**Blocked by:** 无。

**Status:** resolved

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
- 2026-11-14 完成。实现提交 53c5669（合并 22a33c4），7 文件 +51/−115：PlayerScreen.kt（整行 if (!isLocalSong) 条件化、本地居中分支与行内守卫收敛、更多菜单删编辑项与评分兜底守卫、拆 editingSong/页内面板/localMusicViewModel 参数/localSongs/localSong 快照、删 3 个 unused import、页头与行内注释更新）、AppNavigation.kt（去传参+445/162 注释）、PlayerIcons.kt（删 Edit，全仓代码引用核实归零）、LocalMetadataEditSheet.kt:49 与 LocalMusicScreen/LocalSongRow/LocalAlbumScreen 三处 KDoc 仅注释同步（编排层裁定：spec「列表长按零改动」指行为，过时播放器从句随拆除更新）。在线五图标行/定时角标/布局常量/保存后刷新链路零改动。无新测试（spec Testing Decisions：纯 UI 移除无接缝）；未编译未跑 gradlew（仓规约），勾选项待人工编译后真机逐项确认。Status 留待评审后归档。
- 2026-11-14 双轴评审修复。提交 4e5c38a（合并 13a24ff）：FIX1 浮层随切歌收敛——PlayerScreen 加 LaunchedEffect(isLocalSong)，进入本地播放即关闭已打开的更多菜单/播放设置浮层（评分对话框随浮层离开组合自然重置；local→online 不重开、online→online 不触发、在线行为零改动），修复评审发现「在线开浮层→自动切本地歌→评分可点/定时可取消」残留（spec story 4/8）；不恢复评分项守卫（spec 删除决策保留）、PlayerSettingsManager/定时/角标零改动。FIX2 ui.md 本地差异节第 3 条去重复开头从句（Standards 轴判断 call）。FIX3 spec「更多菜单收敛」节补浮层收敛口径。评审其余结论：无硬违规、无缺失、无 scope creep。未编译（仓规约），勾选项待人工真机。
- 2026-11-14 归档：Status open → resolved。评审修复已合入集成分支（4e5c38a，合并 13a24ff）：FIX1 浮层随切歌收敛（LaunchedEffect(isLocalSong) 进入本地播放即收起更多/播放设置浮层，堵「在线开浮层→自动切本地歌」评分可点/定时可取消残留）、FIX2 ui.md 本地差异节去冗余、FIX3 spec 补浮层收敛口径。集成分支 feat/local-player-icon-row-removal。评审结论：Standards 0 硬违规、Spec 0 缺失 / 0 scope creep。无新测试（纯 UI 移除无接缝）；未编译（仓规约）。勾选项保持未勾：设备级验收清单待人工编译后真机逐项确认（图标行消失/恢复、定时不可见仍生效、长按编辑保存即时刷新、正常屏无微滚动/矮屏托底、媒体库行消失边界等）。