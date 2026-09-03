# Prod 数据迁移走只读 API 通道，MP3 先外链后经面板转入 OSS

无 prod 数据库访问凭据，迁移数据只能通过旧 PHP 站点的只读 `api.php` 端点（sign/salt 协议参数取自 Android 客户端常量）分页导出为 JSON dump，再用 Prisma upsert 导入空的新库——放弃生成裸 SQL 文件由人工执行的方案。歌曲阶段一统一落 `type=external` + prod 完整 URL；随后新增**长期保留的后台「转入 OSS」功能**（单首按钮 + 列表多选批量，任意 external URL 均可转），按当时的原名规则上传（原 ADR 0005，现已废除回退为 rand 前缀命名）并把歌曲改回 `type=local` + key。图片/缩略图/LRC 文件不入该功能，由一次性批量脚本按 key 从 prod 下载补齐——导入完成到补齐完成之间存在图片不可用窗口期，已接受。

## Consequences

- API 通道拿不到的数据按既定边界处置：专辑→艺术家关联留空（割接后手工补）、横幅挂歌顺序取 `banner_songs` 接口返回序（可能与原挂接顺序有偏差）、status=0 下线内容与用户/举报/建议直接丢弃、settings 仅 `app_info` 子集自动迁（其余后台手工配）。
- 热门榜（人工固定）从 `home_new` 的 `trending_songs` 返回序还原进关联表，无需手工重配。
- 献给 API 兼容性的额外收益：JSON dump 本身就是旧服务的响应黄金样本，可用于导入后的数量比对与契约抽查。
- 迁移脚本（`fetch-prod.ts` / `import-prod.ts` / 媒体补齐）常驻 `server/scripts/`；只有「转入 OSS」是产品功能。
