# 媒体经 CDN 加速域名明文 HTTP 分发，客户端白名单放行

运营方接入的 CDN 加速域名 `cdn.qqt.yunshangzhiai7.top` 长期不配置 TLS 证书（`OSS_PUBLIC_BASE` 指向它，ADR 0004 的 key 与 URL 分离使这只是一行环境变量），Android 9+ 默认拦截明文流量导致 ExoPlayer 播放崩溃。决定：在 Android 客户端 `network_security_config.xml` 中对该域名放行明文 HTTP（`includeSubdomains`），作为长期终态而非过渡方案——音频内容非敏感，明文可接受，不在服务端/运维侧推动上 HTTPS。

## Considered Options

- **CDN 域名配证书、`OSS_PUBLIC_BASE` 切 `https://`**（ADR 0004 预设的正道）：被拒——证书配置依赖外部 CDN 控制台运维动作，不在本仓库可控范围，且运营方决定长期不上。
- **客户端把 `http://` 强制升级 `https://`**：不可行——实测该域名 443 TLS 握手直接失败，无证书。
- **客户端白名单放行（采纳）**：一行配置即恢复播放，符合"问题只在客户端侧解决"的项目约定。

## Consequences

- 同批放行旧 prod 服务器 IP `47.111.25.157`：迁移管线（backend-next ADR 0006）落库的 `type=external` 歌曲存的是旧 prod 完整 URL，未「转入 OSS」的遗留歌曲仍从该地址拉流，同样会被明文拦截。
- 将来若该 CDN 域名上了 HTTPS，需同步移除白名单条目并把 `OSS_PUBLIC_BASE` 切 `https://`（客户端发版 + 服务端配置两处）。
- 2026-09 修订：媒体内容层不再明文——音频与图片对象逐字节 +31 混淆存储（仓库级 ADR 0011），传输仍保持明文 HTTP，本 ADR 的传输层决定不变。
