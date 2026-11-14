# 01: 批量覆盖规则与一次提交链路

**What to build:** `LocalMetadataEdit.kt` 新纯函数：输入专辑 / 专辑艺术家两个原始字符串，按「填了的字段才改」为单首歌生成新覆盖——trim 后留空 → 保留原覆盖该字段；非空且与文件标签值相同 → 该字段覆盖置 null（沿用 `overrideIfCustom` 口径）；其余 → 存裁剪值；标题 / 艺术家 / 音轨号 / 年份四字段原样保留；生成结果 `isEmpty` → 删键（沿用单首「空壳不落盘」口径）。`LocalMusicViewModel` 新批量保存入口（如 `saveBatchMetadata(songs, albumInput, albumArtistInput)`）：一次 `editLock.withLock` 内整表更新覆盖 Map → 替换 `_songs` 选中条目（`available` 取快照本值，沿用 `applyOverride` 范式）→ `sortedByDisplayTitle` → `LocalMusicStore.saveOverlay` 一次原子落盘 → `notice`「已修改 n 首」（n = 提交数）。快照不动、不重查、不碰文件、不做播放器同步（播放器只显示标题 + 艺术家）。不走 N 次 `applyOverride`。JVM 行为测试覆盖纯规则与提交后列表 / 覆盖表形态（已写待人工执行，本仓规约：AI 不编译、不跑 gradlew）。

**Blocked by:** None (can start immediately).

**Status:** open

- [ ] 勾 N 首只填专辑 → N 首专辑变、专辑艺术家保持原展示值
- [ ] 只填专辑艺术家 → 专辑保持原展示值
- [ ] 两字段都填 → 选中歌并进同一张专辑（归并键两字段一致）
- [ ] 只填专辑名、选中歌艺术家不同 → 拆成多张同名不同艺术家的专辑（与单首编辑一致）
- [ ] 填值与文件标签相同 → 该字段覆盖清除（显示值不变、不造假覆盖）
- [ ] 已有标题 / 音轨号等覆盖的歌批量改专辑 → 其他字段覆盖保留
- [ ] 保存后覆盖层文件一次写入、重启后改动仍在
- [ ] 重扫后批量改动仍生效（覆盖层按行 ID 合并）
- [ ] 批量保存与单首编辑并发不丢改动（editLock 串行）
- [ ] Toast「已修改 n 首」经导航层 notice collector 弹出
- [ ] 飞行模式可完成全流程（纯本地、零网络）

## Comments