# 媒体存储迁移至阿里云 OSS：key 与 URL 分离

本地磁盘存储（media/images、uploads、lrc）全部替换为阿里云 OSS（`ali-oss` SDK），DB 一律只存对象 key（文件名），对外 URL 由 `OSS_PUBLIC_BASE` 运行时拼接。决定此架构的根本动机：运营方后续会把对外地址切换为 CDN 或内网域名——key 与 URL 分离后，这一切换只是改一行环境变量，不涉及数据迁移、不重建前端（管理面板经 `/admin/config` 运行时获取媒体基地址）。

## Considered Options

- **私有 bucket + 签名 URL**：被拒。App 用 `<audio>` 直接拉流，签名 URL 引入过期与缓存问题；选公共读，防盗链交给 bucket Referer 白名单。
- **保留 local 存储驱动做回退/开发兜底**：被拒。无存量数据（现有记录为待清空假数据），双驱动让 URL 拼接永久背负分支；开发/测试使用独立 bucket。
- **前端直传 OSS（STS/PostObject）**：被拒。管理后台低频，服务端中转保持流程与鉴权不变；带宽成本可接受。
- **缩略图用 OSS 图片处理动态生成**：被拒。URL 需带查询参数且引入额外服务费用，sharp 在服务端压好后双对象上传（原图 + `images/thumbs/` 同名缩略图）保持 key 形态与旧协议一致。

## Consequences

- 上传走 SDK `internal` 参数（同 region ECS 内网，流量免费），默认公网 endpoint。
- 服务端静态托管 `/images` `/uploads` `/lrc` 与 `MEDIA_ROOT`、`next_media` 卷全部移除；本地路径推导（`baseUrl(req)` 按请求 Host）同步废弃。
- 编辑/替换文件时不删除旧 OSS 对象（与原"孤儿文件"行为一致，未引入删除逻辑）。
- 音频 ≤500MB 用 `putStream`（chunked，不驻留内存）；图片 ≤20MB 走 sharp 内存压缩后 `put`。
