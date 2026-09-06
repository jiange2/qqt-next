# 访问事实统计采用条数口径：时长客户端写回、命中判定走缓存元数据、身份用 song_id

从 audio-player-demo 移植 mp3 access 播放统计时，对统计口径做了四处重定义。原封照搬会把工期花在 qqt 没有消费者的层上（Web 专属概念、定时聚合基建），且 demo 的字节口径本身站不住：**装载即计整曲字节**——听 3 秒切歌也按整曲字节算流量节省，而是否真实听完服务端永远不可知。

## 决策

1. **无字节维度，命中率条数口径**：songs 表不存 fileSize，事实无 data_size，命中率 = 命中事实条数 ÷ 全部事实条数（每条一票）。命中 = 该次装载未产生媒体网络流量。真实流量数字以 CDN 日志为准，后端不假装知道字节数。
2. **命中判定走缓存元数据（C4）**：`isCached(key, 0, fileSize)` 的 fileSize 入参消失后，改读 SimpleCache 元数据的 content length——CacheDataSource 打开整文件响应时自动写入该 key 元数据，首次播放即有；length 已知且 `isCached(key, 0, length)` 全覆盖即命中。对存量整曲缓存**上线即生效**，零回填。
3. **时长由客户端写回**：songs.duration 由 App 播放就绪后取得真实时长，与列表接口下发的服务器现值比对，不一致才经 `update_song_duration` 上报写回（fire-and-forget）。服务端不做音频元数据解析，不依赖旧库回填。
4. **事实身份 = song_id**：播放 URL 随公网 Base 与重传漂移不落库（demo 用 mp3_path 身份、靠清洗文件名 SQL 反查歌曲的教训）。

附带口径：已下载歌曲的本地播放（file://）同样上报并**计为命中**——「命中率」由此获得统一语义"未产生媒体流量的装载占比"；离线播放因无网不可上报，缺失属口径内，不做本地补报缓存。

**通道**：legacy 门面新增纯增量 method `record_song_access`（单条事实，走签名协议）与 `update_song_duration`；新表 `access_facts`（存储快照 allocated/used 长事实上，无独立快照表）与 `online_samples`（进程内 5 分钟采样，重启漏采不补）。不做 CacheStat T+1 聚合表（后台 SQL 即时聚合）、不做 housekeep（明细查询层 LIMIT 兜底）。

## Considered Options

- **fileSize 落库 + 服务端解析下发（demo 同款）**：被拒——服务端引元数据解析依赖，且字节口径不反映真实听播。
- **fileSize 客户端自举（首播写回 size）**：被拒——每首歌首播事实因 size 未知而丢弃，早期数据永远缺。
- **C1 听完标记法**：被拒——标记零起点积累，存量整曲缓存要等重听一遍才被看见，上线初期大面积假 miss。
- **C3 存在即命中**：被拒——"命中率"虚化为"有部分缓存的占比"，语义失真最重。
- **独立设备快照表（demo agent_storage_stat）**：被拒——快照两列长在事实上，每设备取最新事实即得，省一张表与 upsert。
- **在线状态存 expected_offline_at 列**：被拒——设备最新事实时刻 + 该曲 duration + 1 分钟冗余可实时推导，无需落列。

## Consequences

- **口径难逆**：上线后事实无字节维度，历史不可回填；若将来要字节口径只能双轨重建。
- **命中率恒 0 的排障入口**：CDN 对整文件请求不返回 Content-Length（chunked）时元数据长度永远缺失。阿里云 OSS/CDN 静态 mp3 均带 Content-Length，风险极低，但它是第一嫌疑。
- **duration 依赖播放覆盖**：从未被播过的歌时长为 0，在线推导按 +1 分钟冗余降级；时长随播放自然收敛，不追平。
- **App 获得写内容表的通道**（`update_song_duration`）：通道已开，后续内容字段写回需求须克制，防止 App 变成内容生产方。
- **事实表只增不清**：无 housekeep，数据量 = 单端低频，访问明细 LIMIT 1000 兜底；将来瘦身再议。
- **采样进程内**：backend-next 单容器单实例为前提；重启丢一个采样窗口内的点，折线跨过，不补。
